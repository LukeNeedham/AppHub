package com.lukeneedham.apphub.ui

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val repos: List<RepoSection> = emptyList(),
    /** Repos shown collapsed: those without open pull requests start so, and the user can flip either kind. */
    val collapsedRepos: Set<String> = emptySet(),
)
