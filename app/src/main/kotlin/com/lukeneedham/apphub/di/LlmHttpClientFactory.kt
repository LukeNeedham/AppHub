package com.lukeneedham.apphub.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The client of the LLM calls: the app's OkHttp client (so they are in the network log) with a read timeout long enough for a model to think. */
object LlmHttpClientFactory {
    private const val readTimeoutMinutes = 5L

    fun create(okHttp: OkHttpClient): HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttp.newBuilder().readTimeout(readTimeoutMinutes, TimeUnit.MINUTES).build()
        }
    }
}
