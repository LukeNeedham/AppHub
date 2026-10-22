package com.lukeneedham.apphub.domain

/** The branch built on demand. */
const val MAIN_BRANCH = "main"

/** The caller workflow every app keeps to build [MAIN_BRANCH] on demand (ci-workflows `android_main_build.yml`). */
const val MAIN_BUILD_WORKFLOW = "build_main.yml"

enum class RunState { Active, Succeeded, Failed, Cancelled }

/** One run of the main build workflow, of [commit]. */
data class MainRun(val id: Long, val commit: String, val state: RunState)

/** The APK artifact of a successful run: the CI keeps it for a day. */
data class MainApk(val artifactId: Long, val commit: String)

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

/** A line under the main build: [warn] when it says the build is not the latest. */
data class MainNote(val text: String, val warn: Boolean = false)

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

    val buildLabel: String
        get() = when {
            info.apk == null -> "Build main"
            info.commitsBehind > 0 -> "Build latest main"
            else -> "Rebuild"
        }

    val note: MainNote?
        get() {
            val behind = info.commitsBehind
            val commit = info.apk?.commit?.let(::shortCommit)
            val newerFailed = info.run?.state == RunState.Failed && info.apk != null
            return when (phase) {
                MainPhase.Requested -> MainNote("Starting the build of main…")
                MainPhase.Building -> MainNote("Building main at ${shortCommit(info.run?.commit.orEmpty())}…")
                MainPhase.Failed -> MainNote("The build of ${shortCommit(info.run?.commit.orEmpty())} failed", warn = true)
                MainPhase.NoBuild -> MainNote("No build of main yet. A build is kept for a day.")
                MainPhase.Installed -> when {
                    behind > 0 -> MainNote("${commits(behind)} on main since the installed build ($commit)", warn = true)
                    else -> MainNote("Installed: this is the latest main ($commit)")
                }
                MainPhase.Ready -> when {
                    newerFailed -> MainNote("The newest build failed; this is an older one ($commit)", warn = true)
                    behind > 0 -> MainNote("${commits(behind)} on main since this build ($commit)", warn = true)
                    else -> MainNote("Built from the latest main ($commit)")
                }
            }
        }

    private fun commits(count: Int) = if (count == 1) "1 new commit" else "$count new commits"
}

fun mainKey(repo: String) = "$repo@$MAIN_BRANCH"

fun shortCommit(sha: String) = sha.take(7)
