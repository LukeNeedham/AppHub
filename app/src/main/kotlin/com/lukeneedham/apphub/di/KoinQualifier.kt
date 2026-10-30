package com.lukeneedham.apphub.di

/** The names that tell apart bindings of the same type. */
object KoinQualifier {
    /** The [com.lukeneedham.apphub.domain.TokenStore] that holds the token that creates repos. */
    const val REPO_CREATION_TOKEN = "repoCreation"

    /** The directory the APKs are downloaded into. */
    const val APK_DIRECTORY = "apkDirectory"

    /** The scope that outlives every screen. */
    const val APPLICATION_SCOPE = "applicationScope"
}
