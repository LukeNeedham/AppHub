package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.TokenStore
import java.util.concurrent.ConcurrentHashMap

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
        val apk = release?.toMainApk()
        if (apk == null) {
            withoutBuild[repo] = now() + RECHECK_MS
            return null
        }
        withoutBuild.remove(repo)
        return apk
    }

    private companion object {
        const val RECHECK_MS = 10 * 60 * 1000L
    }
}
