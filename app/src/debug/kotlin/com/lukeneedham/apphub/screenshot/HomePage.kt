package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

/** Rendered by CI to docs/screenshots/HomePage.png. */
@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun HomePageScreenshot() {
    val state = HomeUiState(
        isLoading = false,
        repos = listOf(
            RepoSection(
                repo = "LukeNeedham/FlagTutor",
                pullRequests = listOf(
                    PullRequestItem(
                        pr = PullRequest(
                            repo = "LukeNeedham/FlagTutor",
                            number = 12,
                            title = "Add the flag quiz",
                            author = "LukeNeedham",
                            url = "https://github.com/LukeNeedham/FlagTutor/pull/12",
                            isDraft = false,
                            apk = ApkBuild.Succeeded(
                                url = "https://github.com/LukeNeedham/FlagTutor/releases/download/quiz-12-1/app-debug.apk",
                                commit = "a1b2c3d",
                                builtAt = "23/10/2026 18:27:11",
                            ),
                            webUrl = null,
                            sessionUrl = "https://claude.ai/code/session_sample",
                        ),
                        download = DownloadState.Idle,
                        install = InstallStatus.NotInstalled,
                    ),
                ),
                mainBuild = MainBuildItem(
                    repo = "LukeNeedham/FlagTutor",
                    info = MainBuildInfo(
                        run = MainRun(id = 1, commit = "a1b2c3d", state = RunState.Succeeded),
                        apk = MainApk(artifactId = 1, commit = "a1b2c3d"),
                        tip = "a1b2c3d",
                        commitsBehind = 0,
                    ),
                    requested = false,
                    download = DownloadState.Idle,
                    install = InstallStatus.NotInstalled,
                ),
            ),
            RepoSection(
                repo = "LukeNeedham/VideoDiary",
                pullRequests = listOf(
                    PullRequestItem(
                        pr = PullRequest(
                            repo = "LukeNeedham/VideoDiary",
                            number = 7,
                            title = "Trim a clip before saving it",
                            author = "LukeNeedham",
                            url = "https://github.com/LukeNeedham/VideoDiary/pull/7",
                            isDraft = false,
                            apk = ApkBuild.InProgress(commit = "9f8e7d6", previousUrl = null),
                            webUrl = null,
                        ),
                        download = DownloadState.Idle,
                        install = InstallStatus.Unknown,
                    ),
                ),
            ),
        ),
    )
    // Shown in place of the repo icons, which the page loads from a URL: a screenshot must never need the network.
    // A flat colour per repo, like its icon.
    val iconPreviewHandler = AsyncImagePreviewHandler { request ->
        ColorImage(if ("VideoDiary" in request.data.toString()) 0xFFE53935.toInt() else 0xFF1565C0.toInt())
    }
    // Coil shows its mock images only in inspection mode, which CI renders every screenshot in
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides iconPreviewHandler) {
        AppTheme {
            HomePageContent(
                state = state,
                onRefresh = {},
                onToggleRepo = {},
                onDownload = {},
                onCancelDownload = {},
                onInstall = {},
                onOpen = {},
                mainBuild = MainBuildActions(build = {}, download = {}, cancelDownload = {}, install = {}, open = {}),
                onOpenPullRequest = {},
                onOpenSettings = {},
            )
        }
    }
}
