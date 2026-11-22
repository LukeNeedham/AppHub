package com.lukeneedham.apphub.domain

/**
 * What a new project is built with, which decides how the workflow `new_app.yml` sets it up (`docs/create-app.md` in
 * ci-workflows). There is no default: the user chooses. [id] is the `stack` input of the workflow, and the `stack` of the
 * project in `apps.json` there, so the ids must match its `choice` options.
 */
enum class NewAppStack(val id: String, val title: String, val description: String) {
    Kmp(
        id = "kmp",
        title = "Kotlin Multiplatform",
        description = "An Android app, which can also build for iOS and the web.",
    ),
    NextJs(
        id = "next-js",
        title = "Next.js",
        description = "A website only, with no Android app. Every pull request gets a preview on Vercel.",
    ),
    ;

    /** Whether the project has an Android package (`com.lukeneedham.<name>`): a website has none. */
    val hasAndroidPackage: Boolean get() = this == Kmp

    /** The description of the GitHub repo of the project [name], as `create_repo` of `scripts/new_app.py` words it. */
    fun repoDescription(name: String): String = when (this) {
        Kmp -> "$name, an Android app"
        NextJs -> "$name, a website"
    }
}
