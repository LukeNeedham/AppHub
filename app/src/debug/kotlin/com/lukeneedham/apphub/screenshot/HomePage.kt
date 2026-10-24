package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.RunState
import com.lukeneedham.apphub.ui.HomePageContent
import com.lukeneedham.apphub.ui.HomeUiState
import com.lukeneedham.apphub.ui.MainBuildActions
import com.lukeneedham.apphub.ui.PullRequestItem
import com.lukeneedham.apphub.ui.RepoSection
import com.lukeneedham.apphub.ui.theme.AppTheme

private val buildingPullRequest = PullRequest(
    repo = "LukeNeedham/VideoDiary",
    number = 7,
    title = "Trim a clip before saving it",
    author = "LukeNeedham",
    url = "https://github.com/LukeNeedham/VideoDiary/pull/7",
    isDraft = false,
    apk = ApkBuild.InProgress(commit = "9f8e7d6", previousUrl = null),
    webUrl = null,
)

private fun mainBuild(repo: String) = MainBuildItem(
    repo = repo,
    info = MainBuildInfo(
        run = MainRun(id = 1, commit = "a1b2c3d", state = RunState.Succeeded),
        apk = MainApk(artifactId = 1, commit = "a1b2c3d"),
        tip = "a1b2c3d",
        commitsBehind = 0,
    ),
    requested = false,
    download = DownloadState.Idle,
    install = InstallStatus.NotInstalled,
)

private val homeState = HomeUiState(
    isLoading = false,
    repos = listOf(
        RepoSection(
            repo = "LukeNeedham/FlagTutor",
            pullRequests = listOf(SharedMockData.pullRequestItem),
            mainBuild = mainBuild("LukeNeedham/FlagTutor"),
        ),
        RepoSection(
            repo = "LukeNeedham/VideoDiary",
            pullRequests = listOf(PullRequestItem(buildingPullRequest, DownloadState.Idle, InstallStatus.Unknown)),
        ),
    ),
)

/**
 * Shown in place of the repo icons, which the page loads from a URL: a screenshot must never need the network. A
 * flat colour per repo, like its icon.
 */
@OptIn(ExperimentalCoilApi::class)
private val iconPreviewHandler = AsyncImagePreviewHandler { request ->
    ColorImage(if ("VideoDiary" in request.data.toString()) 0xFFE53935.toInt() else 0xFF1565C0.toInt())
}

/** Rendered by CI to docs/screenshots/HomePage.png. */
@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun HomePageScreenshot() {
    // Coil shows its mock images only in inspection mode
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides iconPreviewHandler,
    ) {
        AppTheme {
            HomePageContent(
                state = homeState,
                onRefresh = {},
                onToggleRepo = {},
                onDownload = {},
                onInstall = {},
                onOpen = {},
                mainBuild = MainBuildActions(build = {}, download = {}, install = {}, open = {}),
                onOpenPullRequest = {},
                onOpenSettings = {},
            )
        }
    }
}
