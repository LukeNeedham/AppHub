package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.DownloadEvent
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallLaunch
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.MainPhase
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.RunState
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

    private fun viewModel(
        source: PullRequestSource,
        downloader: ApkDownloader,
        installer: ApkInstaller,
        tokens: TokenStore,
        mainBuilds: MainBuildSource = FakeMainBuilds(),
    ): HomeViewModel {
        val tracker = FakeTracker()
        return HomeViewModel(
            source,
            downloader,
            ApkDownloads(downloader, installer, tracker, CoroutineScope(dispatcher)),
            tracker,
            tokens,
            mainBuilds,
        )
    }

    private val repo = "LukeNeedham/FlagTutor"

    private fun info(runId: Long, state: RunState, apkCommit: String?, behind: Int = 0) = MainBuildInfo(
        run = MainRun(runId, "run$runId", state),
        apk = apkCommit?.let { MainApk(artifactId = 7, commit = it) },
        tip = "tip",
        commitsBehind = behind,
    )

    /** Answers with [statuses] in turn, repeating the last; null stands for a repo without the workflow. */
    private class FakeMainBuilds(var statuses: List<MainBuildInfo?> = listOf(null)) : MainBuildSource {
        var calls = 0
        val started = mutableListOf<String>()
        var startError: Exception? = null
        override suspend fun status(repo: String) = statuses[minOf(calls++, statuses.size - 1)]
        override suspend fun start(repo: String) {
            startError?.let { throw it }
            started += repo
        }
        override suspend fun downloadUrl(repo: String, apk: MainApk) = "https://example.com/artifact.zip"
    }

    private class FakeSource(var prs: List<PullRequest>, var repos: List<String> = emptyList()) : PullRequestSource {
        override suspend fun openPullRequests() = prs
        override suspend fun repositories() = repos
    }

    private class FakeDownloader : ApkDownloader {
        val kept = mutableListOf<Set<String>>()
        val downloaded = mutableListOf<String>()
        override fun existing(fileName: String): File? = null
        override fun download(url: String, fileName: String, fromZip: Boolean): Flow<DownloadEvent> {
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
        override fun statusFor(repo: String, buildId: String) = InstallStatus.Unknown
        override fun packageNameFor(repo: String): String? = null
        override fun onDownloaded(repo: String, file: File) = Unit
        override fun onInstallStarted(repo: String, buildId: String) = Unit
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
    fun aNewerBuildIsNotShownAsAlreadyDownloaded() = runTest(dispatcher) {
        val source = FakeSource(listOf(pr(1, commit = "aaaaaaa")))
        val viewModel = viewModel(source, FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()
        viewModel.download("LukeNeedham/FlagTutor#1")
        advanceUntilIdle()

        source.prs = listOf(pr(1, commit = "bbbbbbb"))
        viewModel.refresh()
        advanceUntilIdle()

        val item = viewModel.uiState.value.repos.single().pullRequests.single()
        assertEquals(DownloadState.Idle, item.download)
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

    @Test
    fun aRepoWithoutTheWorkflowHasNoMainBuild() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore())
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.repos.single().mainBuild)
    }

    @Test
    fun aRepoWithTheWorkflowShowsItsMainBuild() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(listOf(info(1, RunState.Succeeded, "abc1234")))
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        val main = viewModel.uiState.value.repos.single().mainBuild!!
        assertEquals(MainPhase.Ready, main.phase)
        assertEquals("LukeNeedham_FlagTutor-main-abc1234.apk", main.offer!!.fileName)
    }

    @Test
    fun buildingMainStartsARunAndFollowsItUntilItIsReady() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(
            listOf(
                info(1, RunState.Succeeded, "old1234"),
                // The press: the new run is listed and going
                info(2, RunState.Active, "old1234"),
                info(2, RunState.Succeeded, "new5678"),
            ),
        )
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        viewModel.buildMain(repo)
        assertEquals(MainPhase.Requested, viewModel.uiState.value.repos.single().mainBuild!!.phase)
        advanceUntilIdle()

        assertEquals(listOf(repo), mainBuilds.started)
        val main = viewModel.uiState.value.repos.single().mainBuild!!
        assertEquals(MainPhase.Ready, main.phase)
        assertEquals("new5678", main.info.apk!!.commit)
    }

    @Test
    fun aBuildThatCouldNotBeStartedShowsWhy() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(listOf(info(1, RunState.Succeeded, "abc1234")))
        mainBuilds.startError = java.io.IOException("needs the Actions permission")
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        viewModel.buildMain(repo)
        advanceUntilIdle()

        val main = viewModel.uiState.value.repos.single().mainBuild!!
        assertEquals(MainPhase.Ready, main.phase)
        assertEquals("needs the Actions permission", main.error)
    }

    @Test
    fun aRunThatNeverAppearsIsGivenUpOn() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(listOf(info(1, RunState.Succeeded, "abc1234")))
        val viewModel = viewModel(FakeSource(listOf(pr(1))), FakeDownloader(), FakeInstaller(), FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        viewModel.buildMain(repo)
        advanceUntilIdle()

        val main = viewModel.uiState.value.repos.single().mainBuild!!
        assertEquals(MainPhase.Ready, main.phase)
        assertTrue(main.error!!.startsWith("GitHub did not start the build"))
    }

    @Test
    fun downloadingTheMainBuildAsksForALinkAndOpensTheInstaller() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(listOf(info(1, RunState.Succeeded, "abc1234")))
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val viewModel = viewModel(FakeSource(listOf(pr(1))), downloader, installer, FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        viewModel.downloadMain(repo)
        advanceUntilIdle()

        assertEquals(listOf("https://example.com/artifact.zip"), downloader.downloaded)
        assertEquals(1, installer.installed.size)
    }

    @Test
    fun theDownloadOfTheMainBuildSurvivesTheCleanUpOfFiles() = runTest(dispatcher) {
        val mainBuilds = FakeMainBuilds(listOf(info(1, RunState.Succeeded, "abc1234")))
        val downloader = FakeDownloader()
        val viewModel = viewModel(FakeSource(listOf(pr(1))), downloader, FakeInstaller(), FakeTokenStore(), mainBuilds)
        advanceUntilIdle()

        assertTrue("LukeNeedham_FlagTutor-main-abc1234.apk" in downloader.kept.last())
        assertTrue(pr(1).apkFileName in downloader.kept.last())
    }
}
