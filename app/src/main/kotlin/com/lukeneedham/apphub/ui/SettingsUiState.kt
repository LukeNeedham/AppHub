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
    val showAutoMergePrs: Boolean = false,
    val input: String = "",
    val isChecking: Boolean = false,
    val error: String? = null,
    val rateLimit: RateLimitInfo? = null,
)
