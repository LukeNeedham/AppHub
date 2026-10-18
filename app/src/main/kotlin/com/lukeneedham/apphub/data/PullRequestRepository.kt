package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.CommentParser
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * One request lists every open PR; the comments of a PR (one more request each) are fetched only when
 * the PR has changed since last time. GitHub bumps a PR's `updated_at` for a new or edited comment, so
 * an unchanged `updated_at` means the CI comment is the same. This keeps a refresh to a single request
 * once everything is cached, which matters because the anonymous rate limit is 60 requests an hour.
 */
class PullRequestRepository(
    private val api: GithubApi,
    private val cache: PullRequestCache,
) : PullRequestSource {

    override fun cached(): List<PullRequest> = cache.load().values.map { it.pr }

    override suspend fun openPullRequests(): List<PullRequest> {
        val issues = api.openPullRequests()
        val known = cache.load()
        val resolved = ConcurrentHashMap<String, CachedPullRequest>()
        try {
            coroutineScope {
                val limit = Semaphore(4)
                issues.map { issue ->
                    async {
                        val key = keyOf(issue)
                        resolved[key] = known[key]?.takeIf { it.updatedAt == issue.updatedAt }
                            ?: limit.withPermit { fetch(issue) }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            // Keep what did load (for example before the rate limit hit), so the next try only needs the rest
            cache.save(known + resolved)
            throw e
        }
        val ordered = issues.map { resolved.getValue(keyOf(it)) }
        cache.save(ordered.associateBy { it.pr.key })
        return ordered.map { it.pr }
    }

    private suspend fun fetch(issue: IssueDto): CachedPullRequest {
        val repo = repoOf(issue)
        val bodies = api.comments(repo, issue.number).map { it.body }
        val (apk, web) = CommentParser.parse(bodies)
        return CachedPullRequest(
            updatedAt = issue.updatedAt,
            pr = PullRequest(
                repo = repo,
                number = issue.number,
                // A PR title can carry a whole commit message: keep the first line
                title = issue.title.lineSequence().first().trim(),
                author = issue.user?.login.orEmpty(),
                url = issue.htmlUrl,
                isDraft = issue.draft,
                apk = apk,
                webUrl = web,
                sessionUrl = CommentParser.parseSessionUrl(listOf(issue.body) + bodies),
            ),
        )
    }

    private fun repoOf(issue: IssueDto) = issue.repositoryUrl.substringAfter("/repos/")

    private fun keyOf(issue: IssueDto) = "${repoOf(issue)}#${issue.number}"
}
