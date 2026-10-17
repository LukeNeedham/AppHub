package com.lukeneedham.apphub.domain

import java.io.File

/** The state of the APK build, as reported by the shared CI comment on a pull request. */
sealed interface ApkBuild {
    val commit: String?

    data class Succeeded(val url: String, override val commit: String?) : ApkBuild

    /** [previousUrl] is the stale APK of the last build, kept in the comment while a new one builds. */
    data class InProgress(override val commit: String?, val previousUrl: String?) : ApkBuild

    data class Failed(override val commit: String?) : ApkBuild

    data class Cancelled(override val commit: String?) : ApkBuild
}

data class PullRequest(
    /** `owner/name` */
    val repo: String,
    val number: Int,
    val title: String,
    val author: String,
    val url: String,
    val isDraft: Boolean,
    /** Null when no CI comment has been posted yet. */
    val apk: ApkBuild?,
    val webUrl: String?,
) {
    val key: String get() = "$repo#$number"

    /** The APK to offer, if a build has succeeded. */
    val downloadableApk: ApkBuild.Succeeded? get() = apk as? ApkBuild.Succeeded

    /** Unique per PR and commit, so a new commit is downloaded again and old files can be pruned. */
    val apkFileName: String
        get() = "${repo.replace('/', '_')}-$number-${downloadableApk?.commit ?: "unknown"}.apk"
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val bytes: Long, val total: Long?) : DownloadState
    data class Downloaded(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** How the APK on a PR relates to what is installed on this device. */
enum class InstallStatus {
    /** Nothing is known about the app yet, as it has never been downloaded through AppHub. */
    Unknown,
    NotInstalled,

    /** The installed app is a different build from this PR's. */
    DifferentBuild,

    /** The installed app is this PR's build. */
    Installed,
}

class RateLimitException(val resetEpochSeconds: Long?) : Exception("GitHub rate limit reached")
