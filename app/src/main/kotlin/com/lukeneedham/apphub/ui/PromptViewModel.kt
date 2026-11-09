package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.apphub.domain.LlmSettings
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.domain.isReady
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The page where the one prompt of a session is typed. [pullRequestNumber] is 0 for a new feature in [repo], or the pull request to
 * tweak. Sending starts the session and closes the input: the page never takes a second prompt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptViewModel(
    private val repo: String,
    pullRequestNumber: Int,
    private val manager: FeatureManager,
    githubToken: TokenStore,
    private val llmSettings: LlmSettings,
) : ViewModel() {

    private val number = pullRequestNumber.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(PromptUiState(repo = repo, pullRequestNumber = number))
    val uiState: StateFlow<PromptUiState> = _uiState.asStateFlow()

    private val _started = Channel<String>(Channel.BUFFERED)

    /** The id of the session once it is started: the page then goes to it. */
    val started: Flow<String> = _started.receiveAsFlow()

    init {
        viewModelScope.launch {
            val llm = llmSettings.provider.flatMapLatest { provider ->
                combine(llmSettings.apiKey(provider), llmSettings.model(provider), llmSettings.baseUrl(provider)) { _, model, _ ->
                    Triple(provider, llmSettings.isReady(provider), model)
                }
            }
            combine(githubToken.token, llm) { token, (provider, hasKey, model) ->
                _uiState.update {
                    it.copy(hasGithubToken = token != null, hasLlmKey = hasKey, llmName = "${provider.label}, $model")
                }
            }.collect {}
        }
    }

    fun onPromptChange(prompt: String) {
        if (_uiState.value.sent) return
        _uiState.update { it.copy(prompt = prompt, error = null) }
    }

    fun send() {
        val state = _uiState.value
        if (!state.canSend) return
        // Closed before anything else: a second tap cannot send a second prompt
        _uiState.update { it.copy(sent = true, error = null) }
        val id = if (number != null) manager.startTweak(repo, number, state.prompt) else manager.startFeature(repo, state.prompt)
        if (id == null) {
            _uiState.update { it.copy(sent = false, error = "A session is already working on this pull request. Wait until it ends.") }
        } else {
            _started.trySend(id)
        }
    }
}
