package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.CI_WORKFLOWS_REPO
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MissingPermissionException
import com.lukeneedham.apphub.domain.NEW_APP_REF
import com.lukeneedham.apphub.domain.NEW_APP_WORKFLOW
import com.lukeneedham.apphub.domain.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.IOException

/** The two GitHub calls that creating an app takes: start the workflow, and list its runs. */
interface NewAppApi {
    /** Starts the workflow that creates [name]. GitHub answers 204 with no run: find it in [runs]. */
    suspend fun dispatch(name: String, isPrivate: Boolean)

    /** The latest runs of the workflow that were started by hand or from here, newest first. */
    suspend fun runs(): List<WorkflowRunDto>
}

/** Ktor: [client] is the one of [KtorGithubApi], with JSON bodies. Needs a token with the Actions permission on the common repo. */
class KtorNewAppApi(
    private val client: HttpClient,
    private val tokenStore: TokenStore,
) : NewAppApi {

    override suspend fun dispatch(name: String, isPrivate: Boolean) {
        val response = client.post("$WORKFLOW_URL/dispatches") {
            authorize()
            contentType(ContentType.Application.Json)
            // The inputs of a workflow are strings, whatever their type
            setBody(DispatchDto(NEW_APP_REF, mapOf("name" to name, "private" to isPrivate.toString())))
        }
        throwIfFailed(response)
    }

    override suspend fun runs(): List<WorkflowRunDto> {
        val response = client.get("$WORKFLOW_URL/runs?event=workflow_dispatch&per_page=$RUNS_PER_PAGE") {
            authorize()
            // The state of a run changes under the same URL: never from the HTTP cache
            header(HttpHeaders.CacheControl, "no-cache")
        }
        throwIfFailed(response)
        return response.body<WorkflowRunsDto>().runs
    }

    private fun HttpRequestBuilder.authorize() {
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
        // Only ever sent to api.github.com, which is the only host this class talks to
        tokenStore.token.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private suspend fun throwIfFailed(response: HttpResponse) {
        val code = response.status.value
        if (code == 401) throw InvalidTokenException()
        if (code == 403 || code == 404) throw MissingPermissionException()
        if (!response.status.isSuccess()) throw IOException("GitHub returned $code: ${response.bodyAsText().take(MAX_ERROR_TEXT)}")
    }

    private companion object {
        const val WORKFLOW_URL = "https://api.github.com/repos/$CI_WORKFLOWS_REPO/actions/workflows/$NEW_APP_WORKFLOW"
        const val RUNS_PER_PAGE = 20
        const val MAX_ERROR_TEXT = 300
    }
}

@Serializable
data class DispatchDto(val ref: String, val inputs: Map<String, String>)

@Serializable
data class WorkflowRunsDto(@SerialName("workflow_runs") val runs: List<WorkflowRunDto> = emptyList())

@Serializable
data class WorkflowRunDto(
    val id: Long,
    /** `queued`, `in_progress` or `completed`. */
    val status: String,
    /** `success`, `failure`, `cancelled` and so on once [status] is `completed`; null before. */
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    /** The `run-name` of the workflow. */
    @SerialName("display_title") val displayTitle: String = "",
)
