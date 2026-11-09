package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.statusFor
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class PullRequestUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Null until the PR is found: in the saved list, or the freshly loaded one. */
    val item: PullRequestItem? = null,
    /** The list loaded and has no such PR: it was merged or closed, or never existed. */
    val notFound: Boolean = false,
    /** A feature made this pull request, so it can be tweaked. */
    val hasFeature: Boolean = false,
    /** The tweaks of this pull request, newest first, with the state of each. */
    val tweaks: List<Feature> = emptyList(),
    /** A session is working on this pull request now, so another tweak must wait for it. */
    val tweakBlocked: Boolean = false,
)
