package com.lukeneedham.apphub.domain

/** Whose public repositories are listed. */
const val GITHUB_OWNER = "LukeNeedham"

/** The host of the links that open a pull request's page in the app. */
const val DEEP_LINK_HOST = "www.lukeneedham.com"

/**
 * The first path segment of those links. The host also serves a personal website, so the links live
 * under a path of their own that nothing on the site links to. Keep it in step with `pathPrefix` in the manifest and with the
 * link in ci-workflows' `android_pr_build.yml`.
 */
const val DEEP_LINK_PREFIX = "apphub"

/** A pull request by repo (`owner/name`) and number, as a link names it. */
data class PullRequestRef(val repo: String, val number: Int) {
    val key: String get() = "$repo#$number"
}

/**
 * `https://www.lukeneedham.com/apphub/pr/<repo name>/<number>`. The repo is part of the link because PR numbers
 * repeat across repos; the owner is left out as AppHub only lists [GITHUB_OWNER]'s.
 */
fun pullRequestDeepLink(repo: String, number: Int) = "https://$DEEP_LINK_HOST/$DEEP_LINK_PREFIX/PR/${repo.substringAfter('/')}/$number"

object DeepLink {

    private val repoName = Regex("""[A-Za-z0-9._-]+""")

    /** The pull request [uri] points to, or null if it is not a pull request link. */
    fun parse(uri: String?): PullRequestRef? {
        if (uri == null || !uri.startsWith("https://")) return null
        val rest = uri.removePrefix("https://")
        val host = rest.substringBefore('/')
        if (!host.equals(DEEP_LINK_HOST, ignoreCase = true)) return null
        val segments = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
            .split('/').filter { it.isNotEmpty() }
        if (segments.size != 4 || segments[0] != DEEP_LINK_PREFIX || segments[1] != "pr") return null
        val name = segments[2].takeIf { repoName.matches(it) } ?: return null
        val number = segments[3].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return PullRequestRef("$GITHUB_OWNER/$name", number)
    }
}
