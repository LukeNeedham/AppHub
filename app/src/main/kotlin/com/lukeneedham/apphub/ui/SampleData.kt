package com.lukeneedham.apphub.ui

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.RunState

// Fake data for the previews, which the README screenshots are rendered from

internal val samplePullRequest = PullRequest(
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

private val sampleBuildingPullRequest = PullRequest(
    repo = "LukeNeedham/VideoDiary",
    number = 7,
    title = "Trim a clip before saving it",
    author = "LukeNeedham",
    url = "https://github.com/LukeNeedham/VideoDiary/pull/7",
    isDraft = false,
    apk = ApkBuild.InProgress(commit = "9f8e7d6", previousUrl = null),
    webUrl = null,
)

private fun sampleMainBuild(repo: String) = MainBuildItem(
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

internal fun samplePullRequestState() = PullRequestUiState(
    isLoading = false,
    item = PullRequestItem(samplePullRequest, DownloadState.Idle, InstallStatus.NotInstalled),
)

internal fun sampleHomeState() = HomeUiState(
    isLoading = false,
    repos = listOf(
        RepoSection(
            repo = "LukeNeedham/FlagTutor",
            pullRequests = listOf(PullRequestItem(samplePullRequest, DownloadState.Idle, InstallStatus.NotInstalled)),
            mainBuild = sampleMainBuild("LukeNeedham/FlagTutor"),
        ),
        RepoSection(
            repo = "LukeNeedham/VideoDiary",
            pullRequests = listOf(PullRequestItem(sampleBuildingPullRequest, DownloadState.Idle, InstallStatus.Unknown)),
        ),
    ),
)
