package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.RunState
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.domain.mainKey
import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import com.lukeneedham.apphub.domain.statusFor
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.DateFormat
import java.util.Date

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val repos: List<RepoSection> = emptyList(),
    /** Repos shown collapsed: those without open pull requests start so, and the user can flip either kind. */
    val collapsedRepos: Set<String> = emptySet(),
)
