package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.ConfiguredLlm
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.LlmSettings
import com.lukeneedham.apphub.domain.LlmSource
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
        val key = settings.apiKey(provider).value?.takeIf { it.isNotBlank() } ?: return null
        val client = when (provider) {
            LlmProvider.Anthropic -> AnthropicLlmClient(http, key)
            LlmProvider.DeepSeek -> OpenAiCompatibleLlmClient(http, key, "https://api.deepseek.com/chat/completions", provider.label)
        }
        return ConfiguredLlm(client, settings.model(provider).value, provider.label)
    }
}
