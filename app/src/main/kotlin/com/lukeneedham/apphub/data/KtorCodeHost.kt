package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.CiStatus
import com.lukeneedham.apphub.domain.CodeHost
import com.lukeneedham.apphub.domain.DirectoryEntry
import com.lukeneedham.apphub.domain.FileChange
import com.lukeneedham.apphub.domain.InvalidTokenException
import com.lukeneedham.apphub.domain.PullRequestInfo
import com.lukeneedham.apphub.domain.RateLimitException
import com.lukeneedham.apphub.domain.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * The calls of GitHub that a session makes with the token of Settings, which needs the permissions Contents (read and write),
 * Pull requests (read and write) and Actions (read) on the repo. Commits are made with the Git data API, so a commit with
 * many files is one request chain and one commit. [client] is the one of [KtorGithubApi]. Nothing here is read from the HTTP cache:
 * the branch and the CI change under the same URL.
 */
class KtorCodeHost(
    private val client: HttpClient,
    private val tokenStore: TokenStore,
) : CodeHost {

    /** The same client, that hands a redirect back instead of following it, to download a log without sending the token along. */
    private val noRedirectClient = client.config { followRedirects = false }

    override suspend fun defaultBranch(repo: String): String =
        get<RepoInfoDto>("$api/repos/$repo").defaultBranch ?: "main"

    override suspend fun pullRequest(repo: String, number: Int): PullRequestInfo? {
        val dto = call(HttpMethod.Get, "$api/repos/$repo/pulls/$number", allowNotFound = true)?.body<PullDto>() ?: return null
        return dto.toInfo()
    }

    override suspend fun listDirectory(repo: String, ref: String, path: String): List<DirectoryEntry> {
        val response = call(HttpMethod.Get, contentsUrl(repo, ref, path), allowNotFound = true) ?: return emptyList()
        val element = apiJson.parseToJsonElement(response.bodyAsText())
        if (element !is JsonArray) return emptyList()
        return apiJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(ContentEntryDto.serializer()), element)
            .mapNotNull { entry -> entry.name?.let { DirectoryEntry(it, isDirectory = entry.type == "dir") } }
    }

    override suspend fun readFile(repo: String, ref: String, path: String): String? {
        val response = call(HttpMethod.Get, contentsUrl(repo, ref, path), allowNotFound = true) ?: return null
        val element = apiJson.parseToJsonElement(response.bodyAsText())
        if (element !is JsonObject) return null
        val file = apiJson.decodeFromJsonElement(ContentEntryDto.serializer(), element)
        if (file.type != "file") return null
        return decodeText(Base64.getMimeDecoder().decode(file.content.orEmpty()))
    }

    override suspend fun prepareBranch(repo: String, branch: String) {
        if (branchHead(repo, branch) == null) createBranch(repo, branch)
    }

    override suspend fun commit(repo: String, branch: String, message: String, changes: List<FileChange>): String {
        val headSha = branchHead(repo, branch) ?: createBranch(repo, branch)
        val baseTree = get<GitCommitDto>("$api/repos/$repo/git/commits/$headSha").tree?.sha
            ?: throw IOException("GitHub did not say what the tree of $headSha is.")

        val entries = buildJsonArray {
            for (change in changes) {
                when (change) {
                    is FileChange.Write -> {
                        val blob = post<ShaDto>(
                            "$api/repos/$repo/git/blobs",
                            buildJsonObject {
                                put("content", change.content)
                                put("encoding", "utf-8")
                            },
                        ).sha ?: throw IOException("GitHub did not return the blob of ${change.path}.")
                        add(treeEntry(change.path, blob))
                    }
                    // A null sha removes the file
                    is FileChange.Delete -> add(treeEntry(change.path, null))
                }
            }
        }
        val tree = post<ShaDto>(
            "$api/repos/$repo/git/trees",
            buildJsonObject {
                put("base_tree", baseTree)
                put("tree", entries)
            },
        ).sha ?: throw IOException("GitHub did not return the new tree.")
        val commit = post<ShaDto>(
            "$api/repos/$repo/git/commits",
            buildJsonObject {
                put("message", message)
                put("tree", tree)
                put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(headSha)) })
            },
        ).sha ?: throw IOException("GitHub did not return the new commit.")
        call(
            HttpMethod.Patch,
            "$api/repos/$repo/git/refs/heads/$branch",
            buildJsonObject {
                put("sha", commit)
                put("force", false)
            },
        )
        return commit
    }

    override suspend fun openPullRequest(repo: String, branch: String, title: String, body: String): PullRequestInfo =
        post<PullDto>(
            "$api/repos/$repo/pulls",
            buildJsonObject {
                put("title", title)
                put("head", branch)
                put("base", defaultBranch(repo))
                put("body", body)
            },
        ).toInfo()

    override suspend fun ciStatus(repo: String, sha: String): CiStatus {
        val runs = get<WorkflowRunsListDto>("$api/repos/$repo/actions/runs?head_sha=$sha&per_page=100").runs.orEmpty()
        if (runs.isEmpty()) return CiStatus.NotStarted
        val failed = runs.filter { it.status == "completed" && it.conclusion in failureConclusions }
        if (failed.isNotEmpty()) return CiStatus.Failed(failureReport(repo, failed))
        if (runs.any { it.status != "completed" }) return CiStatus.Running
        return CiStatus.Passed
    }

    /** What the failed runs say: which job and step failed, the compiler errors, and the end of the log. */
    private suspend fun failureReport(repo: String, failedRuns: List<WorkflowRunInfoDto>): String {
        val report = StringBuilder()
        for (run in failedRuns.take(maxReportedRuns)) {
            report.append("Workflow '${run.name}' ${run.conclusion}: ${run.htmlUrl}\n")
            val jobs = try {
                get<JobsDto>("$api/repos/$repo/actions/runs/${run.id}/jobs?filter=latest&per_page=100").jobs.orEmpty()
            } catch (e: IOException) {
                emptyList()
            }
            for (job in jobs.filter { it.conclusion in failureConclusions }.take(maxReportedJobs)) {
                val step = job.steps.orEmpty().firstOrNull { it.conclusion in failureConclusions }?.name
                report.append("\nJob '${job.name}' failed" + (step?.let { " at step '$it'" } ?: "") + ":\n")
                report.append(jobLog(repo, job.id) ?: "(its log could not be downloaded)").append('\n')
            }
        }
        return report.toString().trim().take(maxReportChars)
    }

    private suspend fun jobLog(repo: String, jobId: Long?): String? {
        if (jobId == null) return null
        return try {
            val first = noRedirectClient.request("$api/repos/$repo/actions/jobs/$jobId/logs") { authorize() }
            // The log is stored elsewhere: GitHub answers with a redirect, which is followed without the token
            val text = when (first.status.value) {
                in 300..399 -> {
                    val location = first.headers[HttpHeaders.Location] ?: return null
                    noRedirectClient.request(location) {}.takeIf { it.status.isSuccess() }?.bodyAsText()
                }
                in 200..299 -> first.bodyAsText()
                else -> null
            }
            text?.let(::relevantPartOfLog)
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun branchHead(repo: String, branch: String): String? =
        call(HttpMethod.Get, "$api/repos/$repo/git/ref/heads/$branch", allowNotFound = true)?.body<RefDto>()?.obj?.sha

    private suspend fun createBranch(repo: String, branch: String): String {
        val from = branchHead(repo, defaultBranch(repo)) ?: throw IOException("The default branch of $repo has no commits.")
        post<RefDto>(
            "$api/repos/$repo/git/refs",
            buildJsonObject {
                put("ref", "refs/heads/$branch")
                put("sha", from)
            },
        )
        return from
    }

    private fun treeEntry(path: String, sha: String?): JsonObject = buildJsonObject {
        put("path", path)
        put("mode", "100644")
        put("type", "blob")
        put("sha", sha?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
    }

    private fun contentsUrl(repo: String, ref: String, path: String): String {
        val encodedPath = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { encode(it) }
        return "$api/repos/$repo/contents" + (if (encodedPath.isEmpty()) "" else "/$encodedPath") + "?ref=${encode(ref)}"
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun PullDto.toInfo() = PullRequestInfo(
        number = number ?: 0,
        title = title.orEmpty(),
        url = htmlUrl.orEmpty(),
        branch = head?.ref.orEmpty(),
        isOpen = state == "open",
    )

    private suspend inline fun <reified T> get(url: String): T = call(HttpMethod.Get, url)!!.body()

    private suspend inline fun <reified T> post(url: String, body: JsonElement): T = call(HttpMethod.Post, url, body)!!.body()

    /** Null only for a 404 when [allowNotFound]; every other failure throws. */
    private suspend fun call(
        method: HttpMethod,
        url: String,
        body: JsonElement? = null,
        allowNotFound: Boolean = false,
    ): HttpResponse? {
        val response = client.request(url) {
            this.method = method
            authorize()
            header(HttpHeaders.CacheControl, "no-cache")
            if (body != null) setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
        val code = response.status.value
        if (code == 404 && allowNotFound) return null
        if (code == 401) throw InvalidTokenException()
        if ((code == 403 || code == 429) && response.headers["X-RateLimit-Remaining"] == "0") {
            throw RateLimitException(response.headers["X-RateLimit-Reset"]?.toLongOrNull())
        }
        if (code == 403 || code == 404) {
            throw IOException(
                "GitHub refused ($code) at $url. That needs ${permissionFor(method, url)} on this repo, and the repo must be one " +
                    "the GitHub token of Settings covers. Edit the token on GitHub (Settings → Developer settings → Fine-grained " +
                    "tokens), then paste it in Settings again. A token needs Contents (read and write), Pull requests " +
                    "(read and write) and Actions (read).",
            )
        }
        if (!response.status.isSuccess()) {
            throw IOException("GitHub returned $code at $url: ${response.bodyAsText().take(maxErrorChars)}")
        }
        return response
    }

    /** The permission of a fine-grained token that a call needs, to tell the user which one is missing. */
    private fun permissionFor(method: HttpMethod, url: String): String =
        when {
            "/actions/" in url -> "the permission Actions (read)"
            "/pulls" in url -> if (method == HttpMethod.Get) "the permission Pull requests (read)" else "the permission Pull requests (write)"
            method == HttpMethod.Get -> "the permission Contents (read)"
            else -> "the permission Contents (write)"
        }

    /** Only ever sent to api.github.com, which is the only host this class talks to. */
    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
        tokenStore.token.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private companion object {
        const val api = "https://api.github.com"
        const val maxErrorChars = 300
        const val maxReportedRuns = 2
        const val maxReportedJobs = 2
        const val maxReportChars = 9_000
        val failureConclusions = setOf("failure", "timed_out", "startup_failure", "cancelled")
    }
}

/** The text of [bytes], or null when it is not valid UTF-8 (a binary file). */
internal fun decodeText(bytes: ByteArray): String? =
    try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: java.nio.charset.CharacterCodingException) {
        null
    }

private val logTimestamp = Regex("""^\d{4}-\d\d-\d\dT[\d:.]+Z """)
private val ansiCodes = Regex("""\u001B\[[0-9;]*[A-Za-z]""")

/** The lines of a CI log that matter: the errors, then the end. A whole log is far too long to hand to a model. */
internal fun relevantPartOfLog(log: String, maxErrorLines: Int = 40, tailChars: Int = 3_000): String {
    val lines = log.lines().map { it.replace(logTimestamp, "").replace(ansiCodes, "") }
    val errors = lines.filter { line ->
        line.startsWith("e: ") || line.contains("FAILED") || line.contains("What went wrong") || line.contains("error:") ||
            line.contains("Exception") || line.contains("##[error]")
    }.distinct().take(maxErrorLines)
    val tail = lines.joinToString("\n").takeLast(tailChars)
    return (if (errors.isEmpty()) "" else "Error lines:\n" + errors.joinToString("\n") + "\n\n") + "End of the log:\n" + tail
}

@Serializable
data class RepoInfoDto(@SerialName("default_branch") val defaultBranch: String? = null)

@Serializable
data class PullDto(
    val number: Int? = null,
    val title: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val state: String? = null,
    val head: PullHeadDto? = null,
)

@Serializable
data class PullHeadDto(val ref: String? = null)

/** An entry of a directory listing, or a file with its [content] (base64). */
@Serializable
data class ContentEntryDto(
    val name: String? = null,
    /** `file`, `dir`, `symlink` or `submodule`. */
    val type: String? = null,
    val content: String? = null,
)

@Serializable
data class RefDto(@SerialName("object") val obj: ShaDto? = null)

@Serializable
data class ShaDto(val sha: String? = null)

@Serializable
data class GitCommitDto(val tree: ShaDto? = null)

@Serializable
data class WorkflowRunsListDto(@SerialName("workflow_runs") val runs: List<WorkflowRunInfoDto>? = null)

@Serializable
data class WorkflowRunInfoDto(
    val id: Long? = null,
    val name: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class JobsDto(val jobs: List<JobDto>? = null)

@Serializable
data class JobDto(
    val id: Long? = null,
    val name: String? = null,
    val conclusion: String? = null,
    val steps: List<JobStepDto>? = null,
)

@Serializable
data class JobStepDto(val name: String? = null, val conclusion: String? = null)
