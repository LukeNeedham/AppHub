package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Where the open pull requests come from. */
interface PullRequestSource {
    suspend fun openPullRequests(): List<PullRequest>

    /** The `owner/name` of every repo to list, with or without open pull requests. */
    suspend fun repositories(): List<String> = emptyList()

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

    /**
     * Emits progress, then [DownloadEvent.Done]. Throws on a network or disk error. With [fromZip] the
     * download is a zip (a workflow artifact) and the APK inside it is what is kept.
     */
    fun download(url: String, fileName: String, fromZip: Boolean = false): Flow<DownloadEvent>

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

    /** Starts the installed app [packageName]; false when it has no launcher activity or is not installed. */
    fun open(packageName: String): Boolean
}

/** The user's GitHub token, if they gave one. It raises the rate limit from 60 to 5,000 requests an hour. */
interface TokenStore {
    val token: StateFlow<String?>

    /** Saves [token], or forgets it when null. */
    fun set(token: String?)
}

/** Remembers what AppHub installed, to tell whether the installed app is a given build of a repo. */
interface InstallTracker {
    /** [buildId] is the commit of the build. */
    fun statusFor(repo: String, buildId: String): InstallStatus

    /** The package name of the app built from [repo], once known. */
    fun packageNameFor(repo: String): String?
    fun onDownloaded(repo: String, file: File)
    fun onInstallStarted(repo: String, buildId: String)

    /** Call when AppHub returns to the foreground: notes whether a started install went through. */
    fun confirmPending()
}

fun InstallTracker.statusFor(offer: ApkOffer?): InstallStatus =
    offer?.let { statusFor(it.repo, it.buildId) } ?: InstallStatus.Unknown
