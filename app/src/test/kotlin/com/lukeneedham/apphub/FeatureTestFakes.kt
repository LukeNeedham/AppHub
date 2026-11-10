package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.CiStatus
import com.lukeneedham.apphub.domain.CodeHost
import com.lukeneedham.apphub.domain.ConfiguredLlm
import com.lukeneedham.apphub.domain.DirectoryEntry
import com.lukeneedham.apphub.domain.RepoTree
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.apphub.domain.FeatureRunner
import com.lukeneedham.apphub.domain.FeatureStore
import com.lukeneedham.apphub.domain.FileChange
import com.lukeneedham.apphub.domain.LlmClient
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.LlmReply
import com.lukeneedham.apphub.domain.LlmRequest
import com.lukeneedham.apphub.domain.LlmSettings
import com.lukeneedham.apphub.domain.LlmSource
import com.lukeneedham.apphub.domain.LlmToolCall
import com.lukeneedham.apphub.domain.PullRequestInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class InMemoryFeatureStore : FeatureStore {
    private val state = MutableStateFlow(emptyList<Feature>())
    override val features: StateFlow<List<Feature>> = state
    override fun add(feature: Feature) = state.update { listOf(feature) + it }
    override fun update(id: String, change: (Feature) -> Feature) = state.update { list -> list.map { if (it.id == id) change(it) else it } }
    override fun remove(id: String) = state.update { list -> list.filterNot { it.id == id } }
}

class InMemoryLlmSettings : LlmSettings {
    private val providerState = MutableStateFlow(LlmProvider.Anthropic)
    private val models = LlmProvider.entries.associateWith { MutableStateFlow(it.defaultModel) }
    private val baseUrls = LlmProvider.entries.associateWith { MutableStateFlow("") }
    private val keys = LlmProvider.entries.associateWith { MutableStateFlow<String?>(null) }
    override val provider: StateFlow<LlmProvider> = providerState
    override fun selectProvider(provider: LlmProvider) { providerState.value = provider }
    override fun model(provider: LlmProvider): StateFlow<String> = models.getValue(provider)
    override fun setModel(provider: LlmProvider, model: String) { models.getValue(provider).value = model }
    override fun baseUrl(provider: LlmProvider): StateFlow<String> = baseUrls.getValue(provider)
    override fun setBaseUrl(provider: LlmProvider, url: String) { baseUrls.getValue(provider).value = url }
    override fun apiKey(provider: LlmProvider): StateFlow<String?> = keys.getValue(provider)
    override fun setApiKey(provider: LlmProvider, key: String?) { keys.getValue(provider).value = key }
}

/** An LLM that answers from a script, one reply per call, and remembers what it was asked. */
class ScriptedLlm(private val replies: List<LlmReply>) : LlmClient {
    val requests = mutableListOf<LlmRequest>()
    override suspend fun complete(request: LlmRequest): LlmReply {
        requests += request.copy(messages = request.messages.toList())
        return replies.getOrNull(requests.size - 1) ?: error("The script has no reply for call ${requests.size}")
    }
}

class FixedLlmSource(private val llm: LlmClient?) : LlmSource {
    override fun current() = llm?.let { ConfiguredLlm(it, "test-model", "Test") }
}

fun toolCall(id: String, name: String, vararg arguments: Pair<String, String>) =
    LlmToolCall(id, name, JsonObject(arguments.associate { it.first to JsonPrimitive(it.second) }))

fun reply(vararg calls: LlmToolCall, text: String? = null) = LlmReply(text, calls.toList())

class FakeCodeHost(
    val files: MutableMap<String, String> = mutableMapOf(),
    /** The answers of the CI, one per poll; the last repeats. */
    ci: List<CiStatus> = listOf(CiStatus.Passed),
    private val tweakedPullRequest: PullRequestInfo? = null,
    /** What preparing the branch throws, as GitHub does for a token that may not write. */
    private val branchError: Exception? = null,
) : CodeHost {
    data class Commit(val branch: String, val message: String, val changes: List<FileChange>)

    private val ciAnswers = ci.toMutableList()
    val commits = mutableListOf<Commit>()
    val openedPullRequests = mutableListOf<String>()
    var ciPolls = 0

    override suspend fun defaultBranch(repo: String) = "main"

    override suspend fun pullRequest(repo: String, number: Int) = tweakedPullRequest

    override suspend fun listDirectory(repo: String, ref: String, path: String): List<DirectoryEntry> {
        val prefix = if (path.isEmpty()) "" else "$path/"
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
            .map { DirectoryEntry(it.substringBefore('/'), isDirectory = '/' in it) }.distinct()
    }

    var treeCalls = 0

    override suspend fun listTree(repo: String, ref: String): RepoTree {
        treeCalls++
        return RepoTree(files.keys.sorted(), truncated = false)
    }

    override suspend fun readFile(repo: String, ref: String, path: String) = files[path]

    override suspend fun prepareBranch(repo: String, branch: String) {
        branchError?.let { throw it }
    }

    override suspend fun commit(repo: String, branch: String, message: String, changes: List<FileChange>): String {
        commits += Commit(branch, message, changes)
        changes.forEach { if (it is FileChange.Write) files[it.path] = it.content else files.remove(it.path) }
        return "sha${commits.size}"
    }

    override suspend fun openPullRequest(repo: String, branch: String, title: String, body: String): PullRequestInfo {
        openedPullRequests += title
        return PullRequestInfo(7, title, "https://github.com/$repo/pull/7", branch, isOpen = true)
    }

    override suspend fun ciStatus(repo: String, sha: String): CiStatus {
        ciPolls++
        return if (ciAnswers.size > 1) ciAnswers.removeAt(0) else ciAnswers.first()
    }
}

/** A manager that never gets to run a session: for the tests of the pages that only read it. */
fun testFeatureManager(store: FeatureStore = InMemoryFeatureStore(), scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) =
    FeatureManager(store, FeatureRunner(FixedLlmSource(null), FakeCodeHost(), store), scope)
