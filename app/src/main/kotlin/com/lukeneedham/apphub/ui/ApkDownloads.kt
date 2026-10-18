package com.lukeneedham.apphub.ui

import com.lukeneedham.apphub.domain.ApkDownloader
import com.lukeneedham.apphub.domain.ApkInstaller
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

    /** A file left from an earlier session counts as downloaded. */
    fun stateOf(pr: PullRequest): DownloadState =
        states[pr.key]
            ?: downloader.existing(pr.apkFileName)?.let { DownloadState.Downloaded(it) }
            ?: DownloadState.Idle

    fun download(pr: PullRequest) {
        val apk = pr.downloadableApk ?: return
        val key = pr.key
        if (jobs[key]?.isActive == true) return
        jobs[key] = scope.launch {
            try {
                downloader.download(apk.url, pr.apkFileName).collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> set(key, DownloadState.Downloading(event.bytes, event.total))
                        is DownloadEvent.Done -> {
                            set(key, DownloadState.Downloaded(event.file))
                            tracker.onDownloaded(pr, event.file)
                            install(pr)
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
        val file = (stateOf(pr) as? DownloadState.Downloaded)?.file ?: return
        tracker.onDownloaded(pr, file)
        tracker.onInstallStarted(pr)
        installer.install(file)
        notifyChanged()
    }

    /** Starts the installed app of a PR whose build is installed. */
    fun open(pr: PullRequest) {
        val packageName = tracker.packageNameFor(pr) ?: return
        installer.open(packageName)
    }

    /** Drops the state of every PR whose key is not in [open], cancelling its download. */
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
