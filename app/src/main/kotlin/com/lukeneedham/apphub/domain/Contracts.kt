package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Where the open pull requests come from. */
interface PullRequestSource {
    suspend fun openPullRequests(): List<PullRequest>
}

sealed interface DownloadEvent {
    data class Progress(val bytes: Long, val total: Long?) : DownloadEvent
    data class Done(val file: File) : DownloadEvent
}

interface ApkDownloader {
    /** A previously downloaded file for [fileName], if it is still there. */
    fun existing(fileName: String): File?

    /** Emits progress, then [DownloadEvent.Done]. Throws on a network or disk error. */
    fun download(url: String, fileName: String): Flow<DownloadEvent>

    /** Deletes every downloaded APK whose name is not in [keep]. */
    fun prune(keep: Set<String>)
}

enum class InstallLaunch {
    /** The system installer is open. */
    Launched,

    /** The user must allow AppHub to install apps; the settings page was opened. */
    NeedsPermission,
}

interface ApkInstaller {
    fun install(file: File): InstallLaunch
}

/** Remembers what AppHub installed, to tell whether the installed app is a PR's build. */
interface InstallTracker {
    fun statusFor(pr: PullRequest): InstallStatus
    fun onDownloaded(pr: PullRequest, file: File)
    fun onInstallStarted(pr: PullRequest)

    /** Call when AppHub returns to the foreground: notes whether a started install went through. */
    fun confirmPending()
}
