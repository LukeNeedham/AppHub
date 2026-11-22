package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.TokenStore
import com.lukeneedham.apphub.domain.normalizeProductionUrl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val urlsMutex = Mutex()
    private var urls: Map<String, String> = emptyMap()
    private var urlsUntil = 0L

    /** From `apps.json` of the common repo, which is read again after [RECHECK_MS] rather than once per repo. */
    override suspend fun productionUrl(repo: String): String? =
        normalizeProductionUrl(productionUrls()[repo.lowercase()])

    private suspend fun productionUrls(): Map<String, String> = urlsMutex.withLock {
        if (urlsUntil <= now()) {
            urls = api.productionUrls()
            urlsUntil = now() + RECHECK_MS
        }
        urls
    }

    private companion object {
        const val RECHECK_MS = 10 * 60 * 1000L
    }
}
