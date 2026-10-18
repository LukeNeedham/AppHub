package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data object Home : NavKey

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
                val viewModel = koinViewModel<HomeViewModel>()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onResume = viewModel::onResume,
                    onToggleRepo = viewModel::toggleRepo,
                    onDownload = viewModel::download,
                    onInstall = viewModel::install,
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<Settings> {
                SettingsScreen(
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
