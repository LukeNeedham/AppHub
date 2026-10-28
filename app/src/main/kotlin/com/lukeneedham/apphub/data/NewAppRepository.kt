package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.GITHUB_OWNER
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.NewAppSource
import com.lukeneedham.apphub.domain.newAppRunTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Creates an app by starting the workflow `new_app.yml` of the common repo, and follows its run.
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
        emit(NewAppProgress.Starting)
        val title = newAppRunTitle(name)
        val before = api.runs().filter { it.displayTitle == title }.map { it.id }.toSet()
        api.dispatch(name, isPrivate)
        emit(NewAppProgress.Waiting)

        var current = findRun(title, before)
        emit(NewAppProgress.Running(current.htmlUrl))
        var polls = 0
        while (current.status != COMPLETED) {
            if (++polls > runAttempts) throw IOException("The run is taking too long. Follow it on GitHub: ${current.htmlUrl}")
            delay(pollMillis)
            // A run that is not listed for a moment keeps the state it had
            current = api.runs().firstOrNull { it.id == current.id } ?: current
        }
        emit(
            if (current.conclusion == SUCCESS) {
                NewAppProgress.Done("$GITHUB_OWNER/$name", current.htmlUrl)
            } else {
                NewAppProgress.Failed(current.htmlUrl)
            },
        )
    }

    private suspend fun findRun(title: String, before: Set<Long>): WorkflowRunDto {
        repeat(findAttempts) {
            delay(pollMillis)
            api.runs().firstOrNull { it.displayTitle == title && it.id !in before }?.let { return it }
        }
        throw IOException("GitHub did not start the run. Look at the Actions tab of the common repo.")
    }

    private companion object {
        const val POLL_MILLIS = 3_000L
        const val FIND_ATTEMPTS = 20 // a minute
        const val RUN_ATTEMPTS = 600 // half an hour
        const val COMPLETED = "completed"
        const val SUCCESS = "success"
    }
}
