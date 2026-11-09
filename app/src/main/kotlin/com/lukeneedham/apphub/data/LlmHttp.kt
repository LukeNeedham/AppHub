package com.lukeneedham.apphub.data

import com.lukeneedham.apphub.domain.LlmException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * The one way the clients of a vendor post a JSON body: it retries what a retry can fix (the vendor is busy or unreachable) and turns
 * what it cannot into an [LlmException] with a message to show. [client] has no timeout of its own: a model can think for minutes.
 */
internal class LlmHttp(private val client: HttpClient, private val vendor: String) {

    suspend fun postJson(url: String, headers: Map<String, String>, body: JsonObject): JsonObject {
        var failure: Exception? = null
        for (attempt in 0 until attempts) {
            if (attempt > 0) delay(backoffMillis * (1L shl (attempt - 1)))
            val response = try {
                client.post(url) {
                    headers.forEach { (name, value) -> header(name, value) }
                    setBody(TextContent(body.toString(), ContentType.Application.Json))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                failure = e
                continue
            }
            val code = response.status.value
            val text = response.bodyAsText()
            when {
                code in 200..299 -> return apiJson.parseToJsonElement(text).jsonObject
                code == 401 || code == 403 -> throw LlmException("$vendor rejected the API key. Check it in Settings.")
                // Busy or down: another try may work
                code == 429 || code >= 500 -> failure = LlmException("$vendor is busy or down ($code): ${errorMessage(text)}")
                else -> throw LlmException("$vendor refused the request ($code): ${errorMessage(text)}")
            }
        }
        throw failure ?: LlmException("$vendor did not answer.")
    }

    private fun errorMessage(body: String): String =
        try {
            apiJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: body.take(maxErrorChars)
        } catch (e: Exception) {
            body.take(maxErrorChars)
        }

    private companion object {
        const val attempts = 4
        const val backoffMillis = 2_000L
        const val maxErrorChars = 300
    }
}
