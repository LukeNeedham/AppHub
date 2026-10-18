package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.RateLimitException
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

/** The page of one pull request, [repo] being `owner/name`. */
class PullRequestViewModel(
    private val repo: String,
    private val number: Int,
    private val source: PullRequestSource,
    private val downloads: ApkDownloads,
    private val tracker: InstallTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PullRequestUiState())
    val uiState: StateFlow<PullRequestUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequest: PullRequest? = source.cached().firstOrNull { it.repo == repo && it.number == number }
    private var isLoading = true
    private var error: String? = null
    private var notFound = false

    init {
        refresh()
        viewModelScope.launch { downloads.changes.drop(1).collect { publish() } }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            publish()
            try {
                val found = source.openPullRequests().firstOrNull { it.repo == repo && it.number == number }
                pullRequest = found
                notFound = found == null
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not load $repo#$number", e)
                error = describe(e)
            }
            isLoading = false
            publish()
        }
    }

    /** Call whenever the app comes back to the foreground: an install may have finished. */
    fun onResume() {
        tracker.confirmPending()
        publish()
    }

    fun download() {
        pullRequest?.let(downloads::download)
    }

    fun install() {
        pullRequest?.let(downloads::install)
    }

    private fun publish() {
        val pr = pullRequest
        _uiState.value = PullRequestUiState(
            isLoading = isLoading,
            error = error,
            item = pr?.let { PullRequestItem(it, downloads.stateOf(it), tracker.statusFor(it)) },
            notFound = notFound,
        )
    }

    private fun describe(e: Exception): String =
        when (e) {
            is RateLimitException -> "GitHub's rate limit for anonymous requests is used up. Try again later."
            is InvalidTokenException -> "GitHub rejected your token. Replace or remove it in Settings."
            else -> e.message ?: "Could not load the pull request"
        }
}
