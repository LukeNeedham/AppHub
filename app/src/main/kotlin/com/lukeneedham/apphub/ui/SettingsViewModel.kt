package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.data.GithubApi
import com.lukeneedham.apphub.domain.InvalidTokenException
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
    /** The last characters of the saved token, so the user can tell which one it is. */
    val tokenHint: String = "",
    val input: String = "",
    val isChecking: Boolean = false,
    val error: String? = null,
    val rateLimit: RateLimitInfo? = null,
)

class SettingsViewModel(
    private val api: GithubApi,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(stateFor(tokenStore.token.value))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        tokenStore.token.value?.let { loadRateLimit(it) }
    }

    fun onInputChange(input: String) = _uiState.update { it.copy(input = input, error = null) }

    /** Checks the token with GitHub, and keeps it only if GitHub accepts it. */
    fun save() {
        val token = _uiState.value.input.trim()
        if (token.isEmpty() || _uiState.value.isChecking) return
        _uiState.update { it.copy(isChecking = true, error = null) }
        viewModelScope.launch {
            try {
                val info = api.checkToken(token)
                tokenStore.set(token)
                _uiState.value = stateFor(token).copy(rateLimit = info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidTokenException) {
                _uiState.update { it.copy(isChecking = false, error = "GitHub rejected that token. Check that you copied all of it.") }
            } catch (e: Exception) {
                Logger.e("Could not check the GitHub token", e)
                _uiState.update { it.copy(isChecking = false, error = "Could not reach GitHub. Try again.") }
            }
        }
    }

    fun remove() {
        tokenStore.set(null)
        _uiState.value = stateFor(null)
    }

    private fun loadRateLimit(token: String) {
        viewModelScope.launch {
            try {
                val info = api.checkToken(token)
                _uiState.update { it.copy(rateLimit = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidTokenException) {
                _uiState.update { it.copy(error = "GitHub no longer accepts the saved token. Replace or remove it.") }
            } catch (e: Exception) {
                Logger.w("Could not read the GitHub rate limit", e)
            }
        }
    }

    private fun stateFor(token: String?) = SettingsUiState(
        signedIn = token != null,
        tokenHint = token?.takeLast(4).orEmpty(),
    )
}
