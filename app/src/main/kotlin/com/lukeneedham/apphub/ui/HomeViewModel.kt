package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.DownloadEvent
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** The open pull requests of one repo. */
data class RepoSection(val repo: String, val pullRequests: List<PullRequestItem>)

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val repos: List<RepoSection> = emptyList(),
    /** Repos the user has collapsed. */
    val collapsedRepos: Set<String> = emptySet(),
)

class HomeViewModel(
    private val source: PullRequestSource,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val tracker: InstallTracker,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequests = emptyList<PullRequest>()
    private var isLoading = true
    private var error: String? = null
    private var collapsedRepos = emptySet<String>()
    private val downloads = mutableMapOf<String, DownloadState>()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        pullRequests = source.cached()
        refresh()
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
        collapsedRepos = if (repo in collapsedRepos) collapsedRepos - repo else collapsedRepos + repo
        publish()
    }

    /** Call whenever the app comes back to the foreground: an install may have finished. */
    fun onResume() {
        tracker.confirmPending()
        publish()
    }

    fun download(key: String) {
        val pr = pullRequests.firstOrNull { it.key == key } ?: return
        val apk = pr.downloadableApk ?: return
        if (downloadJobs[key]?.isActive == true) return
        downloadJobs[key] = viewModelScope.launch {
            try {
                downloader.download(apk.url, pr.apkFileName).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> setDownload(key, DownloadState.Downloading(event.bytes, event.total))
                        is DownloadEvent.Done -> {
                            setDownload(key, DownloadState.Downloaded(event.file))
                            tracker.onDownloaded(pr, event.file)
                            install(key)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Download of ${pr.key} failed", e)
                setDownload(key, DownloadState.Failed(e.message ?: "Download failed"))
            }
        }
    }

    /** Opens the system installer for an APK that is already downloaded. */
    fun install(key: String) {
        val pr = pullRequests.firstOrNull { it.key == key } ?: return
        val file = (stateOf(pr) as? DownloadState.Downloaded)?.file ?: return
        tracker.onDownloaded(pr, file)
        tracker.onInstallStarted(pr)
        installer.install(file)
        publish()
    }

    private fun setDownload(key: String, state: DownloadState) {
        downloads[key] = state
        publish()
    }

    /** A file left from an earlier session counts as downloaded. */
    private fun stateOf(pr: PullRequest): DownloadState =
        downloads[pr.key]
            ?: downloader.existing(pr.apkFileName)?.let { DownloadState.Downloaded(it) }
            ?: DownloadState.Idle

    /** Merged and closed PRs vanish from the list: drop their state and their downloaded files. */
    private suspend fun forgetClosedPullRequests() {
        val open = pullRequests.map { it.key }.toSet()
        (downloads.keys - open).forEach { key ->
            downloadJobs.remove(key)?.cancel()
            downloads.remove(key)
        }
        downloader.prune(pullRequests.filter { it.downloadableApk != null }.map { it.apkFileName }.toSet())
    }

    private fun publish() {
        val sections = pullRequests
            .groupBy { it.repo }
            .map { (repo, prs) ->
                RepoSection(
                    repo = repo,
                    pullRequests = prs.map { PullRequestItem(it, stateOf(it), tracker.statusFor(it)) },
                )
            }
        _uiState.value = HomeUiState(isLoading = isLoading, error = error, repos = sections, collapsedRepos = collapsedRepos)
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
