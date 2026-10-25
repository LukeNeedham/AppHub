package com.lukeneedham.apphub.domain

/** The branch built on demand. */
const val MAIN_BRANCH = "main"

/** The caller workflow every app keeps to build [MAIN_BRANCH] on demand (ci-workflows `android_main_build.yml`). */
const val MAIN_BUILD_WORKFLOW = "build_main.yml"

enum class RunState { Active, Succeeded, Failed, Cancelled }

/** One run of the main build workflow, of [commit]. */
data class MainRun(val id: Long, val commit: String, val state: RunState)

/** The APK artifact of a successful run: the CI keeps it for a day. [builtAtMillis] is when it was uploaded, if known. */
data class MainApk(val artifactId: Long, val commit: String, val builtAtMillis: Long? = null)

/**
 * What GitHub knows about the main builds of a repo. [run] is the newest run, [apk] the newest build whose
 * APK is still there (the newest run's, or an older one while it builds or after it failed). [tip] is the
 * newest commit of main and [commitsBehind] how many commits [apk] lacks.
 */
data class MainBuildInfo(
    val run: MainRun?,
    val apk: MainApk?,
    val tip: String?,
    val commitsBehind: Int,
)

/** Where the builds of main come from. */
interface MainBuildSource {
    /** Null when the repo has no main build workflow, or when there is no token to ask with. */
    suspend fun status(repo: String): MainBuildInfo?

    /** Starts a build of the tip of main. */
    suspend fun start(repo: String)

    /** A short-lived link to the zip holding [apk]: use it at once. */
    suspend fun downloadUrl(repo: String, apk: MainApk): String
}

/** A build that can be downloaded and installed: a PR's, or one of main. [key] is unique across both. */
data class ApkOffer(val key: String, val repo: String, val buildId: String, val fileName: String)

enum class MainPhase { NoBuild, Requested, Building, Failed, Ready, Installed }

/**
 * The main build of one repo as the home screen shows it. [requested] is true from the press of the button
 * until the new run appears in GitHub's list.
 */
data class MainBuildItem(
    val repo: String,
    val info: MainBuildInfo,
    val requested: Boolean,
    val download: DownloadState,
    val install: InstallStatus,
    val error: String? = null,
) {
    val key: String get() = mainKey(repo)

    /** The GitHub Actions run of the newest build, which is the one on its way while [phase] is [MainPhase.Building]. */
    val runUrl: String? get() = info.run?.let { "https://github.com/$repo/actions/runs/${it.id}" }

    val offer: ApkOffer?
        get() = info.apk?.let {
            ApkOffer(key, repo, it.commit, "${repo.replace('/', '_')}-main-${it.commit}.apk")
        }

    val phase: MainPhase
        get() = when {
            requested -> MainPhase.Requested
            info.run?.state == RunState.Active -> MainPhase.Building
            info.apk != null -> if (install == InstallStatus.Installed) MainPhase.Installed else MainPhase.Ready
            info.run?.state == RunState.Failed -> MainPhase.Failed
            else -> MainPhase.NoBuild
        }

    /** A build can be asked for unless one is already on its way. */
    val canBuild: Boolean get() = phase != MainPhase.Requested && phase != MainPhase.Building

    val buildLabel: String get() = if (info.apk == null) "Build main" else "Build latest main"

    /** A new build is only worth offering when there is none, or the one there lacks commits of main. */
    val offersBuild: Boolean get() = info.apk == null || info.commitsBehind > 0

    /** How far the build in [info] lags main, short enough for a status pill; null while there is no build. */
    val behindText: String?
        get() = when {
            info.apk == null -> null
            info.commitsBehind > 0 -> "${commits(info.commitsBehind)} behind"
            else -> "Up to date"
        }

    private fun commits(count: Int) = if (count == 1) "1 commit" else "$count commits"
}

fun mainKey(repo: String) = "$repo@$MAIN_BRANCH"

fun shortCommit(sha: String) = sha.take(7)
