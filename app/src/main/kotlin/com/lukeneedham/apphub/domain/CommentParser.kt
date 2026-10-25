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
            body.contains("Build in progress") -> ApkBuild.InProgress(sha, url)
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
