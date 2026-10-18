package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.RateLimitException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Unauthenticated GitHub REST calls, so only public repos are visible. The client should have an
 * HTTP cache: GitHub answers with ETags, and a revalidated `304` does not count against the rate limit.
 */
interface GithubApi {
    /** Every open PR, newest activity first. */
    suspend fun openPullRequests(): List<IssueDto>

    /** The first 100 comments of the PR, oldest first. */
    suspend fun comments(repo: String, number: Int): List<CommentDto>
}

class OkHttpGithubApi(
    private val client: OkHttpClient,
    private val owner: String,
) : GithubApi {
    private val json = Json { ignoreUnknownKeys = true }

    /** Every open PR in [owner]'s repos, newest activity first. One search call. */
    override suspend fun openPullRequests(): List<IssueDto> {
        val url = "https://api.github.com/search/issues?q=is:pr+is:open+archived:false+user:$owner" +
            "&sort=updated&order=desc&per_page=100"
        return json.decodeFromString<SearchDto>(get(url)).items
    }

    override suspend fun comments(repo: String, number: Int): List<CommentDto> =
        json.decodeFromString(get("https://api.github.com/repos/$repo/issues/$number/comments?per_page=100"))

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            if ((response.code == 403 || response.code == 429) &&
                response.header("X-RateLimit-Remaining") == "0"
            ) {
                throw RateLimitException(response.header("X-RateLimit-Reset")?.toLongOrNull())
            }
            if (!response.isSuccessful) throw IOException("GitHub returned ${response.code} for $url")
            response.body?.string() ?: throw IOException("Empty response from $url")
        }
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
data class UserDto(val login: String)

@Serializable
data class CommentDto(val body: String = "")
