package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.ArtifactDto
import com.lukeneedham.apphub.data.CommentDto
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.data.IssueDto
import com.lukeneedham.apphub.data.MainBuildRepository
import com.lukeneedham.apphub.data.RunDto
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.RunState
import com.lukeneedham.apphub.domain.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MainBuildRepositoryTest {

    private class FakeApi(
        var runs: List<RunDto>? = emptyList(),
        var artifacts: Map<Long, List<ArtifactDto>> = emptyMap(),
        var tip: String = "tip",
        var ahead: Int = 0,
    ) : GithubApi {
        var runCalls = 0
        val compared = mutableListOf<Pair<String, String>>()
        override suspend fun openPullRequests(): List<IssueDto> = emptyList()
        override suspend fun repositories(): List<String> = emptyList()
        override suspend fun commonApps(): Set<String> = emptySet()
        override suspend fun comments(repo: String, number: Int): List<CommentDto> = emptyList()
        override suspend fun checkToken(token: String) = RateLimitInfo(5000, 5000)
        override suspend fun mainRuns(repo: String): List<RunDto>? {
            runCalls++
            return runs
        }
        override suspend fun runArtifacts(repo: String, runId: Long) = artifacts[runId].orEmpty()
        override suspend fun releasePublishedAt(repo: String, tag: String): String? = null
        override suspend fun latestCommit(repo: String, branch: String) = tip
        override suspend fun commitsAhead(repo: String, base: String, head: String): Int {
            compared += base to head
            return ahead
        }
        override suspend fun startMainBuild(repo: String) = Unit
        override suspend fun artifactDownloadUrl(repo: String, artifactId: Long) = "https://blob/$artifactId"
    }

    private class FakeTokenStore(initial: String? = "github_pat_x") : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<String?> = state
        override fun set(token: String?) { state.value = token }
    }

    private fun run(id: Long, sha: String, status: String = "completed", conclusion: String? = "success") =
        RunDto(id, sha, status, conclusion)

    private fun apk(id: Long, expired: Boolean = false) = listOf(ArtifactDto(id, "apk", expired))

    @Test
    fun `without a token there is no main build and GitHub is not asked`() = runTest {
        val api = FakeApi(runs = listOf(run(1, "aaa")))
        assertNull(MainBuildRepository(api, FakeTokenStore(null)).status("o/r"))
        assertEquals(0, api.runCalls)
    }

    @Test
    fun `a repo without the workflow has no main build, and is not asked again for a while`() = runTest {
        val api = FakeApi(runs = null)
        var clock = 0L
        val repository = MainBuildRepository(api, FakeTokenStore()) { clock }

        assertNull(repository.status("o/r"))
        assertNull(repository.status("o/r"))
        assertEquals(1, api.runCalls)

        clock += 11 * 60 * 1000
        repository.status("o/r")
        assertEquals(2, api.runCalls)
    }

    @Test
    fun `the newest successful run with a live artifact is the apk`() = runTest {
        val api = FakeApi(
            runs = listOf(run(3, "ccc", status = "in_progress", conclusion = null), run(2, "bbb"), run(1, "aaa")),
            artifacts = mapOf(2L to apk(22), 1L to apk(11)),
            tip = "ccc",
        )

        val info = MainBuildRepository(api, FakeTokenStore()).status("o/r")!!

        assertEquals(MainRun(3, "ccc", RunState.Active), info.run)
        assertEquals(MainApk(22, "bbb"), info.apk)
        assertEquals("ccc", info.tip)
    }

    @Test
    fun `an expired artifact is skipped for an older one, or for none`() = runTest {
        val api = FakeApi(
            runs = listOf(run(2, "bbb"), run(1, "aaa")),
            artifacts = mapOf(2L to apk(22, expired = true), 1L to apk(11, expired = true)),
        )
        assertNull(MainBuildRepository(api, FakeTokenStore()).status("o/r")!!.apk)
    }

    @Test
    fun `an artifact past its expiry is not offered even if GitHub has not flagged it`() = runTest {
        val expiry = "2026-10-23T10:00:00Z"
        val api = FakeApi(runs = listOf(run(1, "aaa")), artifacts = mapOf(1L to listOf(ArtifactDto(11, "apk", false, expiry))))
        val before = Instant.parse(expiry).toEpochMilli() - 1
        assertEquals(MainApk(11, "aaa"), MainBuildRepository(api, FakeTokenStore()) { before }.status("o/r")!!.apk)
        assertNull(MainBuildRepository(api, FakeTokenStore()) { before + 1 }.status("o/r")!!.apk)
    }

    @Test
    fun `a build of the tip is not behind and needs no comparison`() = runTest {
        val api = FakeApi(runs = listOf(run(1, "tip")), artifacts = mapOf(1L to apk(11)), tip = "tip")

        val info = MainBuildRepository(api, FakeTokenStore()).status("o/r")!!

        assertEquals(0, info.commitsBehind)
        assertEquals(emptyList<Pair<String, String>>(), api.compared)
    }

    @Test
    fun `an older build counts the commits since`() = runTest {
        val api = FakeApi(runs = listOf(run(1, "old")), artifacts = mapOf(1L to apk(11)), tip = "tip", ahead = 4)

        val info = MainBuildRepository(api, FakeTokenStore()).status("o/r")!!

        assertEquals(4, info.commitsBehind)
        assertEquals(listOf("old" to "tip"), api.compared)
    }

    @Test
    fun `failed and cancelled runs are told apart`() = runTest {
        val failed = FakeApi(runs = listOf(run(1, "a", conclusion = "failure")))
        val cancelled = FakeApi(runs = listOf(run(1, "a", conclusion = "cancelled")))

        assertEquals(RunState.Failed, MainBuildRepository(failed, FakeTokenStore()).status("o/r")!!.run!!.state)
        assertEquals(RunState.Cancelled, MainBuildRepository(cancelled, FakeTokenStore()).status("o/r")!!.run!!.state)
    }

    @Test
    fun `no runs yet is a repo with the workflow and no build`() = runTest {
        val info = MainBuildRepository(FakeApi(runs = emptyList()), FakeTokenStore()).status("o/r")!!
        assertNull(info.run)
        assertNull(info.apk)
    }
}
