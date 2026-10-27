package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.AssetDto
import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.IssueDto
import com.lukeneedham.apphub.data.MainBuildRepository
import com.lukeneedham.apphub.data.ReleaseDto
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MainBuildRepositoryTest {

    private class FakeApi(var release: ReleaseDto? = null) : GithubApi {
        var releaseCalls = 0
        override suspend fun openPullRequests(): List<IssueDto> = emptyList()
        override suspend fun repositories(): List<String> = emptyList()
        override suspend fun commonApps(): Set<String> = emptySet()
        override suspend fun comments(repo: String, number: Int): List<CommentDto> = emptyList()
        override suspend fun checkToken(token: String) = RateLimitInfo(5000, 5000)
        override suspend fun mainRelease(repo: String): ReleaseDto? {
            releaseCalls++
            return release
        }
        override suspend fun releasePublishedAt(repo: String, tag: String): String? = null
    }

    private class FakeTokenStore(initial: String? = "github_pat_x") : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<String?> = state
        override fun set(token: String?) { state.value = token }
    }

    private val sha = "a".repeat(40)
    private val url = "https://github.com/o/r/releases/download/main-build/app-debug.apk"

    private fun release(
        body: String? = "commit: $sha\n\nDebug build of the default branch.",
        assets: List<AssetDto> = listOf(AssetDto("app-debug.apk", url, "2026-10-27T08:00:00Z")),
    ) = ReleaseDto(body = body, assets = assets)

    @Test
    fun `without a token there is no main build and GitHub is not asked`() = runTest {
        val api = FakeApi(release())
        assertNull(MainBuildRepository(api, FakeTokenStore(null)).status("o/r"))
        assertEquals(0, api.releaseCalls)
    }

    @Test
    fun `the latest main build is the apk of the main-build release, of the commit its notes name`() = runTest {
        val apk = MainBuildRepository(FakeApi(release()), FakeTokenStore()).status("o/r")

        assertEquals(MainApk(sha, url, Instant.parse("2026-10-27T08:00:00Z").toEpochMilli()), apk)
    }

    @Test
    fun `a repo without the release has no main build, and is not asked again for a while`() = runTest {
        val api = FakeApi(release = null)
        var clock = 0L
        val repository = MainBuildRepository(api, FakeTokenStore()) { clock }

        assertNull(repository.status("o/r"))
        assertNull(repository.status("o/r"))
        assertEquals(1, api.releaseCalls)

        clock += 11 * 60 * 1000
        repository.status("o/r")
        assertEquals(2, api.releaseCalls)
    }

    @Test
    fun `a repo that gains the release is found once the wait is over`() = runTest {
        val api = FakeApi(release = null)
        var clock = 0L
        val repository = MainBuildRepository(api, FakeTokenStore()) { clock }
        assertNull(repository.status("o/r"))

        api.release = release()
        clock += 11 * 60 * 1000

        assertEquals(sha, repository.status("o/r")!!.commit)
    }

    @Test
    fun `a release without the apk is no main build`() = runTest {
        val api = FakeApi(release(assets = listOf(AssetDto("notes.txt", "https://x/notes.txt"))))
        assertNull(MainBuildRepository(api, FakeTokenStore()).status("o/r"))
    }

    @Test
    fun `a release whose notes do not name a commit is no main build`() = runTest {
        assertNull(MainBuildRepository(FakeApi(release(body = "Debug build generated automatically")), FakeTokenStore()).status("o/r"))
        assertNull(MainBuildRepository(FakeApi(release(body = null)), FakeTokenStore()).status("o/r"))
        assertNull(MainBuildRepository(FakeApi(release(body = "commit: abc")), FakeTokenStore()).status("o/r"))
    }

    @Test
    fun `an unknown upload time is not a reason to hide the build`() = runTest {
        val apk = MainBuildRepository(FakeApi(release(assets = listOf(AssetDto("app-debug.apk", url)))), FakeTokenStore()).status("o/r")!!
        assertNull(apk.builtAtMillis)
    }
}
