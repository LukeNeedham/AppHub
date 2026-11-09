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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Claude through the Anthropic Messages API. */
class AnthropicLlmClient(client: HttpClient, private val apiKey: String) : LlmClient {

    private val http = LlmHttp(client, "Anthropic")

    override suspend fun complete(request: LlmRequest): LlmReply {
        val response = http.postJson(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body = requestBody(request),
        )
        return replyOf(response)
    }

    /**
     * The conversation grows by a few messages a turn and every turn sends it all again, so the request marks cache breakpoints with
     * block-level `cache_control`: on the system prompt (which also caches the tools, as they come first in the prefix), on the last
     * block of the conversation (the next turn reads everything up to it back at a fraction of the price) and on the last block of
     * the user message before it (the previous turn's breakpoint, so a read still hits if the prefix shifts by a block).
     */
    internal fun requestBody(request: LlmRequest): JsonObject {
        return buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxOutputTokens)
            put(
                "system",
                buildJsonArray { add(cached(textBlock(request.system))) },
            )
            put("messages", messagesOf(request.messages))
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.inputSchema)
                            },
                        )
                    }
                },
            )
        }
    }

    /**
     * The conversation in the API's shape. The API wants roles to alternate and a tool result to sit in a user message, so
     * the consecutive user-side messages (results, then the next text) are one message.
     */
    private fun messagesOf(messages: List<LlmMessage>): JsonArray {
        val out = mutableListOf<Pair<String, MutableList<JsonObject>>>()
        fun append(role: String, block: JsonObject) {
            if (out.isNotEmpty() && out.last().first == role) out.last().second += block else out += role to mutableListOf(block)
        }
        for (message in messages) {
            when (message) {
                is LlmMessage.User -> append("user", textBlock(message.text))
                is LlmMessage.ToolResult -> append(
                    "user",
                    buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", message.callId)
                        put("content", message.content)
                        put("is_error", message.isError)
                    },
                )
                is LlmMessage.Assistant -> {
                    message.text?.takeIf { it.isNotBlank() }?.let { append("assistant", textBlock(it)) }
                    message.toolCalls.forEach { call ->
                        append(
                            "assistant",
                            buildJsonObject {
                                put("type", "tool_use")
                                put("id", call.id)
                                put("name", call.name)
                                put("input", call.arguments)
                            },
                        )
                    }
                }
            }
        }
        // The last block of the conversation, and of the user message before the last one, carry the breakpoints
        val breakpoints = buildSet {
            if (out.isNotEmpty()) add(out.lastIndex)
            if (out.size >= 3) add(out.lastIndex - 2)
        }
        return buildJsonArray {
            out.forEachIndexed { index, (role, blocks) ->
                add(
                    buildJsonObject {
                        put("role", role)
                        put(
                            "content",
                            buildJsonArray {
                                blocks.forEachIndexed { blockIndex, block ->
                                    add(if (index in breakpoints && blockIndex == blocks.lastIndex) cached(block) else block)
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    /** [block] with a cache breakpoint on it. */
    private fun cached(block: JsonObject) = JsonObject(block + ("cache_control" to buildJsonObject { put("type", "ephemeral") }))

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    /** `input_tokens` is only the part after the last cache hit or write, so the cache counts are added back to get all the input. */
    internal fun usageOf(response: JsonObject): LlmUsage? {
        val usage = response["usage"] as? JsonObject ?: return null
        fun count(name: String) = (usage[name] as? JsonPrimitive)?.longOrNull ?: 0L
        val cached = count("cache_read_input_tokens")
        return LlmUsage(
            inputTokens = count("input_tokens") + cached + count("cache_creation_input_tokens"),
            cachedInputTokens = cached,
            outputTokens = count("output_tokens"),
        )
    }

    private fun replyOf(response: JsonObject): LlmReply {
        val blocks = (response["content"] as? JsonArray) ?: throw LlmException("Anthropic answered with something unexpected.")
        val text = StringBuilder()
        val calls = mutableListOf<LlmToolCall>()
        for (block in blocks.map { it.jsonObject }) {
            when (block["type"]?.jsonPrimitive?.contentOrNull) {
                "text" -> block["text"]?.jsonPrimitive?.contentOrNull?.let { text.append(it) }
                "tool_use" -> calls += LlmToolCall(
                    id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    arguments = (block["input"] as? JsonObject) ?: JsonObject(emptyMap()),
                )
            }
        }
        return LlmReply(text.toString().ifBlank { null }, calls, usageOf(response))
    }
}
