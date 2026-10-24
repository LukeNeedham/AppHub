package com.lukeneedham.apphub.screenshot

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.ui.PullRequestItem

/** Fake data that more than one screenshot shows, so that the screenshots tell one story. */
internal object SharedMockData {

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

    val pullRequestItem = PullRequestItem(pullRequest, DownloadState.Idle, InstallStatus.NotInstalled)
}
