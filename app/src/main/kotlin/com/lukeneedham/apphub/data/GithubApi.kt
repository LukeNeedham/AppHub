package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MAIN_RELEASE_TAG
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
import java.util.Base64

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

    /** The `owner/name` of every app listed in `apps.json` of the common repo, lower-cased. */
    suspend fun commonApps(): Set<String>

    /** The first 100 comments of the PR, oldest first. */
    suspend fun comments(repo: String, number: Int): List<CommentDto>

    /** The release `main-build` (the latest main build, replaced by CI on every push to main); null when the repo has none. */
    suspend fun mainRelease(repo: String): ReleaseDto?

    /** The ISO-8601 instant the release tagged [tag] was published; null when there is no such release. */
    suspend fun releasePublishedAt(repo: String, tag: String): String?

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
        return get<SearchDto>(url, forceNetwork = true).items.orEmpty()
    }

    override suspend fun repositories(): List<String> {
        val names = mutableListOf<String>()
        var page = 1
        while (true) {
            val batch = get<List<RepoDto>>(
                "https://api.github.com/users/$owner/repos?type=owner&sort=updated&per_page=$REPOS_PER_PAGE&page=$page",
            )
            names += batch.filter { it.archived != true }.mapNotNull { it.fullName }
            if (batch.size < REPOS_PER_PAGE) return names
            page++
        }
    }

    override suspend fun commonApps(): Set<String> {
        val file = get<ContentDto>("https://api.github.com/repos/$COMMON_REPO/contents/$COMMON_APPS_FILE", forceNetwork = true)
        val json = String(Base64.getMimeDecoder().decode(file.content.orEmpty()))
        return apiJson.decodeFromString<List<CommonAppDto>>(json)
            .mapNotNull { it.repo?.lowercase() }
            .toSet()
    }

    override suspend fun comments(repo: String, number: Int): List<CommentDto> =
        // Revalidated every time: the CI edits its comment in place, and a 304 is free of the rate limit
        get("https://api.github.com/repos/$repo/issues/$number/comments?per_page=100", forceNetwork = true)

    override suspend fun mainRelease(repo: String): ReleaseDto? =
        try {
            // Revalidated every time: CI replaces the asset of this release in place, and a 304 is free of the rate limit
            get<ReleaseDto>("https://api.github.com/repos/$repo/releases/tags/$MAIN_RELEASE_TAG", forceNetwork = true)
        } catch (e: NotFoundException) {
            null
        }

    override suspend fun releasePublishedAt(repo: String, tag: String): String? =
        try {
            // A release does not change once published, so the HTTP cache may keep it
            get<ReleaseDto>("https://api.github.com/repos/$repo/releases/tags/$tag").let { it.publishedAt ?: it.createdAt }
        } catch (e: NotFoundException) {
            null
        }

    override suspend fun checkToken(token: String): RateLimitInfo {
        // Always from the network: this is asking whether the token works right now
        val core = get<RateLimitDto>("https://api.github.com/rate_limit", token = token, forceNetwork = true)
            .resources?.core
        // A token that works but whose allowance is unreadable is not rejected: it shows as unknown, never as a crash
        return RateLimitInfo(core?.limit ?: 0, core?.remaining ?: 0)
    }

    private companion object {
        const val COMMON_REPO = "LukeNeedham/ci-workflows"
        const val COMMON_APPS_FILE = "apps.json"
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
        if (code == 404) throw NotFoundException(url)
        if (!response.status.isSuccess()) throw IOException("GitHub returned $code for $url")
        return response.body()
    }
}

/** The one lenient `Json` of every API call: an unexpected response must never crash the app (docs/networking.md in ci-workflows). */
internal val apiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** The client [KtorGithubApi] needs: OkHttp engine on [okHttp], and JSON bodies decoded to DTOs. */
fun githubHttpClient(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
    engine { preconfigured = okHttp }
    install(ContentNegotiation) {
        json(apiJson)
    }
}

@Serializable
data class SearchDto(val items: List<IssueDto>? = null)

@Serializable
data class IssueDto(
    val number: Int? = null,
    val title: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("repository_url") val repositoryUrl: String? = null,
    val user: UserDto? = null,
    val draft: Boolean? = null,
    val body: String? = null,
    /** Changes on a push or a new comment, but not when a comment is edited. */
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RepoDto(
    @SerialName("full_name") val fullName: String? = null,
    val archived: Boolean? = null,
)

/** A file from the contents API; [content] is base64 with line breaks. */
@Serializable
data class ContentDto(val content: String? = null)

/** One entry of the common repo's `apps.json`. */
@Serializable
data class CommonAppDto(val repo: String? = null)

@Serializable
data class UserDto(val login: String? = null)

@Serializable
data class CommentDto(
    val body: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RateLimitDto(val resources: RateLimitResourcesDto? = null)

@Serializable
data class RateLimitResourcesDto(val core: RateLimitCoreDto? = null)

@Serializable
data class RateLimitCoreDto(val limit: Int? = null, val remaining: Int? = null)

/** GitHub answered 404: the thing asked for is not there, or the token may not see it. */
class NotFoundException(url: String) : IOException("GitHub returned 404 for $url")

@Serializable
data class ReleaseDto(
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val body: String? = null,
    val assets: List<AssetDto>? = null,
)

@Serializable
data class AssetDto(
    val name: String? = null,
    /** A public link: release assets of a public repo need no token. */
    @SerialName("browser_download_url") val downloadUrl: String? = null,
    /** ISO-8601 instant the asset was uploaded, which is when its build finished. */
    @SerialName("created_at") val createdAt: String? = null,
)
