package com.lukeneedham.apphub.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.ui.SettingsPageContent
import com.lukeneedham.apphub.ui.SettingsUiState
import com.lukeneedham.apphub.ui.theme.AppTheme

private val settingsState = SettingsUiState(signedIn = true, rateLimit = RateLimitInfo(limit = 5000, remaining = 4873))

/** Rendered by CI to docs/screenshots/SettingsPage.png. */
@Preview
@Composable
private fun SettingsPageScreenshot() {
    AppTheme {
        SettingsPageContent(
            state = settingsState,
            onInputChange = {},
            onSave = {},
            onRemove = {},
            onBack = {},
            onOpenDebug = {},
        )
    }
}
