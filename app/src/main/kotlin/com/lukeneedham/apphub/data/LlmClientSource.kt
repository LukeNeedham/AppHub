package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.ConfiguredLlm
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.LlmSettings
import com.lukeneedham.apphub.domain.LlmSource
import com.lukeneedham.apphub.domain.isReady
import io.ktor.client.HttpClient

/**
 * Builds the [com.lukeneedham.apphub.domain.LlmClient] of the provider chosen in Settings. The only place that knows
 * which class talks to which vendor: a new vendor is one more branch here.
 */
class LlmClientSource(
    private val settings: LlmSettings,
    private val http: HttpClient,
) : LlmSource {

    override fun current(): ConfiguredLlm? {
        val provider = settings.provider.value
        if (!settings.isReady(provider)) return null
        val key = settings.apiKey(provider).value.orEmpty()
        val client = when (provider) {
            LlmProvider.Anthropic -> AnthropicLlmClient(http, key)
            LlmProvider.DeepSeek -> OpenAiCompatibleLlmClient(http, key, "https://api.deepseek.com/chat/completions", provider.label)
            LlmProvider.Custom -> OpenAiCompatibleLlmClient(http, key, completionsUrl(settings.baseUrl(provider).value), provider.label)
        }
        return ConfiguredLlm(client, settings.model(provider).value, provider.label)
    }
}

/** The address to post a chat to, from what is typed: the address of the API (`https://api.z.ai/api/paas/v4`) or already the full one. */
internal fun completionsUrl(typed: String): String {
    val url = typed.trim().trimEnd('/')
    return if (url.endsWith("/chat/completions")) url else "$url/chat/completions"
}
