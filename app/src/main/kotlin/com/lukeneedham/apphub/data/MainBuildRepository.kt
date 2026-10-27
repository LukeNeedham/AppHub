package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.MAIN_APK_ASSET
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.TokenStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The line of the release notes that says which commit the APK is of (ci-workflows `scripts/main_build.py`). */
private val COMMIT_LINE = Regex("^commit: ([0-9a-f]{40})\\s*$", RegexOption.MULTILINE)

/**
 * Reads the latest main build of a repo: the release `main-build`, whose APK the CI replaces on every push to main.
 * Everything here needs the token, as asking without one would spend the anonymous allowance on every repo, so without
 * a token no repo has a main build.
 */
class MainBuildRepository(
    private val api: GithubApi,
    private val tokenStore: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : MainBuildSource {

    /** Repos without the release, until when to leave them alone: a repo gains it by a commit, rarely. */
    private val withoutBuild = ConcurrentHashMap<String, Long>()

    override suspend fun status(repo: String): MainApk? {
        if (tokenStore.token.value == null) return null
        if ((withoutBuild[repo] ?: 0L) > now()) return null
        val release = api.mainRelease(repo)
        val apk = release?.let(::apkOf)
        if (apk == null) {
            withoutBuild[repo] = now() + RECHECK_MS
            return null
        }
        withoutBuild.remove(repo)
        return apk
    }

    /** Null when the release has no APK or does not say which commit it is of (not a release of the main build). */
    private fun apkOf(release: ReleaseDto): MainApk? {
        val asset = release.assets.firstOrNull { it.name == MAIN_APK_ASSET } ?: return null
        val commit = COMMIT_LINE.find(release.body.orEmpty())?.groupValues?.get(1) ?: return null
        val builtAt = asset.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        return MainApk(commit, asset.downloadUrl, builtAt)
    }

    private companion object {
        const val RECHECK_MS = 10 * 60 * 1000L
    }
}
