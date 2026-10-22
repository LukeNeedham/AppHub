package com.lukeneedham.apphub.ui

import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
import com.lukeneedham.apphub.domain.ApkOffer
import com.lukeneedham.apphub.domain.DownloadEvent
import com.lukeneedham.apphub.domain.DownloadState
import com.lukeneedham.apphub.domain.InstallTracker
import com.lukeneedham.apphub.domain.PullRequest
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The downloads and installs started from any screen. One instance serves the app, so the list and a
 * PR's own page agree on what is downloading. Everything runs on the main thread, from [scope].
 */
class ApkDownloads(
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val tracker: InstallTracker,
    private val scope: CoroutineScope,
) {
    private val states = mutableMapOf<String, DownloadState>()
    private val jobs = mutableMapOf<String, Job>()

    private val _changes = MutableStateFlow(0)

    /** Ticks whenever a download state or an install status may have changed: read the states again. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /**
     * A file left from an earlier session counts as downloaded. A download of an older build of the PR
     * does not: the file is named per build, and installing it would put the previous commit on the device.
     */
    fun stateOf(pr: PullRequest): DownloadState = stateOf(pr.key, pr.apkFileName)

    fun stateOf(offer: ApkOffer): DownloadState = stateOf(offer.key, offer.fileName)

    private fun stateOf(key: String, fileName: String): DownloadState =
        states[key]?.takeUnless { it is DownloadState.Downloaded && it.file.name != fileName }
            ?: downloader.existing(fileName)?.let { DownloadState.Downloaded(it) }
            ?: DownloadState.Idle

    fun download(pr: PullRequest) {
        val apk = pr.downloadableApk ?: return
        val offer = pr.offer ?: return
        download(offer) { apk.url }
    }

    /**
     * Downloads [offer] from the link [url] gives, then opens the installer. The link is asked for as the
     * download starts: a workflow artifact's link only lives for a minute. [fromZip]: the link leads to a
     * zip holding the APK.
     */
    fun download(offer: ApkOffer, fromZip: Boolean = false, url: suspend () -> String) {
        val key = offer.key
        if (jobs[key]?.isActive == true) return
        jobs[key] = scope.launch {
            try {
                set(key, DownloadState.Downloading(0, null))
                downloader.download(url(), offer.fileName, fromZip).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> set(key, DownloadState.Downloading(event.bytes, event.total))
                        is DownloadEvent.Done -> {
                            set(key, DownloadState.Downloaded(event.file))
                            tracker.onDownloaded(offer.repo, event.file)
                            install(offer)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("Download of $key failed", e)
                set(key, DownloadState.Failed(e.message ?: "Download failed"))
            }
        }
    }

    /** Opens the system installer for an APK that is already downloaded. */
    fun install(pr: PullRequest) {
        pr.offer?.let(::install)
    }

    fun install(offer: ApkOffer) {
        val file = (stateOf(offer) as? DownloadState.Downloaded)?.file ?: return
        tracker.onDownloaded(offer.repo, file)
        tracker.onInstallStarted(offer.repo, offer.buildId)
        installer.install(file)
        notifyChanged()
    }

    /** Starts the installed app of a PR whose build is installed. */
    fun open(pr: PullRequest) = open(pr.repo)

    /** Starts the installed app built from [repo]. */
    fun open(repo: String) {
        val packageName = tracker.packageNameFor(repo) ?: return
        installer.open(packageName)
    }

    /** Drops the state of every build whose key is not in [open], cancelling its download. */
    fun retainOnly(open: Set<String>) {
        (states.keys - open).forEach { key ->
            jobs.remove(key)?.cancel()
            states.remove(key)
        }
    }

    private fun set(key: String, state: DownloadState) {
        states[key] = state
        notifyChanged()
    }

    private fun notifyChanged() {
        _changes.value++
    }
}
