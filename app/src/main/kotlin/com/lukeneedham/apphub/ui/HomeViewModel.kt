package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class PullRequestItem(
    val pr: PullRequest,
    val download: DownloadState,
    val install: InstallStatus,
)

/** One repo and its open pull requests, of which there may be none. */
data class RepoSection(val repo: String, val pullRequests: List<PullRequestItem>) {
    val url: String get() = repoUrl(repo)
    val claudeUrl: String get() = newClaudeSessionUrl(repo)
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val repos: List<RepoSection> = emptyList(),
    /** Repos shown collapsed: those without open pull requests start so, and the user can flip either kind. */
    val collapsedRepos: Set<String> = emptySet(),
)

class HomeViewModel(
    private val source: PullRequestSource,
    private val downloader: ApkDownloader,
    private val downloads: ApkDownloads,
    private val tracker: InstallTracker,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequests = emptyList<PullRequest>()
    private var isLoading = true
    private var error: String? = null
    private var repositories = emptyList<String>()
    private var toggledRepos = emptySet<String>()

    init {
        pullRequests = source.cached()
        refresh()
        // Downloads and installs started here or on a PR's own page
        viewModelScope.launch { downloads.changes.drop(1).collect { publish() } }
        // A new or removed token changes what GitHub allows and shows: load again
        viewModelScope.launch { tokenStore.token.drop(1).collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            publish()
            try {
                pullRequests = source.openPullRequests()
                error = null
                forgetClosedPullRequests()
                repositories = source.repositories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not load pull requests", e)
                error = describe(e)
            }
            isLoading = false
            publish()
        }
    }

    fun toggleRepo(repo: String) {
        toggledRepos = if (repo in toggledRepos) toggledRepos - repo else toggledRepos + repo
        publish()
    }

    /** Call whenever the app comes back to the foreground: an install may have finished. */
    fun onResume() {
        tracker.confirmPending()
        publish()
    }

    fun download(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::download)
    }

    /** Opens the system installer for an APK that is already downloaded. */
    fun install(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::install)
    }

    /** Starts the installed app of the PR. */
    fun open(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::open)
    }

    /** Merged and closed PRs vanish from the list: drop their state and their downloaded files. */
    private suspend fun forgetClosedPullRequests() {
        downloads.retainOnly(pullRequests.map { it.key }.toSet())
        downloader.prune(pullRequests.filter { it.downloadableApk != null }.map { it.apkFileName }.toSet())
    }

    private fun publish() {
        val withPullRequests = pullRequests
            .groupBy { it.repo }
            .map { (repo, prs) ->
                RepoSection(
                    repo = repo,
                    pullRequests = prs.map { PullRequestItem(it, downloads.stateOf(it), tracker.statusFor(it)) },
                )
            }
        // Repos with no open pull requests come after the ones that have some
        val withNone = (repositories - withPullRequests.map { it.repo }.toSet())
            .map { RepoSection(repo = it, pullRequests = emptyList()) }
        val sections = withPullRequests + withNone
        val collapsed = sections.filter { (it.repo in toggledRepos) != it.pullRequests.isEmpty() }.map { it.repo }.toSet()
        _uiState.value = HomeUiState(isLoading = isLoading, error = error, repos = sections, collapsedRepos = collapsed)
    }

    private fun describe(e: Exception): String =
        when (e) {
            is RateLimitException -> {
                val resets = e.resetEpochSeconds?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
                "GitHub's rate limit for anonymous requests is used up." +
                    if (resets != null) " Try again after $resets." else ""
            }
            is InvalidTokenException -> "GitHub rejected your token. Replace or remove it in Settings."
            else -> e.message ?: "Could not load pull requests"
        }
}
