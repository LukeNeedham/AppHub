package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.MAIN_APK_ASSET
import com.lukeneedham.apphub.domain.MainApk
import java.time.Instant

/** The commit line of the release notes that says which commit the APK is of (ci-workflows `scripts/main_build.py`). */
private val COMMIT_LINE = Regex("^commit: ([0-9a-f]{40})\\s*$", RegexOption.MULTILINE)

/** An open pull request as the search lists it, with what it needs to be usable. */
internal data class OpenIssue(
    val repo: String,
    val number: Int,
    val title: String,
    val author: String,
    val url: String,
    val isDraft: Boolean,
    val body: String?,
    val updatedAt: String,
) {
    val key: String get() = "$repo#$number"
}

/** A comment of a pull request, with the time it was last edited as epoch milliseconds when GitHub gave one. */
internal data class IssueComment(val body: String, val updatedAtMillis: Long?)

/** Null when the search result cannot be told apart or opened: without a number, a repo or a link it is dropped. */
internal fun IssueDto.toOpenIssue(): OpenIssue? {
    val number = number ?: return null
    val repo = repositoryUrl?.takeIf { "/repos/" in it }?.substringAfter("/repos/") ?: return null
    val url = htmlUrl ?: return null
    return OpenIssue(
        repo = repo,
        number = number,
        title = title.orEmpty(),
        author = user?.login.orEmpty(),
        url = url,
        isDraft = draft == true,
        body = body,
        updatedAt = updatedAt.orEmpty(),
    )
}

internal fun CommentDto.toIssueComment() = IssueComment(
    body = body.orEmpty(),
    updatedAtMillis = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
)

/** Null when the release has no APK or does not say which commit it is of (not a release of the main build). */
internal fun ReleaseDto.toMainApk(): MainApk? {
    val asset = assets.orEmpty().firstOrNull { it.name == MAIN_APK_ASSET } ?: return null
    val downloadUrl = asset.downloadUrl ?: return null
    val commit = COMMIT_LINE.find(body.orEmpty())?.groupValues?.get(1) ?: return null
    val builtAt = asset.createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return MainApk(commit, downloadUrl, builtAt)
}

/** A run of the New App workflow with the fields it is followed by. */
internal data class WorkflowRun(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val displayTitle: String,
)

/** Null when the run has no id, status or link, which makes it impossible to follow. */
internal fun WorkflowRunDto.toWorkflowRun(): WorkflowRun? {
    val id = id ?: return null
    val status = status ?: return null
    val htmlUrl = htmlUrl ?: return null
    return WorkflowRun(id, status, conclusion, htmlUrl, displayTitle.orEmpty())
}
