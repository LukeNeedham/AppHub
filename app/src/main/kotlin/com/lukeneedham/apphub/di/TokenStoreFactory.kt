package com.lukeneedham.apphub.di

import android.content.Context
import com.lukeneedham.apphub.data.KeystoreTokenStore
import com.lukeneedham.apphub.domain.TokenStore

object TokenStoreFactory {
    /** A second token, kept apart from the main one, whose only power is to create repos (docs/create-app.md in ci-workflows). */
    fun repoCreation(context: Context): TokenStore =
        KeystoreTokenStore(context, prefsName = "repo_creation_token", alias = "apphub_repo_creation_token")
}
