package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.CI_WORKFLOWS_REPO
import com.lukeneedham.apphub.domain.GITHUB_OWNER
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.MissingPermissionException
import com.lukeneedham.apphub.domain.NEW_APP_REF
import com.lukeneedham.apphub.domain.NEW_APP_WORKFLOW
import com.lukeneedham.apphub.domain.NewAppException
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

/**
 * The GitHub calls that creating an app takes: make the repo, start the workflow that sets it up, and list the runs of
 * that workflow. The repo is made here, with a token of its own, so that no secret of the common repo can create repos.
 */
interface NewAppApi {
    /**
     * Creates the repo [GITHUB_OWNER]/[name] with a README, so that its default branch exists, and returns that branch's name.
     * Throws [NewAppException] when GitHub refuses (a bad token, a missing permission, a name that is taken).
     */
    suspend fun createRepo(name: String, isPrivate: Boolean): String

    /** Renames the branch [from] of the repo [name] to [to]. Throws [NewAppException] when GitHub refuses. */
    suspend fun renameBranch(name: String, from: String, to: String)

    /** Starts the workflow that sets up the repo [name], which exists: with its `resume` input on. GitHub answers 204 with no run: find it in [runs]. */
    suspend fun dispatch(name: String)

    /** The latest runs of the workflow that were started by hand or from here, newest first. */
    suspend fun runs(): List<WorkflowRunDto>
}

/**
 * Ktor: [client] is the one of [KtorGithubApi], with JSON bodies. The workflow calls need [tokenStore]'s token to have the
 * Actions permission on the common repo; the repo calls use [repoCreationTokenStore]'s, which may create repos.
 */
class KtorNewAppApi(
    private val client: HttpClient,
    private val tokenStore: TokenStore,
    private val repoCreationTokenStore: TokenStore,
) : NewAppApi {

    override suspend fun createRepo(name: String, isPrivate: Boolean): String {
        val token = repoCreationTokenStore.token.value ?: throw NewAppException("Add a repo creation token first.")
        val response = client.post("https://api.github.com/user/repos") {
            authorize(token)
            contentType(ContentType.Application.Json)
            setBody(CreateRepoDto(name, isPrivate, autoInit = true, description = "$name, an Android app"))
        }
        when (response.status.value) {
            401 -> throw NewAppException("GitHub rejected the repo creation token. Replace it.")
            403, 404 -> throw NewAppException(
                "The repo creation token may not create repositories. It needs the permission Repository creation " +
                    "(write), or Administration (write), for all repositories, or the scope repo if it is a classic token.",
            )
            422 -> throw NewAppException("A repository named $name exists already, or GitHub does not take the name.")
        }
        throwIfFailed(response)
        return response.body<RepoDto>().defaultBranch
    }

    override suspend fun renameBranch(name: String, from: String, to: String) {
        val token = repoCreationTokenStore.token.value ?: throw NewAppException("Add a repo creation token first.")
        val response = client.post("https://api.github.com/repos/$GITHUB_OWNER/$name/branches/$from/rename") {
            authorize(token)
            contentType(ContentType.Application.Json)
            setBody(RenameBranchDto(to))
        }
        if (!response.status.isSuccess()) {
            throw NewAppException("The repo was created, but its branch $from could not be renamed to $to (GitHub returned ${response.status.value}).")
        }
    }

    override suspend fun dispatch(name: String) {
        val response = client.post("$WORKFLOW_URL/dispatches") {
            authorize(tokenStore.token.value)
            contentType(ContentType.Application.Json)
            // The inputs of a workflow are strings, whatever their type. The repo exists, so the workflow carries on with it
            setBody(DispatchDto(NEW_APP_REF, mapOf("name" to name, "resume" to "true")))
        }
        throwIfFailed(response)
    }

    override suspend fun runs(): List<WorkflowRunDto> {
        val response = client.get("$WORKFLOW_URL/runs?event=workflow_dispatch&per_page=$RUNS_PER_PAGE") {
            authorize(tokenStore.token.value)
            // The state of a run changes under the same URL: never from the HTTP cache
            header(HttpHeaders.CacheControl, "no-cache")
        }
        throwIfFailed(response)
        return response.body<WorkflowRunsDto>().runs
    }

    /** Only ever sent to api.github.com, which is the only host this class talks to. */
    private fun HttpRequestBuilder.authorize(token: String?) {
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
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
data class CreateRepoDto(
    val name: String,
    val private: Boolean,
    @SerialName("auto_init") val autoInit: Boolean,
    val description: String,
)

@Serializable
data class RepoDto(@SerialName("default_branch") val defaultBranch: String = "main")

@Serializable
data class RenameBranchDto(@SerialName("new_name") val newName: String)

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
