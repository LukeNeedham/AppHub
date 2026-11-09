package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.LlmClient
import com.lukeneedham.apphub.domain.LlmException
import com.lukeneedham.apphub.domain.LlmMessage
import com.lukeneedham.apphub.domain.LlmReply
import com.lukeneedham.apphub.domain.LlmRequest
import com.lukeneedham.apphub.domain.LlmToolCall
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Claude through the Anthropic Messages API. */
class AnthropicLlmClient(client: HttpClient, private val apiKey: String) : LlmClient {

    private val http = LlmHttp(client, "Anthropic")

    override suspend fun complete(request: LlmRequest): LlmReply {
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxOutputTokens)
            put("system", request.system)
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
        val response = http.postJson(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body = body,
        )
        return replyOf(response)
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
        return buildJsonArray {
            out.forEach { (role, blocks) ->
                add(
                    buildJsonObject {
                        put("role", role)
                        put("content", buildJsonArray { blocks.forEach { add(it) } })
                    },
                )
            }
        }
    }

    private fun textBlock(text: String) = buildJsonObject {
        put("type", "text")
        put("text", text)
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
        return LlmReply(text.toString().ifBlank { null }, calls)
    }
}
