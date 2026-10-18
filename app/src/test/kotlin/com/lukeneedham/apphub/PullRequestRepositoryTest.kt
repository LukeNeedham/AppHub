package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.CachedPullRequest
import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.IssueDto
import com.lukeneedham.apphub.data.PullRequestCache
import com.lukeneedham.apphub.data.PullRequestRepository
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.RateLimitInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestRepositoryTest {

    private val apk = "https://github.com/o/r/releases/download/b-1/app-debug.apk"
    private val comment = "### ✅ Build succeeded\n\n- **APK:** [Download]($apk)\n- **Commit:** [`abc1234`](https://github.com/o/r/commit/abc1234)"

    private fun issue(number: Int, updatedAt: String) = IssueDto(
        number = number,
        title = "PR $number\n\nlong description",
        htmlUrl = "https://github.com/o/r/pull/$number",
        repositoryUrl = "https://api.github.com/repos/o/r",
        updatedAt = updatedAt,
    )

    private class FakeApi(var issues: List<IssueDto>, val comment: String) : GithubApi {
        val commentCalls = mutableListOf<Int>()
        var rateLimitedOn: Int? = null
        override suspend fun openPullRequests() = issues
        override suspend fun checkToken(token: String) = RateLimitInfo(5000, 5000)
        override suspend fun comments(repo: String, number: Int): List<CommentDto> {
            if (number == rateLimitedOn) throw RateLimitException(null)
            commentCalls += number
            return listOf(CommentDto(comment))
        }
    }

    private class MemoryCache : PullRequestCache {
        var entries: Map<String, CachedPullRequest> = emptyMap()
        override fun load() = entries
        override fun save(entries: Map<String, CachedPullRequest>) { this.entries = entries }
    }

    @Test
    fun unchangedPullRequestsCostNoCommentRequests() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1")), comment)
        val repository = PullRequestRepository(api, MemoryCache())

        val first = repository.openPullRequests()
        assertEquals(listOf(1, 2), api.commentCalls.sorted())
        assertEquals("PR 1", first[0].title)
        assertTrue(first[0].apk is ApkBuild.Succeeded)

        api.commentCalls.clear()
        repository.openPullRequests()
        assertEquals(emptyList<Int>(), api.commentCalls)
    }

    @Test
    fun onlyAChangedPullRequestIsFetchedAgain() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1")), comment)
        val repository = PullRequestRepository(api, MemoryCache())
        repository.openPullRequests()

        api.commentCalls.clear()
        api.issues = listOf(issue(1, "t1"), issue(2, "t2"))
        repository.openPullRequests()
        assertEquals(listOf(2), api.commentCalls)
    }

    @Test
    fun closedPullRequestsLeaveTheCache() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1")), comment)
        val cache = MemoryCache()
        val repository = PullRequestRepository(api, cache)
        repository.openPullRequests()

        api.issues = listOf(issue(2, "t1"))
        assertEquals(listOf(2), repository.openPullRequests().map { it.number })
        assertEquals(setOf("o/r#2"), cache.entries.keys)
    }

    @Test
    fun whatLoadedBeforeTheRateLimitIsKept() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1")), comment)
        api.rateLimitedOn = 2
        val cache = MemoryCache()
        val repository = PullRequestRepository(api, cache)

        try {
            repository.openPullRequests()
            error("expected the rate limit")
        } catch (e: RateLimitException) {
            // expected
        }
        assertEquals(listOf(1), repository.cached().map { it.number })

        api.rateLimitedOn = null
        api.commentCalls.clear()
        repository.openPullRequests()
        assertEquals(listOf(2), api.commentCalls)
    }
}
