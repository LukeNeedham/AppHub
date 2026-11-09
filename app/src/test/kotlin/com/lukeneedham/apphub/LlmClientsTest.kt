package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.AnthropicLlmClient
import com.lukeneedham.apphub.data.OpenAiCompatibleLlmClient
import com.lukeneedham.apphub.domain.AgentTools
import com.lukeneedham.apphub.domain.LlmMessage
import com.lukeneedham.apphub.domain.LlmRequest
import com.lukeneedham.apphub.domain.LlmUsage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmClientsTest {

    private val http = HttpClient(OkHttp)

    @After
    fun tearDown() = http.close()

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    private val request = LlmRequest(
        model = "claude-sonnet-5-5",
        system = "system",
        messages = listOf(
            LlmMessage.User("prompt"),
            LlmMessage.Assistant("thinking", listOf(toolCall("1", "read_file", "path" to "A.kt"))),
            LlmMessage.ToolResult("1", "content"),
            LlmMessage.User("CI failed"),
        ),
        tools = AgentTools.specs,
    )

    @Test
    fun anthropicRequestsCacheThePrefixWithATopLevelBreakpoint() {
        val body = AnthropicLlmClient(http, "key").requestBody(request)

        assertEquals("ephemeral", body["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("claude-sonnet-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(AgentTools.specs.size, body["tools"]!!.jsonArray.size)
    }

    @Test
    fun aToolResultAndTheTextAfterItShareOneUserMessage() {
        val messages: JsonArray = AnthropicLlmClient(http, "key").requestBody(request)["messages"]!!.jsonArray

        assertEquals(listOf("user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val last = messages.last().jsonObject["content"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("tool_result", "text"), last)
    }

    @Test
    fun anthropicInputIncludesTheCacheReadsAndWrites() {
        val usage = AnthropicLlmClient(http, "key").usageOf(
            json("""{"usage":{"input_tokens":50,"cache_creation_input_tokens":200,"cache_read_input_tokens":1000,"output_tokens":30}}"""),
        )

        assertEquals(LlmUsage(inputTokens = 1250, cachedInputTokens = 1000, outputTokens = 30), usage)
    }

    @Test
    fun aReplyWithNoUsageHasNone() {
        assertNull(AnthropicLlmClient(http, "key").usageOf(JsonObject(emptyMap())))
        assertNull(OpenAiCompatibleLlmClient(http, "key", "https://example.com", "Test").usageOf(JsonObject(emptyMap())))
    }

    @Test
    fun deepSeekReportsItsOwnCacheHits() {
        val usage = OpenAiCompatibleLlmClient(http, "key", "https://example.com", "DeepSeek").usageOf(
            json("""{"usage":{"prompt_tokens":900,"prompt_cache_hit_tokens":700,"completion_tokens":40}}"""),
        )

        assertEquals(LlmUsage(inputTokens = 900, cachedInputTokens = 700, outputTokens = 40), usage)
    }
}
