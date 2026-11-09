package com.lukeneedham.apphub.domain

/** One change to the files of a repo. */
sealed interface FileChange {
    val path: String

    data class Write(override val path: String, val content: String) : FileChange

    data class Delete(override val path: String) : FileChange
}

data class DirectoryEntry(val name: String, val isDirectory: Boolean)

/** A pull request as the automation needs it. [branch] is the branch it is made from. */
data class PullRequestInfo(val number: Int, val title: String, val url: String, val branch: String, val isOpen: Boolean)

/** How the CI of one commit stands. */
sealed interface CiStatus {
    /** No check has started on the commit yet. */
    data object NotStarted : CiStatus

    data object Running : CiStatus

    data object Passed : CiStatus

    /** [report] is what the failing checks say, with the end of their logs. */
    data class Failed(val report: String) : CiStatus
}

/** The code host (GitHub) as the feature automation uses it. Throws on a network error or when the host refuses. */
interface CodeHost {
    suspend fun defaultBranch(repo: String): String

    /** Null when the repo has no such pull request. */
    suspend fun pullRequest(repo: String, number: Int): PullRequestInfo?

    /** The entries of a directory; empty when [path] is not a directory of [ref] (a branch name). [path] "" is the root. */
    suspend fun listDirectory(repo: String, ref: String, path: String): List<DirectoryEntry>

    /** The text of a file, or null when there is no such file of [ref] or it is not text. */
    suspend fun readFile(repo: String, ref: String, path: String): String?

    /**
     * Makes [branch] from the default branch if it is not there. A session does this before the model starts, so a token that may not write
     * to the repo fails the session at once, not after the model has used its tokens.
     */
    suspend fun prepareBranch(repo: String, branch: String)

    /**
     * Commits [changes] on top of the head of [branch] in one commit and moves [branch] to it, making the branch from the
     * default branch first when it does not exist. Returns the sha of the commit.
     */
    suspend fun commit(repo: String, branch: String, message: String, changes: List<FileChange>): String

    suspend fun openPullRequest(repo: String, branch: String, title: String, body: String): PullRequestInfo

    /** The state of the checks of the commit [sha]. */
    suspend fun ciStatus(repo: String, sha: String): CiStatus
}
