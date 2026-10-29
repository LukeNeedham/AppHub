package com.lukeneedham.apphub.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.DisplaySettings
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallStatus
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildItem
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.apphub.domain.PullRequestSource
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.domain.isAutoMerge
import com.lukeneedham.apphub.domain.mainKey
import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import com.lukeneedham.apphub.domain.statusFor
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.DateFormat
import java.util.Date

data class PullRequestItem(
    val pr: PullRequest,
    val download: DownloadState,
    val install: InstallStatus,
)

/** One repo and its open pull requests, of which there may be none. */
data class RepoSection(
    val repo: String,
    val pullRequests: List<PullRequestItem>,
    /** Null when the repo has no main build (no `main-build` release), or there is no token to look it up with. */
    val mainBuild: MainBuildItem? = null,
) {
    val url: String get() = repoUrl(repo)
    val claudeUrl: String get() = newClaudeSessionUrl(repo)
}

class HomeViewModel(
    private val source: PullRequestSource,
    private val downloader: ApkDownloader,
    private val downloads: ApkDownloads,
    private val tracker: InstallTracker,
    private val tokenStore: TokenStore,
    private val mainBuilds: MainBuildSource,
    private val displaySettings: DisplaySettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequests = emptyList<PullRequest>()
    private var isLoading = true
    private var error: String? = null
    private var repositories = emptyList<String>()
    private var toggledRepos = emptySet<String>()
    private var mainApks = emptyMap<String, MainApk>()

    init {
        pullRequests = source.cached()
        refresh()
        // Downloads and installs started here or on a PR's own page
        viewModelScope.launch { downloads.changes.drop(1).collect { publish() } }
        // A new or removed token changes what GitHub allows and shows: load again
        viewModelScope.launch { tokenStore.token.drop(1).collect { refresh() } }
        // Showing or hiding auto merge PRs only changes the list, so there is nothing to load
        viewModelScope.launch { displaySettings.showAutoMergePrs.drop(1).collect { publish() } }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            publish()
            var listed = false
            try {
                pullRequests = source.openPullRequests()
                error = null
                listed = true
                repositories = source.repositories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not load pull requests", e)
                error = describe(e)
            }
            refreshMainBuilds(allRepos())
            // After the main builds, whose downloads must be kept as well
            if (listed) forgetClosedPullRequests()
            isLoading = false
            publish()
        }
    }

    fun downloadMain(repo: String) {
        val item = mainItem(repo) ?: return
        downloads.download(item.offer) { item.apk.downloadUrl }
    }

    fun cancelMain(repo: String) = downloads.cancel(mainKey(repo))

    fun installMain(repo: String) {
        mainItem(repo)?.let { downloads.install(it.offer) }
    }

    fun openMain(repo: String) = downloads.open(repo)

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

    fun cancelDownload(key: String) = downloads.cancel(key)

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
        downloads.retainOnly(pullRequests.map { it.key }.toSet() + mainApks.keys.map(::mainKey))
        val files = pullRequests.mapNotNull { it.offer?.fileName } + mainApks.keys.mapNotNull { mainItem(it)?.offer?.fileName }
        downloader.prune(files.toSet())
    }

    private fun allRepos() = (pullRequests.map { it.repo } + repositories).distinct()

    private fun mainItem(repo: String): MainBuildItem? {
        val apk = mainApks[repo] ?: return null
        val item = MainBuildItem(repo, apk, DownloadState.Idle, InstallStatus.Unknown)
        return item.copy(download = downloads.stateOf(item.offer), install = tracker.statusFor(item.offer))
    }

    /** Loads the main build of every repo in [repos]; one that fails keeps what it showed. */
    private suspend fun refreshMainBuilds(repos: List<String>) {
        val limit = Semaphore(4)
        coroutineScope {
            repos.map { repo ->
                async {
                    try {
                        applyMain(repo, limit.withPermit { mainBuilds.status(repo) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("Could not load the main build of $repo", e)
                    }
                }
            }.awaitAll()
        }
    }

    private fun applyMain(repo: String, apk: MainApk?) {
        mainApks = if (apk == null) mainApks - repo else mainApks + (repo to apk)
    }

    private fun publish() {
        val showAutoMerge = displaySettings.showAutoMergePrs.value
        val withPullRequests = pullRequests
            .filter { showAutoMerge || !it.isAutoMerge() }
            .groupBy { it.repo }
            .map { (repo, prs) ->
                RepoSection(
                    repo = repo,
                    pullRequests = prs.map { PullRequestItem(it, downloads.stateOf(it), tracker.statusFor(it.offer)) },
                    mainBuild = mainItem(repo),
                )
            }
        // Repos with no open pull requests come after the ones that have some
        val withNone = (repositories - withPullRequests.map { it.repo }.toSet())
            .map { RepoSection(repo = it, pullRequests = emptyList(), mainBuild = mainItem(it)) }
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
