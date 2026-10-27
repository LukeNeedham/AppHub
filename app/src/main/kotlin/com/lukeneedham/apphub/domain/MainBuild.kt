package com.lukeneedham.apphub.domain

/** The branch whose latest build is shown. */
const val MAIN_BRANCH = "main"

/** The rolling pre-release every app's main build publishes the debug APK to (ci-workflows `android_main_build.yml`). */
const val MAIN_RELEASE_TAG = "main-build"

/** The one asset of [MAIN_RELEASE_TAG]: the APK. */
const val MAIN_APK_ASSET = "app-debug.apk"

/**
 * The latest build of main: the APK of the release [MAIN_RELEASE_TAG], which CI replaces on every push to main.
 * [commit] is the commit it was built from, [downloadUrl] a public link to the APK, and [builtAtMillis] when it was
 * uploaded, if known.
 */
data class MainApk(val commit: String, val downloadUrl: String, val builtAtMillis: Long? = null)

/** Where the builds of main come from. */
interface MainBuildSource {
    /** The latest main build of the repo; null when it has none (no such release), or when there is no token to ask with. */
    suspend fun status(repo: String): MainApk?
}

/** A build that can be downloaded and installed: a PR's, or one of main. [key] is unique across both. */
data class ApkOffer(val key: String, val repo: String, val buildId: String, val fileName: String)

enum class MainPhase { Ready, Installed }

/** The latest main build of one repo as the home screen shows it. */
data class MainBuildItem(
    val repo: String,
    val apk: MainApk,
    val download: DownloadState,
    val install: InstallStatus,
) {
    val key: String get() = mainKey(repo)

    val offer: ApkOffer
        get() = ApkOffer(key, repo, apk.commit, "${repo.replace('/', '_')}-main-${apk.commit}.apk")

    val phase: MainPhase
        get() = if (install == InstallStatus.Installed) MainPhase.Installed else MainPhase.Ready
}

fun mainKey(repo: String) = "$repo@$MAIN_BRANCH"

fun shortCommit(sha: String) = sha.take(7)
