package com.lukeneedham.apphub.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The destinations of the app, one per page: [Home] is `HomePage`, and so on. */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    /** The page of one pull request. [repo] is `owner/name`. */
    @Serializable
    data class PullRequest(val repo: String, val number: Int) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Debug : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object Crashes : Route
}
