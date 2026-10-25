package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.ui.PullRequestItem
import com.lukeneedham.apphub.ui.PullRequestPageContent
import com.lukeneedham.apphub.ui.PullRequestUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/PullRequestPage.png. */
@Preview
@Composable
private fun PullRequestPageScreenshot() {
    val state = PullRequestUiState(
        isLoading = false,
        item = PullRequestItem(
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
                description = "Adds a quiz that shows a flag and asks for its country.\n\n- Four answers per question\n- A score at the end",
            ),
            download = DownloadState.Idle,
            install = InstallStatus.NotInstalled,
        ),
    )
    AppTheme {
        PullRequestPageContent(
            repo = "LukeNeedham/FlagTutor",
            number = 12,
            state = state,
            onRefresh = {},
            onDownload = {},
            onCancelDownload = {},
            onInstall = {},
            onOpen = {},
            onBack = {},
        )
    }
}
