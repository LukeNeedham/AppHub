package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)
