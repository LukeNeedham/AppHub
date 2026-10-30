package com.lukeneedham.apphub.di

import android.content.Context
import com.lukeneedham.common.network.NetworkLogInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

/** The one OkHttp client of the app. */
object OkHttpClientFactory {
    private const val CACHE_DIRECTORY = "http"
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024

    fun create(context: Context): OkHttpClient =
        OkHttpClient.Builder()
            // The HTTP cache lets GitHub answer repeat requests with a 304, which is free of the rate limit
            .cache(Cache(File(context.cacheDir, CACHE_DIRECTORY), CACHE_SIZE_BYTES))
            // Every call of the app goes through this client, so the debug kit network page lists them all
            .addInterceptor(NetworkLogInterceptor())
            .build()
}
