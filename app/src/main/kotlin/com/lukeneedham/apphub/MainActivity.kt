package com.lukeneedham.apphub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import com.lukeneedham.apphub.domain.DeepLink
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.ui.AppNavigation
import com.lukeneedham.apphub.ui.Route
import com.lukeneedham.apphub.ui.theme.AppTheme
import com.lukeneedham.apphub.ui.theme.ProvideAppDebugSettings

class MainActivity : ComponentActivity() {

    /** A pull request link that has been opened and not yet shown. */
    private var pendingLink by mutableStateOf<PullRequestRef?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a rotation or process restart the saved back stack already has the page
        if (savedInstanceState == null) pendingLink = linkOf(intent)
        setContent {
            AppTheme {
                ProvideAppDebugSettings {
                    // The debug page is reached from Settings, so there is no floating DebugButton
                    val backStack = rememberNavBackStack(Route.Home)
                    LaunchedEffect(pendingLink) {
                        val link = pendingLink ?: return@LaunchedEffect
                        pendingLink = null
                        val page = Route.PullRequest(link.repo, link.number)
                        if (backStack.lastOrNull() != page) backStack.add(page)
                    }
                    AppNavigation(backStack)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkOf(intent)?.let { pendingLink = it }
    }

    private fun linkOf(intent: Intent): PullRequestRef? =
        if (intent.action == Intent.ACTION_VIEW) DeepLink.parse(intent.dataString) else null
}
