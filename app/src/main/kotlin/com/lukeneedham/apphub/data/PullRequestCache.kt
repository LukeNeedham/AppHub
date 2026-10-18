package com.lukeneedham.apphub.data

import android.content.Context
import com.lukeneedham.apphub.domain.PullRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A PR as parsed, with the `updated_at` it was parsed at. */
@Serializable
data class CachedPullRequest(val updatedAt: String, val pr: PullRequest)

/** Keeps the parsed PRs between refreshes and app starts, keyed by `owner/repo#number`, in list order. */
interface PullRequestCache {
    fun load(): Map<String, CachedPullRequest>
    fun save(entries: Map<String, CachedPullRequest>)
}

class PrefsPullRequestCache(context: Context) : PullRequestCache {

    private val prefs = context.getSharedPreferences("pull_requests", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), CachedPullRequest.serializer())

    override fun load(): Map<String, CachedPullRequest> {
        val text = prefs.getString(KEY, null) ?: return emptyMap()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            // Written by an older version of the app: start again
            emptyMap()
        }
    }

    override fun save(entries: Map<String, CachedPullRequest>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, entries)).apply()
    }

    private companion object {
        const val KEY = "entries"
    }
}
