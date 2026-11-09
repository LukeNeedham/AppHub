package com.lukeneedham.apphub.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The destinations of the app, one per page: [Home] is `HomePage`, and so on. */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    /**
     * The page of one pull request. [repo] is `owner/name`. [openedAt] is set when opened from a deep link, so opening
     * the same pull request again is a new entry, which reloads.
     */
    @Serializable
    data class PullRequest(val repo: String, val number: Int, val openedAt: Long = 0) : Route

    /**
     * The page of the one prompt that starts a session. [pullRequestNumber] is 0 for a new feature in [repo] (`owner/name`),
     * or the number of the pull request of [repo] to tweak.
     */
    @Serializable
    data class Prompt(val repo: String, val pullRequestNumber: Int = 0) : Route

    /** The page of one session, a feature or a tweak, by its id. */
    @Serializable
    data class Feature(val id: String) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object NewApp : Route

    @Serializable
    data object Debug : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object Crashes : Route

    @Serializable
    data object Network : Route
}
