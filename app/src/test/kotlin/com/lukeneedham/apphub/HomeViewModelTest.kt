package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.DownloadEvent
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallLaunch
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.ui.ApkDownloads
import com.lukeneedham.apphub.ui.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun pr(number: Int, repo: String = "LukeNeedham/FlagTutor", commit: String = "abc1234") = PullRequest(
        repo = repo,
        number = number,
        title = "PR $number",
        author = "luke",
        url = "https://github.com/$repo/pull/$number",
        isDraft = false,
        apk = ApkBuild.Succeeded("https://github.com/$repo/releases/download/b-$number/app-debug.apk", commit),
        webUrl = null,
    )

    private fun viewModel(source: PullRequestSource, downloader: ApkDownloader, installer: ApkInstaller, tokens: TokenStore): HomeViewModel {
        val tracker = FakeTracker()
        return HomeViewModel(source, downloader, ApkDownloads(downloader, installer, tracker, CoroutineScope(dispatcher)), tracker, tokens)
    }

    private class FakeSource(var prs: List<PullRequest>, var repos: List<String> = emptyList()) : PullRequestSource {
        override suspend fun openPullRequests() = prs
        override suspend fun repositories() = repos
    }

    private class FakeDownloader : ApkDownloader {
        val kept = mutableListOf<Set<String>>()
        val downloaded = mutableListOf<String>()
        override fun existing(fileName: String): File? = null
        override fun download(url: String, fileName: String): Flow<DownloadEvent> {
            downloaded += url
            return flowOf(DownloadEvent.Progress(5, 10), DownloadEvent.Done(File(fileName)))
        }
        override suspend fun prune(keep: Set<String>) { kept += keep }
    }

    private class FakeInstaller : ApkInstaller {
        val installed = mutableListOf<File>()
        override fun install(file: File): InstallLaunch {
            installed += file
            return InstallLaunch.Launched
        }
        override fun open(packageName: String) = true
    }

    private class FakeTokenStore(initial: String? = null) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val token: StateFlow<String?> = state
        override fun set(token: String?) { state.value = token }
    }

    private class FakeTracker : InstallTracker {
        override fun statusFor(pr: PullRequest) = InstallStatus.Unknown
        override fun packageNameFor(pr: PullRequest): String? = null
        override fun onDownloaded(pr: PullRequest, file: File) = Unit
        override fun onInstallStarted(pr: PullRequest) = Unit
        override fun confirmPending() = Unit
    }

    @Test
    fun groupsPullRequestsByRepo() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1), pr(2), pr(3, repo = "LukeNeedham/Other")))
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("LukeNeedham/FlagTutor", "LukeNeedham/Other"), state.repos.map { it.repo })
        assertEquals(listOf(1, 2), state.repos[0].pullRequests.map { it.pr.number })
    }

    @Test
    fun reposWithoutPullRequestsComeAfterThoseWithSome() = runTest(dispatcher) {
        val source = FakeSource(
            listOf(pr(1, repo = "LukeNeedham/Other")),
            repos = listOf("LukeNeedham/Empty", "LukeNeedham/Other", "LukeNeedham/AlsoEmpty"),
        )
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("LukeNeedham/Other", "LukeNeedham/Empty", "LukeNeedham/AlsoEmpty"), state.repos.map { it.repo })
        assertEquals("https://github.com/LukeNeedham/Empty", state.repos[1].url)
        assertTrue(state.repos[1].pullRequests.isEmpty())
    }

    @Test
    fun reposWithoutPullRequestsStartCollapsedAndCanBeExpanded() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1)), repos = listOf("LukeNeedham/FlagTutor", "LukeNeedham/Empty"))
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()
        assertEquals(setOf("LukeNeedham/Empty"), viewModel.uiState.value.collapsedRepos)

        viewModel.toggleRepo("LukeNeedham/Empty")
        assertEquals(emptySet<String>(), viewModel.uiState.value.collapsedRepos)
    }

    @Test
    fun mergedPullRequestsDisappearAndTheirFilesArePruned() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1), pr(2)))
        val downloader = FakeDownloader()
        val viewModel = viewModel(source, downloader, FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        source.prs = listOf(pr(2))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.uiState.value.repos.single().pullRequests.map { it.pr.number })
        assertEquals(setOf(pr(2).apkFileName), downloader.kept.last())
    }

    @Test
    fun downloadingThenOpensTheInstaller() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1)))
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val viewModel = viewModel(source, downloader, installer, FakeTokenStore())
        advanceUntilIdle()

        viewModel.download("LukeNeedham/FlagTutor#1")
        advanceUntilIdle()

        assertEquals(1, downloader.downloaded.size)
        assertEquals(1, installer.installed.size)
        val item = viewModel.uiState.value.repos.single().pullRequests.single()
        assertTrue(item.download is DownloadState.Downloaded)
    }

    @Test
    fun failedLoadShowsAnError() = runTest(dispatcher) {
        val source = object : PullRequestSource {
            override suspend fun openPullRequests(): List<PullRequest> = error("offline")
        }
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        assertEquals("offline", viewModel.uiState.value.error)
    }

    @Test
    fun whileRebuildingThePreviousApkIsOfferedAsStale() {
        val previous = "https://github.com/LukeNeedham/FlagTutor/releases/download/b-1/app-debug.apk"
        val rebuilding = pr(1).copy(apk = ApkBuild.InProgress("newcommit", previous))

        val target = rebuilding.downloadableApk!!
        assertEquals(previous, target.url)
        assertTrue(target.isStale)
        assertEquals(null, rebuilding.copy(apk = ApkBuild.InProgress("newcommit", null)).downloadableApk)
        assertTrue(!pr(1).downloadableApk!!.isStale)
    }

    @Test
    fun reposCanBeCollapsedAndExpanded() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        viewModel.toggleRepo("LukeNeedham/FlagTutor")
        assertEquals(setOf("LukeNeedham/FlagTutor"), viewModel.uiState.value.collapsedRepos)
        viewModel.toggleRepo("LukeNeedham/FlagTutor")
        assertEquals(emptySet<String>(), viewModel.uiState.value.collapsedRepos)
    }

    @Test
    fun changingTheTokenLoadsThePullRequestsAgain() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1)))
        val tokens = FakeTokenStore()
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), tokens)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.repos.single().pullRequests.size)

        source.prs = listOf(pr(1), pr(2))
        tokens.set("github_pat_new")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.repos.single().pullRequests.size)
    }
}
