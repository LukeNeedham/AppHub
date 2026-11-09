package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.LlmClient
import com.lukeneedham.apphub.domain.LlmException
import com.lukeneedham.apphub.domain.LlmMessage
import com.lukeneedham.apphub.domain.LlmReply
import com.lukeneedham.apphub.domain.LlmRequest
import com.lukeneedham.apphub.domain.LlmToolCall
import com.lukeneedham.apphub.domain.LlmUsage
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Any vendor that speaks the chat completions API of OpenAI, which many do: DeepSeek today. [url] is the full address of its
 * `chat/completions`, [vendor] the name in the messages shown to the user.
 */
class OpenAiCompatibleLlmClient(
    client: HttpClient,
    private val apiKey: String,
    private val url: String,
    vendor: String,
) : LlmClient {

    private val http = LlmHttp(client, vendor)
    private val vendorName = vendor

    override suspend fun complete(request: LlmRequest): LlmReply {
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxOutputTokens)
            put("messages", messagesOf(request))
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("parameters", tool.inputSchema)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        val response = http.postJson(url, mapOf("Authorization" to "Bearer $apiKey"), body)
        return replyOf(response)
    }

    private fun messagesOf(request: LlmRequest): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("role", "system")
                put("content", request.system)
            },
        )
        for (message in request.messages) {
            add(
                when (message) {
                    is LlmMessage.User -> buildJsonObject {
                        put("role", "user")
                        put("content", message.text)
                    }
                    is LlmMessage.ToolResult -> buildJsonObject {
                        put("role", "tool")
                        put("tool_call_id", message.callId)
                        put("content", message.content)
                    }
                    is LlmMessage.Assistant -> buildJsonObject {
                        put("role", "assistant")
                        put("content", message.text?.let { JsonPrimitive(it) } ?: JsonNull)
                        if (message.toolCalls.isNotEmpty()) {
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    message.toolCalls.forEach { call ->
                                        add(
                                            buildJsonObject {
                                                put("id", call.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject {
                                                        put("name", call.name)
                                                        // This API wants the arguments as a string of JSON
                                                        put("arguments", call.arguments.toString())
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    private fun replyOf(response: JsonObject): LlmReply {
        val message = (response["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message") as? JsonObject
            ?: throw LlmException("$vendorName answered with something unexpected.")
        val calls = (message["tool_calls"] as? JsonArray).orEmpty().map { it.jsonObject }.map { call ->
            val function = call["function"] as? JsonObject
            LlmToolCall(
                id = call["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                name = function?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                arguments = parseArguments(function?.get("arguments")?.jsonPrimitive?.contentOrNull),
            )
        }
        return LlmReply((message["content"] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }, calls, usageOf(response))
    }

    /**
     * These vendors cache repeated prefixes by themselves, with nothing to ask for in the request, and report the hits in the usage:
     * DeepSeek as `prompt_cache_hit_tokens`, and OpenAI and Z.ai (GLM) as `prompt_tokens_details.cached_tokens`. A vendor that
     * reports neither leaves them out, and the count stays 0.
     */
    internal fun usageOf(response: JsonObject): LlmUsage? {
        val usage = response["usage"] as? JsonObject ?: return null
        fun count(from: JsonObject, name: String) = (from[name] as? JsonPrimitive)?.longOrNull ?: 0L
        val details = usage["prompt_tokens_details"] as? JsonObject
        return LlmUsage(
            inputTokens = count(usage, "prompt_tokens"),
            cachedInputTokens = maxOf(count(usage, "prompt_cache_hit_tokens"), details?.let { count(it, "cached_tokens") } ?: 0L),
            outputTokens = count(usage, "completion_tokens"),
        )
    }

    /** A model may send arguments that are not valid JSON: the call then has none, and the tool says what is missing. */
    private fun parseArguments(text: String?): JsonObject =
        try {
            apiJson.parseToJsonElement(text.orEmpty()).jsonObject
        } catch (e: Exception) {
            JsonObject(emptyMap())
        }
}
