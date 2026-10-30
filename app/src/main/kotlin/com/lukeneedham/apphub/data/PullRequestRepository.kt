package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.CommentParser
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Only apps listed in the common repo (ci-workflows `apps.json`) are shown. One request lists every open PR, then one more per PR reads its comments. The comments are read on
 * every refresh: the CI edits its comment in place, and GitHub does not bump a PR's `updated_at` for an
 * edited comment, so nothing cheap says whether a cached result is still right. The requests are
 * revalidated through the HTTP cache, and a `304` does not count against the rate limit, which is what
 * keeps this affordable on the anonymous limit of 60 requests an hour.
 */
class PullRequestRepository(
    private val api: GithubApi,
    private val cache: PullRequestCache,
) : PullRequestSource {

    override fun cached(): List<PullRequest> = cache.load().values.map { it.pr }

    override suspend fun repositories(): List<String> {
        val apps = api.commonApps()
        return api.repositories().filter { it.lowercase() in apps }
    }

    override suspend fun openPullRequests(): List<PullRequest> {
        val apps = api.commonApps()
        // Only the apps listed in the common repo: the search covers every repo of the owner
        val issues = api.openPullRequests().mapNotNull { it.toOpenIssue() }.filter { it.repo.lowercase() in apps }
        val known = cache.load()
        val resolved = ConcurrentHashMap<String, CachedPullRequest>()
        try {
            coroutineScope {
                val limit = Semaphore(4)
                issues.map { issue ->
                    async {
                        resolved[issue.key] = limit.withPermit { fetch(issue) }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            // Keep what did load (for example before the rate limit hit), so the next try only needs the rest
            cache.save(known + resolved)
            throw e
        }
        val ordered = issues.map { resolved.getValue(it.key) }
        cache.save(ordered.associateBy { it.pr.key })
        return ordered.map { it.pr }
    }

    private suspend fun fetch(issue: OpenIssue): CachedPullRequest {
        val repo = issue.repo
        val comments = api.comments(repo, issue.number).map { it.toIssueComment() }
        val bodies = comments.map { it.body }
        val (parsed, web) = CommentParser.parse(comments.map { it.body to it.updatedAtMillis })
        val apk = parsed.withPreviousBuildTime(repo)
        return CachedPullRequest(
            updatedAt = issue.updatedAt,
            pr = PullRequest(
                repo = repo,
                number = issue.number,
                // A PR title can carry a whole commit message: keep the first line
                title = issue.title.lineSequence().first().trim(),
                author = issue.author,
                url = issue.url,
                isDraft = issue.isDraft,
                apk = apk,
                webUrl = web,
                sessionUrl = CommentParser.parseSessionUrl(listOf(issue.body) + bodies),
                description = issue.body?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    /** A stale APK is dated by the release it was published in: the comment says nothing of when it was built. */
    private suspend fun ApkBuild?.withPreviousBuildTime(repo: String): ApkBuild? {
        if (this !is ApkBuild.InProgress || previousUrl == null) return this
        val tag = previousUrl.substringAfter("/releases/download/").substringBefore('/')
        val publishedAt = try {
            api.releasePublishedAt(repo, tag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Without the date the stale build still shows, just undated
            null
        }
        val millis = publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return copy(previousBuiltAtMillis = millis)
    }
}
