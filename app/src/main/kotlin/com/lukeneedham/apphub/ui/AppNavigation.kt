package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.common.DebugKit
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LogsPage

@Composable
fun AppNavigation(backStack: NavBackStack<NavKey>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomePage(
                    onOpenPullRequest = { backStack.add(Route.PullRequest(it.repo, it.number)) },
                    onOpenSettings = { backStack.add(Route.Settings) },
                )
            }
            entry<Route.PullRequest> { key ->
                PullRequestPage(repo = key.repo, number = key.number, onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.Settings> {
                SettingsPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDebug = if (DebugKit.enabled) ({ backStack.add(Route.Debug) }) else null,
                )
            }
            entry<Route.Debug> {
                DebugPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenLogs = { backStack.add(Route.Logs) },
                    onOpenCrashes = { backStack.add(Route.Crashes) },
                )
            }
            entry<Route.Logs> { LogsPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Route.Crashes> { CrashesPage(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
