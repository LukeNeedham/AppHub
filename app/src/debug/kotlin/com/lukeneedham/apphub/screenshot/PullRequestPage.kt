package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.ui.PullRequestPageContent
import com.lukeneedham.apphub.ui.PullRequestUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

private val pullRequestState = PullRequestUiState(
    isLoading = false,
    item = SharedMockData.pullRequestItem,
)

/** Rendered by CI to docs/screenshots/PullRequestPage.png. */
@Preview
@Composable
private fun PullRequestPageScreenshot() {
    AppTheme {
        PullRequestPageContent(
            repo = SharedMockData.pullRequest.repo,
            number = SharedMockData.pullRequest.number,
            state = pullRequestState,
            onRefresh = {},
            onDownload = {},
            onInstall = {},
            onOpen = {},
            onBack = {},
        )
    }
}
