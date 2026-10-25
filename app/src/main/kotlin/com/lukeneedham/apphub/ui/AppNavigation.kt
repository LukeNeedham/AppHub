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
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

/** The page of one pull request. [repo] is `owner/name`. */
@Serializable
data class PullRequestNavKey(val repo: String, val number: Int) : NavKey

@Serializable
data object SettingsNavKey : NavKey

@Serializable
data object DebugNavKey : NavKey

@Serializable
data object LogsNavKey : NavKey

@Serializable
data object CrashesNavKey : NavKey

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
            entry<HomeNavKey> {
                HomePage(
                    onOpenPullRequest = { backStack.add(PullRequestNavKey(it.repo, it.number)) },
                    onOpenSettings = { backStack.add(SettingsNavKey) },
                )
            }
            entry<PullRequestNavKey> { key ->
                PullRequestPage(repo = key.repo, number = key.number, onBack = { backStack.removeLastOrNull() })
            }
            entry<SettingsNavKey> {
                SettingsPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDebug = if (DebugKit.enabled) ({ backStack.add(DebugNavKey) }) else null,
                )
            }
            entry<DebugNavKey> {
                DebugPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenLogs = { backStack.add(LogsNavKey) },
                    onOpenCrashes = { backStack.add(CrashesNavKey) },
                )
            }
            entry<LogsNavKey> { LogsPage(onBack = { backStack.removeLastOrNull() }) }
            entry<CrashesNavKey> { CrashesPage(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
