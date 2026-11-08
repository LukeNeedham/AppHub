package com.lukeneedham.apphub.domain

/**
 * Reads the comments the CI posts on a pull request. The APK comment format comes from the shared
 * workflow in LukeNeedham/ci-workflows (`android_pr_build.yml`); the web deploy link comes from a
 * separate comment, posted by whatever hosts the preview.
 */
object CommentParser {

    private val apkUrl = Regex("""https://github\.com/[^\s)]+/releases/download/[^\s)]+\.apk""")
    private val commit = Regex("""\*\*Commit:\*\*\s*\[`([0-9a-fA-F]{7,40})`]""")
    private val built = Regex("""\*\*Built:\*\*\s*(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})""")
    private val run = Regex("""\*\*Run:\*\*\s*\[[^\]]*]\((https://github\.com/[^)\s]+/actions/runs/\d+)\)""")
    private val anyUrl = Regex("""https://[^\s)>\]"'<]+""")

    /** Hosts that serve web previews. A URL on one of these in a comment is taken as the deploy link. */
    val previewHosts = listOf(
        "netlify.app",
        "vercel.app",
        "pages.dev",
        "web.app",
        "firebaseapp.com",
        "surge.sh",
        "onrender.com",
        "fly.dev",
        "herokuapp.com",
        "github.io",
    )

    /** Null when the comment is not the APK comment. */
    fun parseApkBuild(body: String, updatedAtMillis: Long? = null): ApkBuild? {
        val sha = commit.find(body)?.groupValues?.get(1) ?: return null
        val url = apkUrl.find(body)?.value
        return when {
            body.contains("Build succeeded") -> url?.let { ApkBuild.Succeeded(it, sha, built.find(body)?.groupValues?.get(1), updatedAtMillis) }
            body.contains("Build in progress") -> ApkBuild.InProgress(sha, url, run.find(body)?.groupValues?.get(1))
            body.contains("Build failed") -> ApkBuild.Failed(sha)
            body.contains("Build cancelled") -> ApkBuild.Cancelled(sha)
            else -> null
        }
    }

    /** The first preview URL in the comment, or null. */
    fun parseWebUrl(body: String): String? =
        anyUrl.findAll(body)
            .map { it.value.trimEnd('.', ',', ';') }
            .firstOrNull { url -> hostOf(url)?.let(::isPreviewHost) == true }

    private const val PREVIEWS_MARKER = "<!-- previews-diff -->"
    private val imgSrc = Regex("""<img\b[^>]*?\bsrc\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val markdownImage = Regex("""!\[[^\]]*]\((https://[^)\s]+)\)""")
    private val rowLabel = Regex("""^`([^`]+)`\s*(?:\(([A-Za-z]+)\))?""")

    /**
     * The changed previews of the "Preview changes" comment of the CI (marked `<!-- previews-diff -->`): one table row
     * each, with the name and kind in the first cell and the image on main and on the PR in the next two.
     * Null when [body] is not that comment; empty when it says nothing changed.
     */
    fun parsePreviewChanges(body: String): List<PreviewChange>? {
        if (!body.contains(PREVIEWS_MARKER)) return null
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .mapNotNull(::parsePreviewRow)
            .toList()
    }

    private fun parsePreviewRow(line: String): PreviewChange? {
        val cells = line.trim().trim('|').split('|').map { it.trim() }
        if (cells.size < 3) return null
        val label = rowLabel.find(cells[0]) ?: return null // the header and the `---` row do not start with a name
        val before = imageOf(cells[1])
        val after = imageOf(cells[2])
        val kind = when (label.groupValues[2].lowercase()) {
            "new" -> PreviewChangeKind.New
            "removed" -> PreviewChangeKind.Removed
            "modified" -> PreviewChangeKind.Modified
            else -> when {
                before == null && after != null -> PreviewChangeKind.New
                after == null && before != null -> PreviewChangeKind.Removed
                else -> PreviewChangeKind.Modified
            }
        }
        if (before == null && after == null) return null
        return PreviewChange(label.groupValues[1], kind, before, after)
    }

    private fun imageOf(cell: String): String? =
        (imgSrc.find(cell) ?: markdownImage.find(cell))?.groupValues?.get(1)

    /** The changes of the newest "Preview changes" comment among [commentBodies] (oldest first); empty if there is none. */
    fun parsePreviewChanges(commentBodies: List<String>): List<PreviewChange> =
        commentBodies.mapNotNull(::parsePreviewChanges).lastOrNull().orEmpty()

    private val sessionUrl = Regex("""https://claude\.ai/code/session_[A-Za-z0-9_-]+""")

    /** The first Claude Code session link in any of [texts] (the PR description, then comments). */
    fun parseSessionUrl(texts: List<String?>): String? =
        texts.firstNotNullOfOrNull { text -> text?.let { sessionUrl.find(it)?.value } }

    /** The newest APK build and web link across a PR's comments, oldest first. */
    fun parse(commentBodies: List<String>): Pair<ApkBuild?, String?> = parse(commentBodies.map { it to null })

    /** As above, each comment with the moment it was last edited (epoch milliseconds), which dates the build. */
    @JvmName("parseDated")
    fun parse(comments: List<Pair<String, Long?>>): Pair<ApkBuild?, String?> {
        var apk: ApkBuild? = null
        var web: String? = null
        for ((body, updatedAt) in comments) {
            val build = parseApkBuild(body, updatedAt)
            if (build != null) {
                apk = build
            } else {
                web = parseWebUrl(body) ?: web
            }
        }
        return apk to web
    }

    private fun hostOf(url: String): String? =
        url.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore(':')
            .lowercase().takeIf { it.isNotEmpty() }

    private fun isPreviewHost(host: String) = previewHosts.any { host == it || host.endsWith(".$it") }
}
