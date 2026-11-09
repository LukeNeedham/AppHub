package com.lukeneedham.apphub.data

import android.content.Context
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.TokenStore

/** The API key of each LLM provider, each encrypted with a Keystore key of its own, like the GitHub token. */
class LlmKeyStores(context: Context) {
    private val stores: Map<LlmProvider, TokenStore> = LlmProvider.entries.associateWith { provider ->
        val name = provider.name.lowercase()
        KeystoreTokenStore(context, prefsName = "llm_key_$name", alias = "apphub_llm_key_$name")
    }

    fun of(provider: LlmProvider): TokenStore = stores.getValue(provider)
}
