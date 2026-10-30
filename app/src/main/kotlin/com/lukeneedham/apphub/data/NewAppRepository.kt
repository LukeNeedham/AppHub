package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.GITHUB_OWNER
import com.lukeneedham.apphub.domain.NewAppException
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.NewAppSource
import com.lukeneedham.apphub.domain.newAppRunTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Creates an app: makes its repo with the token of its own, then starts the workflow `new_app.yml` of the common repo to set
 * the repo up, and follows its run. The repo is made here and not by the workflow so that no secret of the common repo
 * can create repos (`docs/create-app.md` there).
 *
 * GitHub answers a start with no id for the run, so the run is found by its title (the workflow names each run after the
 * app), among the runs that were not there before: a second try of the same name does not take the first one's.
 */
class NewAppRepository(
    private val api: NewAppApi,
    private val pollMillis: Long = POLL_MILLIS,
    private val findAttempts: Int = FIND_ATTEMPTS,
    private val runAttempts: Int = RUN_ATTEMPTS,
) : NewAppSource {

    override fun create(name: String, isPrivate: Boolean): Flow<NewAppProgress> = flow {
        emit(NewAppProgress.CreatingRepo)
        val title = newAppRunTitle(name)
        // First, so that a token that cannot see the workflow is found out before a repo is made that nothing can set up
        val before = runs().filter { it.displayTitle == title }.map { it.id }.toSet()
        val branch = api.createRepo(name, isPrivate)
        if (branch != MAIN) api.renameBranch(name, branch, MAIN)

        emit(NewAppProgress.Starting)
        try {
            api.dispatch(name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NewAppException(
                "The repo $GITHUB_OWNER/$name was created, but its setup could not be started (${e.message}). " +
                    "Run the New App workflow of ci-workflows from its Actions tab, with resume on.",
            )
        }
        emit(NewAppProgress.Waiting)

        var current = findRun(title, before)
        emit(NewAppProgress.Running(current.htmlUrl))
        var polls = 0
        while (current.status != COMPLETED) {
            if (++polls > runAttempts) throw IOException("The run is taking too long. Follow it on GitHub: ${current.htmlUrl}")
            delay(pollMillis)
            // A run that is not listed for a moment keeps the state it had
            current = runs().firstOrNull { it.id == current.id } ?: current
        }
        emit(
            if (current.conclusion == SUCCESS) {
                NewAppProgress.Done("$GITHUB_OWNER/$name", current.htmlUrl)
            } else {
                NewAppProgress.Failed(current.htmlUrl)
            },
        )
    }

    /** The runs GitHub lists, without those that cannot be followed. */
    private suspend fun runs(): List<WorkflowRun> = api.runs().mapNotNull { it.toWorkflowRun() }

    private suspend fun findRun(title: String, before: Set<Long>): WorkflowRun {
        repeat(findAttempts) {
            delay(pollMillis)
            runs().firstOrNull { it.displayTitle == title && it.id !in before }?.let { return it }
        }
        throw IOException("GitHub did not start the run. Look at the Actions tab of the common repo.")
    }

    private companion object {
        const val MAIN = "main"
        const val POLL_MILLIS = 3_000L
        const val FIND_ATTEMPTS = 20 // a minute
        const val RUN_ATTEMPTS = 600 // half an hour
        const val COMPLETED = "completed"
        const val SUCCESS = "success"
    }
}
