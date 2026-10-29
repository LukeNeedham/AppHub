package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.ui.NewAppPageContent
import com.lukeneedham.apphub.ui.NewAppUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

/** Rendered by CI to docs/screenshots/<position in order.txt>-NewAppPage.png. */
@Preview
@Composable
private fun NewAppPageScreenshot() {
    val state = NewAppUiState(
        signedIn = true,
        hasCreationToken = true,
        name = "LyricTutor",
        progress = NewAppProgress.Running("https://github.com/LukeNeedham/ci-workflows/actions/runs/1"),
    )
    AppTheme {
        NewAppPageContent(
            state = state,
            onNameChange = {},
            onPrivateChange = {},
            onCreate = {},
            onDismissResult = {},
            onOpenSettings = {},
            onBack = {},
        )
    }
}
