package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.Flow

/** The common repo, whose workflow [NEW_APP_WORKFLOW] creates a new app and sets it up (`docs/create-app.md` there). */
const val CI_WORKFLOWS_REPO = "LukeNeedham/ci-workflows"

/** The file of the workflow that creates an app. */
const val NEW_APP_WORKFLOW = "new_app.yml"

/** The branch the workflow is run from. */
const val NEW_APP_REF = "main"

/** The title of the run that creates [name]: the `run-name` of the workflow, by which AppHub tells its run from the others. */
fun newAppRunTitle(name: String) = "New App $name"

private const val MIN_NAME_LENGTH = 2
private const val MAX_NAME_LENGTH = 50
private val NEW_APP_NAME = Regex("[A-Za-z][A-Za-z0-9]*([-_][A-Za-z0-9]+)*")

/** What is wrong with [name] as the name of a new app and its repo, said as `scripts/new_app.py` checks it; null when it is fine. */
fun newAppNameError(name: String): String? = when {
    name.length < MIN_NAME_LENGTH -> "At least $MIN_NAME_LENGTH characters"
    name.length > MAX_NAME_LENGTH -> "At most $MAX_NAME_LENGTH characters"
    !NEW_APP_NAME.matches(name) ->
        "Letters, digits, hyphens and underscores only, starting with a letter, with no hyphen or underscore at the end or doubled"
    else -> null
}

/** The package the app gets: the letters and digits of [name], lower case (`LyricTutor` is `com.lukeneedham.lyrictutor`). */
fun newAppPackage(name: String) = "com.lukeneedham." + name.filter { it.isLetterOrDigit() }.lowercase()

/** How far the creation of an app has got. */
sealed interface NewAppProgress {
    /** Making the repo. */
    data object CreatingRepo : NewAppProgress

    /** The repo is made; asking GitHub to start the workflow that sets it up. */
    data object Starting : NewAppProgress

    /** GitHub took the request; its run is not listed yet. */
    data object Waiting : NewAppProgress

    /** The workflow is running. */
    data class Running(val runUrl: String) : NewAppProgress

    /** The app is created: [repo] is its repo, `owner/name`. */
    data class Done(val repo: String, val runUrl: String) : NewAppProgress

    /** The workflow ended without success. [runUrl] has its log. */
    data class Failed(val runUrl: String) : NewAppProgress
}

/** Where new apps are created. */
interface NewAppSource {
    /**
     * Makes the repo of the app [name] (private when [isPrivate]), then starts the workflow that sets it up and follows it
     * until it ends. Throws [NewAppException] with a message to show when the repo could not be made or its setup not
     * started, [InvalidTokenException] or [MissingPermissionException] when the token of the workflow calls does not allow
     * them, and an `IOException` for any other failure to reach GitHub.
     */
    fun create(name: String, isPrivate: Boolean): Flow<NewAppProgress>
}

/** GitHub answered 403 or 404: the token may not start the workflow, or cannot see its repo. */
class MissingPermissionException : Exception("GitHub refused: the token may not start the workflow")

/** Something went wrong in a way the user can act on: [message] says what, and is shown as it is. */
class NewAppException(message: String) : Exception(message)
