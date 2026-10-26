package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.ui.theme.AppTheme
import com.lukeneedham.apphub.ui.theme.ProvideAppDebugSettings

/**
 * The one composable `MainActivity` calls: everything the UI sits in, then the navigation. The debug page is reached from
 * Settings, so there is no floating DebugButton.
 *
 * @param pendingLink a pull request link that the activity was opened with and that has not been shown yet.
 * @param onLinkShown called once [pendingLink] has been taken.
 */
@Composable
fun Root(pendingLink: PullRequestRef?, onLinkShown: () -> Unit) {
    AppTheme {
        ProvideAppDebugSettings {
            AppNavigation(pendingLink = pendingLink, onLinkShown = onLinkShown)
        }
    }
}
