package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Where the open pull requests come from. */
interface PullRequestSource {
    suspend fun openPullRequests(): List<PullRequest>

    /** What the last successful (or partly successful) load found, to show before the next one finishes. */
    fun cached(): List<PullRequest> = emptyList()
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

    /** Deletes every downloaded APK whose name is not in [keep]. A download in progress for a kept name survives. */
    suspend fun prune(keep: Set<String>)
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

/** The user's GitHub token, if they gave one. It raises the rate limit from 60 to 5,000 requests an hour. */
interface TokenStore {
    val token: StateFlow<String?>

    /** Saves [token], or forgets it when null. */
    fun set(token: String?)
}

/** Remembers what AppHub installed, to tell whether the installed app is a PR's build. */
interface InstallTracker {
    fun statusFor(pr: PullRequest): InstallStatus
    fun onDownloaded(pr: PullRequest, file: File)
    fun onInstallStarted(pr: PullRequest)

    /** Call when AppHub returns to the foreground: notes whether a started install went through. */
    fun confirmPending()
}
