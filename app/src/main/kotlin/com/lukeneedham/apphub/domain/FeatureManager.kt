package com.lukeneedham.apphub.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Starts, answers and stops the sessions. Each runs in [scope] (which outlives every screen) in its own coroutine with its own
 * conversation: nothing is shared between sessions, and a session that ends leaves nothing running.
 */
class FeatureManager(
    private val store: FeatureStore,
    private val runner: FeatureRunner,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val questions = mutableMapOf<String, CompletableDeferred<String>>()

    val features: StateFlow<List<Feature>> get() = store.features

    /**
     * A session ended by the app closing is over: nothing resumes it. Call once, when the app starts, before any session starts.
     */
    fun failInterrupted() {
        store.features.value.filter { it.status.isActive }.forEach { feature ->
            store.update(feature.id) {
                it.copy(status = FeatureStatus.Failed, question = null, error = "AppHub was closed while this was running, which ended it.")
            }
        }
    }

    /** Starts a session that builds [prompt] in [repo] and opens a pull request. Returns its id, or null for an empty prompt. */
    fun startFeature(repo: String, prompt: String): String? {
        if (prompt.isBlank()) return null
        val id = newId()
        val feature = Feature(
            id = id,
            kind = FeatureKind.Feature,
            repo = repo,
            prompt = prompt.trim(),
            summary = summarize(prompt),
            status = FeatureStatus.Working,
            branch = featureBranchName(prompt, id),
            createdAtMillis = nowMillis(),
        )
        return begin(feature)
    }

    /** Starts a session that changes pull request [number] of [repo] as [prompt] says. Null for an empty prompt or when a session is on that pull request. */
    fun startTweak(repo: String, number: Int, prompt: String): String? {
        if (prompt.isBlank() || isBusy(repo, number)) return null
        val feature = Feature(
            id = newId(),
            kind = FeatureKind.Tweak,
            repo = repo,
            prompt = prompt.trim(),
            summary = summarize(prompt),
            status = FeatureStatus.Working,
            prNumber = number,
            createdAtMillis = nowMillis(),
        )
        return begin(feature)
    }

    /** Whether a session is changing pull request [number] of [repo] now: a second one would push to the same branch. */
    fun isBusy(repo: String, number: Int): Boolean =
        store.features.value.any { it.status.isActive && it.belongsTo(repo, number) }

    /** Gives the answer to the question of the session [id]. Anything else is ignored. */
    fun answer(id: String, text: String) {
        if (text.isBlank()) return
        questions.remove(id)?.complete(text.trim())
    }

    /** Ends the session [id] now. */
    fun stop(id: String) {
        val feature = store.find(id) ?: return
        jobs.remove(id)?.cancel()
        questions.remove(id)?.cancel()
        if (feature.status.isActive) {
            store.update(id) { it.copy(status = FeatureStatus.Failed, question = null, error = "Stopped.") }
        }
    }

    /** Forgets a session that is over. */
    fun dismiss(id: String) {
        if (store.find(id)?.status?.isActive == true) return
        store.remove(id)
    }

    private fun begin(feature: Feature): String {
        store.add(feature)
        jobs[feature.id] = scope.launch {
            try {
                runner.run(feature.id) { question ->
                    val answer = CompletableDeferred<String>()
                    questions[feature.id] = answer
                    answer.await()
                }
            } finally {
                jobs.remove(feature.id)
                questions.remove(feature.id)
            }
        }
        return feature.id
    }
}
