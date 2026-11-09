package com.lukeneedham.apphub.ui

data class AppUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Null until the repo is known. */
    val section: RepoSection? = null,
)
