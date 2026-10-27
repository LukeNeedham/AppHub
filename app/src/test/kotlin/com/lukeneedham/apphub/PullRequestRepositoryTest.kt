package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.CachedPullRequest
import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.ReleaseDto
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
        body = "  Adds the thing\n\nhttps://claude.ai/code/session_abc  ",
    )

    private class FakeApi(var issues: List<IssueDto>, var comment: String) : GithubApi {
        val commentCalls = mutableListOf<Int>()
        var rateLimitedOn: Int? = null
        override suspend fun openPullRequests() = issues
        override suspend fun repositories(): List<String> = emptyList()
        override suspend fun commonApps(): Set<String> = setOf("o/r")
        override suspend fun checkToken(token: String) = RateLimitInfo(5000, 5000)
        override suspend fun mainRelease(repo: String): ReleaseDto? = null
        override suspend fun releasePublishedAt(repo: String, tag: String): String? = null
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
    fun commentsAreReadOnEveryRefresh() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1")), comment)
        val repository = PullRequestRepository(api, MemoryCache())

        val first = repository.openPullRequests()
        assertEquals(listOf(1, 2), api.commentCalls.sorted())
        assertEquals("PR 1", first[0].title)
        assertTrue(first[0].apk is ApkBuild.Succeeded)

        api.commentCalls.clear()
        repository.openPullRequests()
        assertEquals(listOf(1, 2), api.commentCalls.sorted())
    }

    @Test
    fun theDescriptionIsKeptAndABlankOneIsNot() = runTest {
        val api = FakeApi(listOf(issue(1, "t1"), issue(2, "t1").copy(body = " \n")), comment)
        val prs = PullRequestRepository(api, MemoryCache()).openPullRequests()

        assertEquals("Adds the thing\n\nhttps://claude.ai/code/session_abc", prs[0].description)
        assertEquals(null, prs[1].description)
    }

    @Test
    fun aCommentEditedInPlaceIsPickedUpEvenIfUpdatedAtIsUnchanged() = runTest {
        val inProgress = "### ⏳ Build in progress\n\n- **Commit:** [`abc1234`](https://github.com/o/r/commit/abc1234)"
        val api = FakeApi(listOf(issue(1, "t1")), comment)
        val repository = PullRequestRepository(api, MemoryCache())
        assertTrue(repository.openPullRequests()[0].apk is ApkBuild.Succeeded)

        // A push, then the CI edits its comment: updated_at may not have moved
        api.comment = inProgress
        assertTrue(repository.openPullRequests()[0].apk is ApkBuild.InProgress)

        api.comment = comment
        assertTrue(repository.openPullRequests()[0].apk is ApkBuild.Succeeded)
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
        assertEquals(listOf(1, 2), repository.openPullRequests().map { it.number })
    }

    @Test
    fun onlyAppsInTheCommonRepoAreListed() = runTest {
        val other = issue(2, "t1").copy(repositoryUrl = "https://api.github.com/repos/o/other")
        val api = FakeApi(listOf(issue(1, "t1"), other), comment)
        val prs = PullRequestRepository(api, MemoryCache()).openPullRequests()

        assertEquals(listOf(1), prs.map { it.number })
    }
}
