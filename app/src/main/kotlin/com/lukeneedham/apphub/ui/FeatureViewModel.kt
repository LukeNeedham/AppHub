package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.apphub.domain.FeatureStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The page of one session, a feature or a tweak, by its [id]. */
class FeatureViewModel(
    private val id: String,
    private val manager: FeatureManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeatureUiState(feature = manager.features.value.firstOrNull { it.id == id }))
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            manager.features.collect { list -> _uiState.update { it.copy(feature = list.firstOrNull { feature -> feature.id == id }) } }
        }
    }

    fun onAnswerChange(answer: String) = _uiState.update { it.copy(answer = answer) }

    /** Gives the answer, the only thing that can be typed to a session after its prompt. */
    fun sendAnswer() {
        val state = _uiState.value
        if (state.feature?.status != FeatureStatus.NeedsAnswer || state.answer.isBlank()) return
        manager.answer(id, state.answer)
        _uiState.update { it.copy(answer = "") }
    }

    fun stop() = manager.stop(id)

    fun dismiss() = manager.dismiss(id)
}
