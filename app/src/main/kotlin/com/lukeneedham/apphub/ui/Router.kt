package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LogsPage
import com.lukeneedham.common.ui.NetworkPage

/** Calls the page of [route], with the navigation as lambdas. The only place that calls a page. */
@Composable
fun Router(backStack: NavBackStack<NavKey>, route: Route) {
    // The common actions are defined once here, never repeated in each branch
    fun navigateTo(target: Route) { backStack.add(target) }
    fun onBack() { backStack.removeLastOrNull() }
    fun replaceWith(target: Route) {
        backStack.removeLastOrNull()
        backStack.add(target)
    }

    CompositionLocalProvider(LocalCurrentRoute provides route) {
        when (route) { // exhaustive: no `else`
            Route.Home -> HomePage(
                onOpenPullRequest = { navigateTo(Route.PullRequest(it.repo, it.number)) },
                onOpenSettings = { navigateTo(Route.Settings) },
                onNewApp = { navigateTo(Route.NewApp) },
                onStartFeature = { navigateTo(Route.Prompt(it)) },
                onOpenFeature = { navigateTo(Route.Feature(it)) },
                onOpenApp = { navigateTo(Route.App(it)) },
            )

            is Route.App -> AppPage(
                repo = route.repo,
                onBack = ::onBack,
                onOpenPullRequest = { navigateTo(Route.PullRequest(it.repo, it.number)) },
                onStartFeature = { navigateTo(Route.Prompt(it)) },
                onOpenFeature = { navigateTo(Route.Feature(it)) },
            )

            is Route.PullRequest -> PullRequestPage(
                repo = route.repo,
                number = route.number,
                onBack = ::onBack,
                onTweak = { navigateTo(Route.Prompt(route.repo, route.number)) },
                onOpenFeature = { navigateTo(Route.Feature(it)) },
            )

            is Route.Prompt -> PromptPage(
                repo = route.repo,
                pullRequestNumber = route.pullRequestNumber,
                onBack = ::onBack,
                // A tweak goes back to the pull request, which lists it; a feature goes to its own page
                onStarted = { id -> if (route.pullRequestNumber > 0) onBack() else replaceWith(Route.Feature(id)) },
                onOpenSettings = { navigateTo(Route.Settings) },
            )

            is Route.Feature -> FeaturePage(
                id = route.id,
                onBack = ::onBack,
                onOpenPullRequest = { repo, number -> navigateTo(Route.PullRequest(repo, number)) },
            )

            Route.Settings -> SettingsPage(
                onBack = ::onBack,
                onOpenDebug = if (DebugKit.enabled) ({ navigateTo(Route.Debug) }) else null,
            )

            Route.NewApp -> NewAppPage(
                onBack = ::onBack,
                onOpenSettings = { navigateTo(Route.Settings) },
            )

            Route.Debug -> DebugPage(
                onBack = ::onBack,
                onOpenLogs = { navigateTo(Route.Logs) },
                onOpenCrashes = { navigateTo(Route.Crashes) },
                onOpenNetwork = { navigateTo(Route.Network) },
            )

            Route.Logs -> LogsPage(onBack = ::onBack)

            Route.Crashes -> CrashesPage(onBack = ::onBack)

            Route.Network -> NetworkPage(onBack = ::onBack)
        }
    }
}
