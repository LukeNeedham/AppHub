package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.FeatureKind
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.isAutoMerge
import com.lukeneedham.apphub.domain.mainKey
import com.lukeneedham.apphub.domain.statusFor
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The page of one app, [repo] being `owner/name`: the same section as its card on Home. */
class AppViewModel(
    private val repo: String,
    private val source: PullRequestSource,
    private val downloads: ApkDownloads,
    private val tracker: InstallTracker,
    private val mainBuilds: MainBuildSource,
    private val displaySettings: com.lukeneedham.apphub.domain.DisplaySettings,
    private val featureManager: FeatureManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequests = source.cached().filter { it.repo == repo }
    private var mainApk: MainApk? = null
    private var isLoading = true
    private var error: String? = null

    init {
        refresh()
        viewModelScope.launch { downloads.changes.drop(1).collect { publish() } }
        viewModelScope.launch { displaySettings.showAutoMergePrs.drop(1).collect { publish() } }
        viewModelScope.launch { featureManager.features.collect { publish() } }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            publish()
            try {
                pullRequests = source.openPullRequests().filter { it.repo == repo }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not load pull requests of $repo", e)
                error = describe(e)
            }
            try {
                mainApk = mainBuilds.status(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not load the main build of $repo", e)
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

    fun download(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::download)
    }

    fun cancelDownload(key: String) = downloads.cancel(key)

    fun install(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::install)
    }

    fun open(key: String) {
        pullRequests.firstOrNull { it.key == key }?.let(downloads::open)
    }

    fun downloadMain() {
        val item = mainItem() ?: return
        downloads.download(item.offer) { item.apk.downloadUrl }
    }

    fun cancelMain() = downloads.cancel(mainKey(repo))

    fun installMain() {
        mainItem()?.let { downloads.install(it.offer) }
    }

    fun openMain() = downloads.open(repo)

    private fun mainItem(): MainBuildItem? {
        val apk = mainApk ?: return null
        val item = MainBuildItem(repo, apk, DownloadState.Idle, InstallStatus.Unknown)
        return item.copy(download = downloads.stateOf(item.offer), install = tracker.statusFor(item.offer))
    }

    private fun publish() {
        val showAutoMerge = displaySettings.showAutoMergePrs.value
        val features = featureManager.features.value.filter { feature ->
            feature.repo == repo && feature.kind == FeatureKind.Feature &&
                (feature.prNumber == null || pullRequests.any { it.number == feature.prNumber })
        }
        val section = RepoSection(
            repo = repo,
            pullRequests = pullRequests
                .filter { showAutoMerge || !it.isAutoMerge() }
                .map { PullRequestItem(it, downloads.stateOf(it), tracker.statusFor(it.offer)) },
            mainBuild = mainItem(),
            features = features,
        )
        _uiState.value = AppUiState(isLoading = isLoading, error = error, section = section)
    }

    private fun describe(e: Exception): String =
        when (e) {
            is RateLimitException -> "GitHub's rate limit for anonymous requests is used up."
            is InvalidTokenException -> "GitHub rejected your token. Replace or remove it in Settings."
            else -> e.message ?: "Could not load pull requests"
        }
}
