package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.AnthropicLlmClient
import com.lukeneedham.apphub.data.OpenAiCompatibleLlmClient
import com.lukeneedham.apphub.data.completionsUrl
import com.lukeneedham.apphub.domain.AgentTools
import com.lukeneedham.apphub.domain.LlmMessage
import com.lukeneedham.apphub.domain.LlmProvider
import com.lukeneedham.apphub.domain.LlmRequest
import com.lukeneedham.apphub.domain.LlmUsage
import com.lukeneedham.apphub.domain.isReady
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun anthropicRequestsPutCacheBreakpointsOnTheSystemPromptAndTheEndOfTheConversation() {
        val body = AnthropicLlmClient(http, "key").requestBody(request)

        assertNull(body["cache_control"])
        val system = body["system"]!!.jsonArray.single().jsonObject
        assertEquals("system", system["text"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // One breakpoint on the last block of the conversation and one on the user message before the last one
        val marked = body["messages"]!!.jsonArray.map { message ->
            message.jsonObject["content"]!!.jsonArray.count { it.jsonObject["cache_control"] != null }
        }
        assertEquals(listOf(1, 0, 1), marked)
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
    fun openAiStyleInputReadsTheCachedTokensOfTheDetails() {
        val usage = OpenAiCompatibleLlmClient(http, "key", "https://example.com", "Test").usageOf(
            json("""{"usage":{"prompt_tokens":1200,"completion_tokens":25,"prompt_tokens_details":{"cached_tokens":1000}}}"""),
        )

        assertEquals(LlmUsage(inputTokens = 1200, cachedInputTokens = 1000, outputTokens = 25), usage)
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

    @Test
    fun theChatAddressIsMadeFromTheTypedOneOnce() {
        assertEquals("https://api.z.ai/api/paas/v4/chat/completions", completionsUrl("https://api.z.ai/api/paas/v4"))
        assertEquals("https://api.z.ai/api/paas/v4/chat/completions", completionsUrl(" https://api.z.ai/api/paas/v4/ "))
        assertEquals("https://x.test/v1/chat/completions", completionsUrl("https://x.test/v1/chat/completions"))
    }

    @Test
    fun aCustomProviderIsReadyOnlyWithKeyModelAndAddress() {
        val settings = InMemoryLlmSettings()
        val custom = LlmProvider.Custom
        assertFalse(settings.isReady(custom))

        settings.setApiKey(custom, "key")
        settings.setModel(custom, "glm-5")
        assertFalse(settings.isReady(custom))

        settings.setBaseUrl(custom, "https://api.z.ai/api/paas/v4")
        assertTrue(settings.isReady(custom))
    }

    @Test
    fun aBuiltInProviderNeedsOnlyAKey() {
        val settings = InMemoryLlmSettings()
        assertFalse(settings.isReady(LlmProvider.DeepSeek))

        settings.setApiKey(LlmProvider.DeepSeek, "key")

        assertTrue(settings.isReady(LlmProvider.DeepSeek))
    }
}
