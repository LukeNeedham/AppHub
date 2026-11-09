package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/**
 * An LLM vendor AppHub can talk to. To add one, add an entry here and an [LlmClient] for it in `data/`, and wire it in
 * `LlmClientSource`: nothing else in the app knows which vendor is in use.
 */
enum class LlmProvider(
    val label: String,
    val defaultModel: String,
    /** Model names offered as shortcuts in Settings; any other name can be typed. */
    val suggestedModels: List<String>,
    val keyHelp: String,
) {
    Anthropic(
        label = "Anthropic (Claude)",
        defaultModel = "claude-sonnet-5-5",
        suggestedModels = listOf("claude-fable-5-1", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        keyHelp = "Make a key in the Anthropic Console (console.anthropic.com), under API keys.",
    ),
    DeepSeek(
        label = "DeepSeek",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf("deepseek-chat"),
        keyHelp = "Make a key on the DeepSeek platform (platform.deepseek.com), under API keys.",
    ),
}

/** One message of a conversation with a model, in a shape that no vendor owns. */
sealed interface LlmMessage {
    data class User(val text: String) : LlmMessage

    data class Assistant(val text: String?, val toolCalls: List<LlmToolCall> = emptyList()) : LlmMessage

    /** The answer to the call [callId] of an [Assistant] message. */
    data class ToolResult(val callId: String, val content: String, val isError: Boolean = false) : LlmMessage
}

/** A tool the model may call. [inputSchema] is a JSON Schema object. */
data class LlmTool(val name: String, val description: String, val inputSchema: JsonObject)

data class LlmToolCall(val id: String, val name: String, val arguments: JsonObject)

data class LlmRequest(
    val model: String,
    val system: String,
    val messages: List<LlmMessage>,
    val tools: List<LlmTool>,
    val maxOutputTokens: Int = 8_192,
)

/** What the model said: [text] and/or [toolCalls]. */
data class LlmReply(val text: String?, val toolCalls: List<LlmToolCall>)

/**
 * The interface over the LLM. The feature automation only ever sees this, so a different vendor is a different
 * implementation. A call is one model turn; the loop around it belongs to the caller.
 * Throws [LlmException] when the vendor refuses, and an `IOException` when it cannot be reached.
 */
interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmReply
}

/** A client and the model to use with it, as chosen in Settings. */
data class ConfiguredLlm(val client: LlmClient, val model: String, val providerLabel: String)

/** Where the configured LLM comes from. */
interface LlmSource {
    /** Null when the chosen provider has no API key yet. */
    fun current(): ConfiguredLlm?
}

/** The chosen provider and the model of each provider, and the API key of each provider. */
interface LlmSettings {
    val provider: StateFlow<LlmProvider>

    fun selectProvider(provider: LlmProvider)

    /** The model to use with [provider]: the one typed in Settings, or its [LlmProvider.defaultModel]. */
    fun model(provider: LlmProvider): StateFlow<String>

    fun setModel(provider: LlmProvider, model: String)

    /** Null while there is no key for [provider]. */
    fun apiKey(provider: LlmProvider): StateFlow<String?>

    fun setApiKey(provider: LlmProvider, key: String?)
}

/** The vendor answered with an error that retrying will not fix; [message] is fit to show. */
class LlmException(message: String) : Exception(message)
