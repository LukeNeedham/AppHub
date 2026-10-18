package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.RateLimitInfo
import com.lukeneedham.apphub.domain.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
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
        return get<SearchDto>(url).items
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
        get("https://api.github.com/repos/$repo/issues/$number/comments?per_page=100")

    override suspend fun checkToken(token: String): RateLimitInfo {
        // Always from the network: this is asking whether the token works right now
        val core = get<RateLimitDto>("https://api.github.com/rate_limit", token = token, forceNetwork = true)
            .resources.core
        return RateLimitInfo(core.limit, core.remaining)
    }

    private companion object {
        const val REPOS_PER_PAGE = 100
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
    /** Changes when the PR or any of its comments does, including an edited comment. */
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
