package com.lukeneedham.apphub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.apphub.domain.PullRequestRef

/**
 * Owns the back stack and the [NavDisplay]. Called only by [Root].
 *
 * @param pendingLink a pull request link that has been opened and not yet shown; it replaces the back stack once.
 * @param onLinkShown called when [pendingLink] has been pushed, so the caller can forget it.
 */
@Composable
fun AppNavigation(pendingLink: PullRequestRef?, onLinkShown: () -> Unit) {
    val backStack = rememberNavBackStack(Route.Home)

    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        onLinkShown()
        // Always a fresh stack of Home and the page, so back goes Home; the new openedAt makes the page a new
        // entry even when the same pull request is already showing, so its ViewModel reloads
        val page = Route.PullRequest(link.repo, link.number, openedAt = System.currentTimeMillis())
        backStack.clear()
        backStack.add(Route.Home)
        backStack.add(page)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key -> routeEntryProvider(backStack, key) },
    )
}

private fun routeEntryProvider(backStack: NavBackStack<NavKey>, key: NavKey): NavEntry<NavKey> {
    val route = key as Route
    return NavEntry(route) {
        Router(backStack, route)
    }
}
