package com.lukeneedham.apphub.domain

import kotlinx.serialization.Serializable
import java.io.File
import java.net.URLEncoder

/** The state of the APK build, as reported by the shared CI comment on a pull request. */
@Serializable
sealed interface ApkBuild {
    val commit: String?

    @Serializable
    /** [builtAt] is the time the CI comment states, as written (the CI's own timezone); null if absent. */
    data class Succeeded(val url: String, override val commit: String?, val builtAt: String? = null) : ApkBuild

    /** [previousUrl] is the stale APK of the last build, kept in the comment while a new one builds. */
    @Serializable
    data class InProgress(override val commit: String?, val previousUrl: String?) : ApkBuild

    @Serializable
    data class Failed(override val commit: String?) : ApkBuild

    @Serializable
    data class Cancelled(override val commit: String?) : ApkBuild
}

@Serializable
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
    /** A Claude Code session linked from the PR description or comments. */
    val sessionUrl: String? = null,
    /** The PR description, as written (Markdown); null when empty. */
    val description: String? = null,
) {
    val key: String get() = "$repo#$number"

    /**
     * The APK to offer: this commit's if its build succeeded, or while a newer build runs the previous
     * one, flagged [ApkTarget.isStale] because it does not include the latest commits.
     */
    val downloadableApk: ApkTarget?
        get() = when (val build = apk) {
            is ApkBuild.Succeeded -> ApkTarget(build.url, build.commit ?: tagOf(build.url), isStale = false)
            is ApkBuild.InProgress -> build.previousUrl?.let { ApkTarget(it, "stale-${tagOf(it)}", isStale = true) }
            else -> null
        }

    /** When the APK of the latest commit was built, as the CI comment states it; null while it is not known. */
    val builtAt: String? get() = (apk as? ApkBuild.Succeeded)?.builtAt

    /** Unique per PR and build, so a new build is downloaded again and old files can be pruned. */
    val apkFileName: String
        get() = "${repo.replace('/', '_')}-$number-${downloadableApk?.buildId ?: "unknown"}.apk"

    /** The APK on offer as something that can be downloaded and installed, if there is one. */
    val offer: ApkOffer?
        get() = downloadableApk?.let { ApkOffer(key, repo, it.buildId, apkFileName) }

    private fun tagOf(url: String) = url.substringAfter("/releases/download/").substringBefore('/')
}

/** An APK that can be downloaded. [buildId] tells builds apart: the commit, or the release tag for a stale one. */
data class ApkTarget(val url: String, val buildId: String, val isStale: Boolean)

/** The GitHub page of the repo `owner/name`. */
fun repoUrl(repo: String) = "https://github.com/$repo"

/** The app icon every app keeps at the root of its repo `owner/name`; absent (404) for repos without one. */
fun repoIconUrl(repo: String) = "https://raw.githubusercontent.com/$repo/HEAD/app-icon.png"

/** Opens Claude Code ready to start a new session on the repo `owner/name`. */
fun newClaudeSessionUrl(repo: String) = "https://claude.ai/code/new?repo=" + URLEncoder.encode(repo, "UTF-8")

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

/** GitHub's request allowance: [limit] an hour, [remaining] of it left. */
data class RateLimitInfo(val limit: Int, val remaining: Int)

/** GitHub answered 401: the saved token is wrong, revoked or expired. */
class InvalidTokenException : Exception("GitHub rejected the token")

class RateLimitException(val resetEpochSeconds: Long?) : Exception("GitHub rate limit reached")
