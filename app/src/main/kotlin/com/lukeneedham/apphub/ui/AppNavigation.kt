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
data object Home : NavKey

/** The page of one pull request. [repo] is `owner/name`. */
@Serializable
data class PullRequestKey(val repo: String, val number: Int) : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object Debug : NavKey

@Serializable
data object Logs : NavKey

@Serializable
data object Crashes : NavKey

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
            entry<Home> {
                HomePage(
                    onOpenPullRequest = { backStack.add(PullRequestKey(it.repo, it.number)) },
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<PullRequestKey> { key ->
                PullRequestPage(repo = key.repo, number = key.number, onBack = { backStack.removeLastOrNull() })
            }
            entry<Settings> {
                SettingsPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenDebug = if (DebugKit.enabled) ({ backStack.add(Debug) }) else null,
                )
            }
            entry<Debug> {
                DebugPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenLogs = { backStack.add(Logs) },
                    onOpenCrashes = { backStack.add(Crashes) },
                )
            }
            entry<Logs> { LogsPage(onBack = { backStack.removeLastOrNull() }) }
            entry<Crashes> { CrashesPage(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
