package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MAIN_BRANCH
import com.lukeneedham.apphub.domain.MAIN_BUILD_WORKFLOW
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * GitHub REST calls. Without a token only public repos are visible and the limit is 60 requests an hour;
 * with one it is 5,000. The client should have an HTTP cache: GitHub answers with ETags, and a
 * revalidated `304` does not count against the rate limit.
 */
interface GithubApi {
    /** Every open PR, newest activity first. */
    suspend fun openPullRequests(): List<IssueDto>

    /** The `owner/name` of every non-archived repo of the owner, most recently updated first. */
    suspend fun repositories(): List<String>

    /** The first 100 comments of the PR, oldest first. */
    suspend fun comments(repo: String, number: Int): List<CommentDto>

    /** The newest runs of the main build workflow on main, newest first; null when the repo has no such workflow. */
    suspend fun mainRuns(repo: String): List<RunDto>?

    /** The artifacts of the workflow run [runId]. */
    suspend fun runArtifacts(repo: String, runId: Long): List<ArtifactDto>

    /** The sha of the newest commit of [branch]. */
    suspend fun latestCommit(repo: String, branch: String): String

    /** How many commits [head] has that [base] lacks. */
    suspend fun commitsAhead(repo: String, base: String, head: String): Int

    /** Starts the main build workflow on main. Needs a token that may run workflows. */
    suspend fun startMainBuild(repo: String)

    /** The short-lived link to the zip of an artifact. Needs a token that may read Actions. */
    suspend fun artifactDownloadUrl(repo: String, artifactId: Long): String

    /** Checks [token] against GitHub and returns its allowance. Throws [InvalidTokenException] if rejected. */
    suspend fun checkToken(token: String): RateLimitInfo
}

/** Ktor over OkHttp: [client] should be built on an OkHttp engine with the HTTP cache. */
class KtorGithubApi(
    private val client: HttpClient,
    private val owner: String,
    private val tokenStore: TokenStore,
) : GithubApi {

    /** Every open PR in [owner]'s repos, newest activity first. One search call. */
    override suspend fun openPullRequests(): List<IssueDto> {
        val url = "https://api.github.com/search/issues?q=is:pr+is:open+archived:false+user:$owner" +
            "&sort=updated&order=desc&per_page=100"
        // Revalidated every time: GitHub marks responses max-age=60, so the HTTP cache would otherwise
        // serve this list unchecked for a minute, and a refresh would show the previous state
        return get<SearchDto>(url, forceNetwork = true).items
    }

    override suspend fun repositories(): List<String> {
        val names = mutableListOf<String>()
        var page = 1
        while (true) {
            val batch = get<List<RepoDto>>(
                "https://api.github.com/users/$owner/repos?type=owner&sort=updated&per_page=$REPOS_PER_PAGE&page=$page",
            )
            names += batch.filterNot { it.archived }.map { it.fullName }
            if (batch.size < REPOS_PER_PAGE) return names
            page++
        }
    }

    override suspend fun comments(repo: String, number: Int): List<CommentDto> =
        // Revalidated every time: the CI edits its comment in place, and a 304 is free of the rate limit
        get("https://api.github.com/repos/$repo/issues/$number/comments?per_page=100", forceNetwork = true)

    override suspend fun mainRuns(repo: String): List<RunDto>? =
        try {
            // Revalidated every time: a run changes state in place, and a 304 is free of the rate limit
            get<RunsDto>(
                "https://api.github.com/repos/$repo/actions/workflows/$MAIN_BUILD_WORKFLOW/runs" +
                    "?branch=$MAIN_BRANCH&event=workflow_dispatch&per_page=$MAIN_RUNS_PER_PAGE",
                forceNetwork = true,
            ).runs
        } catch (e: NotFoundException) {
            null
        }

    override suspend fun runArtifacts(repo: String, runId: Long): List<ArtifactDto> =
        get<ArtifactsDto>("https://api.github.com/repos/$repo/actions/runs/$runId/artifacts", forceNetwork = true).artifacts

    override suspend fun latestCommit(repo: String, branch: String): String =
        get<CommitDto>("https://api.github.com/repos/$repo/commits/$branch", forceNetwork = true).sha

    override suspend fun commitsAhead(repo: String, base: String, head: String): Int =
        // A comparison of two commits never changes, so the HTTP cache may keep it
        get<CompareDto>("https://api.github.com/repos/$repo/compare/$base...$head").aheadBy

    override suspend fun startMainBuild(repo: String) {
        val response = client.post("https://api.github.com/repos/$repo/actions/workflows/$MAIN_BUILD_WORKFLOW/dispatches") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            tokenStore.token.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(TextContent("""{"ref":"$MAIN_BRANCH"}""", ContentType.Application.Json))
        }
        when (val code = response.status.value) {
            in 200..299 -> Unit
            401 -> throw InvalidTokenException()
            403, 404 -> throw IOException(
                "GitHub refused to start the build. The token needs the Actions permission (read and write) " +
                    "on $repo, and the repo needs $MAIN_BUILD_WORKFLOW on $MAIN_BRANCH.",
            )
            422 -> throw IOException("$MAIN_BUILD_WORKFLOW cannot be started: it needs a workflow_dispatch trigger.")
            else -> throw IOException("GitHub returned $code when starting the build")
        }
    }

    override suspend fun artifactDownloadUrl(repo: String, artifactId: Long): String {
        // Not followed: the redirect goes to another host, which must not get the token
        val response = withoutRedirects.get("https://api.github.com/repos/$repo/actions/artifacts/$artifactId/zip") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            tokenStore.token.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        return when (val code = response.status.value) {
            302 -> response.headers[HttpHeaders.Location] ?: throw IOException("GitHub gave no download link")
            401 -> throw InvalidTokenException()
            403, 404 -> throw IOException("GitHub refused the download: the token needs the Actions permission (read), and the build may have expired.")
            410 -> throw IOException("The build has expired. Build main again.")
            else -> throw IOException("GitHub returned $code for the download")
        }
    }

    override suspend fun checkToken(token: String): RateLimitInfo {
        // Always from the network: this is asking whether the token works right now
        val core = get<RateLimitDto>("https://api.github.com/rate_limit", token = token, forceNetwork = true)
            .resources.core
        return RateLimitInfo(core.limit, core.remaining)
    }

    private val withoutRedirects = client.config { followRedirects = false }

    private companion object {
        const val REPOS_PER_PAGE = 100
        const val MAIN_RUNS_PER_PAGE = 10
    }

    private suspend inline fun <reified T> get(
        url: String,
        token: String? = tokenStore.token.value,
        forceNetwork: Boolean = false,
    ): T {
        val response = client.get(url) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            // Only ever sent to api.github.com, which is the only host this class talks to
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (forceNetwork) header(HttpHeaders.CacheControl, "no-cache")
        }
        val code = response.status.value
        if (code == 401) throw InvalidTokenException()
        if ((code == 403 || code == 429) && response.headers["X-RateLimit-Remaining"] == "0") {
            throw RateLimitException(response.headers["X-RateLimit-Reset"]?.toLongOrNull())
        }
        if (code == 404) throw NotFoundException(url)
        if (!response.status.isSuccess()) throw IOException("GitHub returned $code for $url")
        return response.body()
    }
}

