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
import com.lukeneedham.apphub.ui.ApkDownloads
import com.lukeneedham.apphub.ui.PullRequestViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PullRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val repo = "LukeNeedham/FlagTutor"

    private fun pr(number: Int) = PullRequest(
        repo = repo,
        number = number,
        title = "PR $number",
        author = "luke",
        url = "https://github.com/$repo/pull/$number",
        isDraft = false,
        apk = ApkBuild.Succeeded("https://github.com/$repo/releases/download/b-$number/app-debug.apk", "abc1234"),
        webUrl = null,
        description = "About PR $number",
    )

    private class FakeSource(var prs: List<PullRequest>, var saved: List<PullRequest> = emptyList()) : PullRequestSource {
        override suspend fun openPullRequests() = prs
        override fun cached() = saved
    }

    private class FakeDownloader : ApkDownloader {
        val downloaded = mutableListOf<String>()
        override fun existing(fileName: String): File? = null
        override fun download(url: String, fileName: String, fromZip: Boolean): Flow<DownloadEvent> {
            downloaded += url
            return flowOf(DownloadEvent.Done(File(fileName)))
        }
        override suspend fun prune(keep: Set<String>) = Unit
    }

    private class FakeInstaller : ApkInstaller {
        val installed = mutableListOf<File>()
        override fun install(file: File): InstallLaunch {
            installed += file
            return InstallLaunch.Launched
        }
        override fun open(packageName: String) = true
    }

    private class FakeTracker : InstallTracker {
        override fun statusFor(repo: String, buildId: String) = InstallStatus.Unknown
        override fun packageNameFor(repo: String): String? = null
        override fun onDownloaded(repo: String, file: File) = Unit
        override fun onInstallStarted(repo: String, buildId: String) = Unit
        override fun confirmPending() = Unit
    }

    private fun viewModel(
        number: Int,
        source: PullRequestSource,
        downloader: ApkDownloader = FakeDownloader(),
        installer: ApkInstaller = FakeInstaller(),
    ): PullRequestViewModel {
        val tracker = FakeTracker()
        val downloads = ApkDownloads(downloader, installer, tracker, CoroutineScope(dispatcher))
        return PullRequestViewModel(repo, number, source, downloads, tracker)
    }

    @Test
    fun showsThePullRequestWithItsDescription() = runTest(dispatcher) {
        val viewModel = viewModel(2, FakeSource(listOf(pr(1), pr(2))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.item?.pr?.number)
        assertEquals("About PR 2", state.item?.pr?.description)
        assertTrue(!state.isLoading)
        assertTrue(!state.notFound)
    }

    @Test
    fun theSavedPullRequestShowsWhileTheListLoads() = runTest(dispatcher) {
        val viewModel = viewModel(2, FakeSource(listOf(pr(2)), saved = listOf(pr(2))))
        assertEquals(2, viewModel.uiState.value.item?.pr?.number)
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertTrue(!viewModel.uiState.value.isLoading)
    }

    @Test
    fun aPullRequestThatIsNotOpenIsNotFound() = runTest(dispatcher) {
        val viewModel = viewModel(9, FakeSource(listOf(pr(1))))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.item)
        assertTrue(viewModel.uiState.value.notFound)
    }

    @Test
    fun aFailedLoadKeepsTheSavedPullRequestAndShowsTheError() = runTest(dispatcher) {
        val source = object : PullRequestSource {
            override suspend fun openPullRequests(): List<PullRequest> = error("offline")
            override fun cached() = listOf(pr(2))
        }
        val viewModel = viewModel(2, source)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.item)
        assertEquals("offline", viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.notFound)
    }

    @Test
    fun downloadingThenOpensTheInstaller() = runTest(dispatcher) {
        val downloader = FakeDownloader()
        val installer = FakeInstaller()
        val viewModel = viewModel(2, FakeSource(listOf(pr(2))), downloader, installer)
        advanceUntilIdle()

        viewModel.download()
        advanceUntilIdle()

        assertEquals(1, downloader.downloaded.size)
        assertEquals(1, installer.installed.size)
        assertTrue(viewModel.uiState.value.item?.download is DownloadState.Downloaded)
    }
}
