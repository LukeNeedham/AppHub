package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MissingPermissionException
import com.lukeneedham.apphub.domain.NewAppException
import com.lukeneedham.apphub.domain.NewAppProgress
import com.lukeneedham.apphub.domain.NewAppSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [tokenStore] is the token of Settings, which starts the workflow; [repoCreationTokenStore] holds a token of its own, which only
 * creates the repo, so that no secret of the common repo can. Both are entered in Settings: this page only reads them.
 */
class NewAppViewModel(
    private val source: NewAppSource,
    private val tokenStore: TokenStore,
    private val repoCreationTokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NewAppUiState(
            signedIn = tokenStore.token.value != null,
            hasCreationToken = repoCreationTokenStore.token.value != null,
        ),
    )
    val uiState: StateFlow<NewAppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tokenStore.token.collect { token -> _uiState.update { it.copy(signedIn = token != null) } }
        }
        viewModelScope.launch {
            repoCreationTokenStore.token.collect { token -> _uiState.update { it.copy(hasCreationToken = token != null) } }
        }
    }

    fun onNameChange(name: String) {
        if (_uiState.value.inProgress) return
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onPrivateChange(isPrivate: Boolean) {
        if (_uiState.value.inProgress) return
        _uiState.update { it.copy(isPrivate = isPrivate) }
    }

    /** Makes the repo, then starts its setup and follows it. Nothing happens while the name is wrong, a token is missing or one is running. */
    fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(progress = NewAppProgress.CreatingRepo, error = null) }
        viewModelScope.launch {
            try {
                source.create(state.name, state.isPrivate).collect { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NewAppException) {
                fail(e.message ?: "Could not create the app")
            } catch (e: InvalidTokenException) {
                fail("GitHub rejected your token. Replace or remove it in Settings.")
            } catch (e: MissingPermissionException) {
                fail(
                    "Your token may not start the workflow. It needs the Actions permission (read and write) " +
                        "for the ci-workflows repo: \"All repositories\" covers it.",
                )
            } catch (e: Exception) {
                Logger.e("Could not create the app", e)
                fail(e.message ?: "Could not create the app")
            }
        }
    }

    /** Closes the result: a created app starts a blank form, a failed run keeps the name to try again. */
    fun dismissResult() {
        _uiState.update {
            if (it.progress is NewAppProgress.Done) {
                NewAppUiState(signedIn = it.signedIn, hasCreationToken = it.hasCreationToken)
            } else {
                it.copy(progress = null, error = null)
            }
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(progress = null, error = message) }
}
