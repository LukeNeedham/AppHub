package com.lukeneedham.apphub.ui

data class PromptUiState(
    /** `owner/name` */
    val repo: String,
    /** Null for a new feature; the pull request that is tweaked otherwise. */
    val pullRequestNumber: Int? = null,
    val prompt: String = "",
    /** Whether Settings has the GitHub token the session pushes with. */
    val hasGithubToken: Boolean = false,
    /** Whether Settings has the API key of the chosen LLM provider. */
    val hasLlmKey: Boolean = false,
    /** The provider and model the session will use, for the page to say. */
    val llmName: String = "",
    val error: String? = null,
    /** The prompt is sent: the input is closed for good, and the page is on its way out. */
    val sent: Boolean = false,
) {
    val isTweak: Boolean get() = pullRequestNumber != null

    val canSend: Boolean get() = !sent && prompt.isNotBlank() && hasGithubToken && hasLlmKey
}
