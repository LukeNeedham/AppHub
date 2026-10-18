package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.CommentParser
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class PullRequestRepository(private val api: GithubApi) : PullRequestSource {

    override suspend fun openPullRequests(): List<PullRequest> = coroutineScope {
        val limit = Semaphore(4)
        api.openPullRequests()
            .map { issue ->
                async {
                    val repo = issue.repositoryUrl.substringAfter("/repos/")
                    val bodies = limit.withPermit { api.comments(repo, issue.number) }.map { it.body }
                    val (apk, web) = CommentParser.parse(bodies)
                    PullRequest(
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
                    )
                }
            }
            .map { it.await() }
    }
}