/** The client [KtorGithubApi] needs: OkHttp engine on [okHttp], and JSON bodies decoded to DTOs. */
fun githubHttpClient(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = okHttp }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

@Serializable
data class SearchDto(val items: List<IssueDto> = emptyList())

@Serializable
data class IssueDto(
    val number: Int,
    val title: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("repository_url") val repositoryUrl: String,
    val user: UserDto? = null,
    val draft: Boolean = false,
    val body: String? = null,
    /** Changes on a push or a new comment, but not when a comment is edited. */
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class RepoDto(
    @SerialName("full_name") val fullName: String,
    val archived: Boolean = false,
)

@Serializable
data class UserDto(val login: String)

@Serializable
data class CommentDto(val body: String = "")

@Serializable
data class RateLimitDto(val resources: RateLimitResourcesDto)

@Serializable
data class RateLimitResourcesDto(val core: RateLimitCoreDto)

@Serializable
data class RateLimitCoreDto(val limit: Int, val remaining: Int)

/** GitHub answered 404: the thing asked for is not there, or the token may not see it. */
class NotFoundException(url: String) : IOException("GitHub returned 404 for $url")

@Serializable
data class RunsDto(@SerialName("workflow_runs") val runs: List<RunDto> = emptyList())

@Serializable
data class RunDto(
    val id: Long,
    @SerialName("head_sha") val headSha: String,
    /** `queued`, `in_progress` or `completed`, among others. */
    val status: String = "",
    /** Set once the run is completed: `success`, `failure`, `cancelled` and so on. */
    val conclusion: String? = null,
)

@Serializable
data class ArtifactsDto(val artifacts: List<ArtifactDto> = emptyList())

@Serializable
data class ArtifactDto(val id: Long, val name: String, val expired: Boolean = false)

@Serializable
data class CommitDto(val sha: String)

@Serializable
data class CompareDto(@SerialName("ahead_by") val aheadBy: Int = 0)
