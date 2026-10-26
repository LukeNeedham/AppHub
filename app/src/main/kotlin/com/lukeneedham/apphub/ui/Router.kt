package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LogsPage

/** Calls the page of [route], with the navigation as lambdas. The only place that calls a page. */
@Composable
fun Router(backStack: NavBackStack<NavKey>, route: Route) {
    // The common actions are defined once here, never repeated in each branch
    fun navigateTo(target: Route) { backStack.add(target) }
    fun onBack() { backStack.removeLastOrNull() }

    when (route) { // exhaustive: no `else`
        Route.Home -> HomePage(
            onOpenPullRequest = { navigateTo(Route.PullRequest(it.repo, it.number)) },
            onOpenSettings = { navigateTo(Route.Settings) },
        )

        is Route.PullRequest -> PullRequestPage(repo = route.repo, number = route.number, onBack = ::onBack)

        Route.Settings -> SettingsPage(
            onBack = ::onBack,
            onOpenDebug = if (DebugKit.enabled) ({ navigateTo(Route.Debug) }) else null,
        )

        Route.Debug -> DebugPage(
            onBack = ::onBack,
            onOpenLogs = { navigateTo(Route.Logs) },
            onOpenCrashes = { navigateTo(Route.Crashes) },
        )

        Route.Logs -> LogsPage(onBack = ::onBack)

        Route.Crashes -> CrashesPage(onBack = ::onBack)
    }
}
