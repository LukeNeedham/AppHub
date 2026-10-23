package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.MAIN_BRANCH
import com.lukeneedham.apphub.domain.MainApk
import com.lukeneedham.apphub.domain.MainBuildInfo
import com.lukeneedham.apphub.domain.MainBuildSource
import com.lukeneedham.apphub.domain.MainRun
import com.lukeneedham.apphub.domain.RunState
import com.lukeneedham.apphub.domain.TokenStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The artifact the shared workflow uploads (ci-workflows `android_main_build.yml`). */
const val APK_ARTIFACT = "apk"

/**
 * Reads the main build workflow runs of a repo, and the artifact of the newest successful one. Everything
 * here needs the token: starting a run and downloading an artifact need it, and asking without one would
 * spend the anonymous allowance, so without a token no repo has a main build.
 */
class MainBuildRepository(
    private val api: GithubApi,
    private val tokenStore: TokenStore,
    private val now: () -> Long = System::currentTimeMillis,
) : MainBuildSource {

    /** Repos without the workflow, until when to leave them alone: a repo gains it by a commit, rarely. */
    private val withoutWorkflow = ConcurrentHashMap<String, Long>()

    override suspend fun status(repo: String): MainBuildInfo? {
        if (tokenStore.token.value == null) return null
        if ((withoutWorkflow[repo] ?: 0L) > now()) return null
        val runs = api.mainRuns(repo)
        if (runs == null) {
            withoutWorkflow[repo] = now() + RECHECK_MS
            return null
        }
        withoutWorkflow.remove(repo)

        val apk = runs.filter { stateOf(it) == RunState.Succeeded }.take(RUNS_CHECKED).firstNotNullOfOrNull { run ->
            api.runArtifacts(repo, run.id)
                .firstOrNull { it.name == APK_ARTIFACT && !isExpired(it) }
                ?.let { MainApk(it.id, run.headSha) }
        }
        val tip = api.latestCommit(repo, MAIN_BRANCH)
        val behind = if (apk == null || apk.commit == tip) 0 else api.commitsAhead(repo, apk.commit, tip)
        return MainBuildInfo(
            run = runs.firstOrNull()?.let { MainRun(it.id, it.headSha, stateOf(it)) },
            apk = apk,
            tip = tip,
            commitsBehind = behind,
        )
    }

    override suspend fun start(repo: String) = api.startMainBuild(repo)

    override suspend fun downloadUrl(repo: String, apk: MainApk): String = api.artifactDownloadUrl(repo, apk.artifactId)

    /** A build that has expired is not offered: the main build then shows as not built. */
    private fun isExpired(artifact: ArtifactDto): Boolean =
        artifact.expired || artifact.expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() <= now() }.getOrNull() } == true

    private fun stateOf(run: RunDto): RunState =
        when {
            run.status != "completed" -> RunState.Active
            run.conclusion == "success" -> RunState.Succeeded
            run.conclusion == "cancelled" || run.conclusion == "skipped" -> RunState.Cancelled
            else -> RunState.Failed
        }

    private companion object {
        const val RECHECK_MS = 10 * 60 * 1000L

        /** Successful runs looked at for a live artifact: a day's artifacts expire, so only the newest can be there. */
        const val RUNS_CHECKED = 3
    }
}
