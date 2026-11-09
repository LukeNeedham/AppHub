package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val signedIn: Boolean = false,
    val showAutoMergePrs: Boolean = false,
    val input: String = "",
    val isChecking: Boolean = false,
    val error: String? = null,
    val rateLimit: RateLimitInfo? = null,
    /** Whether the token that creates repos (the New app page) is saved. */
    val hasRepoCreationToken: Boolean = false,
    /** What was typed for that token, until it is saved. */
    val repoCreationTokenInput: String = "",
    val llm: LlmUiState = LlmUiState(),
)

/** The LLM part of Settings: which provider the sessions run on, with which model and key. */
data class LlmUiState(
    val provider: LlmProvider = LlmProvider.Anthropic,
    /** The model field: what is typed for the chosen provider. A blank one is not saved, and the provider's default stays. */
    val modelInput: String = LlmProvider.Anthropic.defaultModel,
    /** Whether the chosen provider has an API key. */
    val hasKey: Boolean = false,
    val keyInput: String = "",
)
