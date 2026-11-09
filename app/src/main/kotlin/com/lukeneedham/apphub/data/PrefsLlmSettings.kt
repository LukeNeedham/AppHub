package com.lukeneedham.apphub.data

import android.content.Context
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.LlmSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The choice of provider and model in preferences; the keys are in [keys]. */
class PrefsLlmSettings(context: Context, private val keys: LlmKeyStores) : LlmSettings {

    private val prefs = context.getSharedPreferences("llm_settings", Context.MODE_PRIVATE)

    private val _provider = MutableStateFlow(
        LlmProvider.entries.firstOrNull { it.name == prefs.getString(keyProvider, null) } ?: LlmProvider.Anthropic,
    )
    override val provider: StateFlow<LlmProvider> = _provider.asStateFlow()

    private val models: Map<LlmProvider, MutableStateFlow<String>> = LlmProvider.entries.associateWith {
        MutableStateFlow(prefs.getString(modelKey(it), null)?.takeIf { model -> model.isNotBlank() } ?: it.defaultModel)
    }

    override fun selectProvider(provider: LlmProvider) {
        prefs.edit().putString(keyProvider, provider.name).apply()
        _provider.value = provider
    }

    override fun model(provider: LlmProvider): StateFlow<String> = models.getValue(provider).asStateFlow()

    override fun setModel(provider: LlmProvider, model: String) {
        val clean = model.trim()
        if (clean.isEmpty()) prefs.edit().remove(modelKey(provider)).apply() else prefs.edit().putString(modelKey(provider), clean).apply()
        models.getValue(provider).value = clean.ifEmpty { provider.defaultModel }
    }

    override fun apiKey(provider: LlmProvider): StateFlow<String?> = keys.of(provider).token

    override fun setApiKey(provider: LlmProvider, key: String?) = keys.of(provider).set(key?.trim()?.takeIf { it.isNotEmpty() })

    private fun modelKey(provider: LlmProvider) = "model_${provider.name}"

    private companion object {
        const val keyProvider = "provider"
    }
}
