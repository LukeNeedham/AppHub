package com.lukeneedham.apphub.ui

import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.RunState

/**
 * The sample data of the README screenshots (docs/screenshots.md in LukeNeedham/ci-workflows): used by the
 * `…PageContentScreenshot` composables and nothing else. One place for the app's fake pull requests, so that the
 * screenshots tell one story.
 */
@OptIn(ExperimentalCoilApi::class)
internal object AppScreenshotMockData {

    /**
     * Shown in place of the images that the pages load from a URL (the icons of the repos), so that a screenshot
     * never needs the network and comes out the same every time: a flat colour per repo, like its icon.
     */
    val imagePreviewHandler = AsyncImagePreviewHandler { request ->
        ColorImage(if ("VideoDiary" in request.data.toString()) 0xFFE53935.toInt() else 0xFF1565C0.toInt())
    }

    val pullRequest = PullRequest(
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
        description = "Adds a quiz that shows a flag and asks for its country.\n\n- Four answers per question\n- A score at the end",
    )

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

    val pullRequestState = PullRequestUiState(
        isLoading = false,
        item = PullRequestItem(pullRequest, DownloadState.Idle, InstallStatus.NotInstalled),
    )

    val homeState = HomeUiState(
        isLoading = false,
        repos = listOf(
            RepoSection(
                repo = "LukeNeedham/FlagTutor",
                pullRequests = listOf(PullRequestItem(pullRequest, DownloadState.Idle, InstallStatus.NotInstalled)),
                mainBuild = mainBuild("LukeNeedham/FlagTutor"),
            ),
            RepoSection(
                repo = "LukeNeedham/VideoDiary",
                pullRequests = listOf(PullRequestItem(buildingPullRequest, DownloadState.Idle, InstallStatus.Unknown)),
            ),
        ),
    )

    val settingsState = SettingsUiState(signedIn = true, rateLimit = RateLimitInfo(limit = 5000, remaining = 4873))
}
