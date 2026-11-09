package com.lukeneedham.apphub.ui

import com.lukeneedham.apphub.domain.Feature

data class FeatureUiState(
    /** Null when there is no such session: it was removed. */
    val feature: Feature? = null,
    /** What is typed for the question of the session. */
    val answer: String = "",
)
