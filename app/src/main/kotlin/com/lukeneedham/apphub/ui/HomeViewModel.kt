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

data class PullRequestItem(
    val pr: PullRequest,
    val download: DownloadState,
    val install: InstallStatus,
)

/** One repo and its open pull requests, of which there may be none. */
data class RepoSection(
    val repo: String,
    val pullRequests: List<PullRequestItem>,
    /** Null when the repo cannot build main on demand, or there is no token to do it with. */
    val mainBuild: MainBuildItem? = null,
) {
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
    private val mainBuilds: MainBuildSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // All of this is touched on the main thread only, from viewModelScope
    private var pullRequests = emptyList<PullRequest>()
    private var isLoading = true
    private var error: String? = null
    private var repositories = emptyList<String>()
    private var toggledRepos = emptySet<String>()
    private var mainInfo = emptyMap<String, MainBuildInfo>()
    private var requested = emptyMap<String, Requested>()
    private var mainErrors = emptyMap<String, String>()
    private var polling: Job? = null

    /** A build asked for: [baseline] is the id of the newest run before, so a newer one is the one asked for. */
    private data class Requested(val baseline: Long, val polls: Int = 0)

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
            startPolling()
            // After the main builds, whose downloads must be kept as well
            if (listed) forgetClosedPullRequests()
            isLoading = false
            publish()
        }
    }

    /** Asks for a build of the tip of main, and follows it until it is done. */
    fun buildMain(repo: String) {
        val current = mainInfo[repo] ?: return
        if (repo in requested || current.run?.state == RunState.Active) return
        requested = requested + (repo to Requested(current.run?.id ?: 0L))
        mainErrors = mainErrors - repo
        publish()
        viewModelScope.launch {
            try {
                mainBuilds.start(repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Could not start the main build of $repo", e)
                requested = requested - repo
                mainErrors = mainErrors + (repo to describe(e))
                publish()
                return@launch
            }
            startPolling()
        }
    }

    fun downloadMain(repo: String) {
        val item = mainItem(repo) ?: return
        val apk = item.info.apk ?: return
        val offer = item.offer ?: return
        downloads.download(offer, fromZip = true) { mainBuilds.downloadUrl(repo, apk) }
    }

    fun cancelMain(repo: String) = downloads.cancel(mainKey(repo))

    fun installMain(repo: String) {
        mainItem(repo)?.offer?.let(downloads::install)
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
        startPolling()
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
        downloads.retainOnly(pullRequests.map { it.key }.toSet() + mainInfo.keys.map(::mainKey))
        val files = pullRequests.mapNotNull { it.offer?.fileName } + mainInfo.keys.mapNotNull { mainItem(it)?.offer?.fileName }
        downloader.prune(files.toSet())
    }

    private fun allRepos() = (pullRequests.map { it.repo } + repositories).distinct()

    private fun mainItem(repo: String): MainBuildItem? {
        val info = mainInfo[repo] ?: return null
        val item = MainBuildItem(repo, info, repo in requested, DownloadState.Idle, InstallStatus.Unknown, mainErrors[repo])
        val offer = item.offer ?: return item
        return item.copy(download = downloads.stateOf(offer), install = tracker.statusFor(offer))
    }

    /** Loads the main build of every repo in [repos]; one that fails keeps what it showed. */
    private suspend fun refreshMainBuilds(repos: List<String>) {
        val limit = Semaphore(4)
        coroutineScope {
            repos.map { repo ->
                async {
                    try {
                        val info = limit.withPermit { mainBuilds.status(repo) }
                        applyMain(repo, info)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e("Could not load the main build of $repo", e)
                    }
                }
            }.awaitAll()
        }
    }

    private fun applyMain(repo: String, info: MainBuildInfo?) {
        if (info == null) {
            mainInfo = mainInfo - repo
            requested = requested - repo
            return
        }
        mainInfo = mainInfo + (repo to info)
        val asked = requested[repo]
        // The run asked for is in the list: from here its own state says what is going on
        if (asked != null && (info.run?.id ?: 0L) > asked.baseline) requested = requested - repo
    }

    private fun isBusy(repo: String) = repo in requested || mainInfo[repo]?.run?.state == RunState.Active

    /** While a main build is on its way, asks again every few seconds, until none is. */
    private fun startPolling() {
        if (polling?.isActive == true || (mainInfo.keys + requested.keys).none(::isBusy)) return
        polling = viewModelScope.launch {
            while (true) {
                val busy = (mainInfo.keys + requested.keys).filter(::isBusy)
                if (busy.isEmpty()) break
                delay(POLL_MS)
                giveUpOnLateRuns()
                refreshMainBuilds(busy)
                publish()
            }
        }
    }

    /** A build that GitHub never started (for example a disabled workflow) must not be waited for forever. */
    private fun giveUpOnLateRuns() {
        val aged = requested.mapValues { it.value.copy(polls = it.value.polls + 1) }
        val late = aged.filterValues { it.polls > MAX_POLLS_FOR_RUN }.keys
        requested = aged - late
        mainErrors = mainErrors + late.associateWith { "GitHub did not start the build. Check that $it has build_main.yml on main." }
    }

    private fun publish() {
        val withPullRequests = pullRequests
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

    private companion object {
        const val POLL_MS = 10_000L

        /** Polls to wait for a requested run to show up: about two minutes. */
        const val MAX_POLLS_FOR_RUN = 12
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
