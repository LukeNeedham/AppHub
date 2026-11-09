package com.lukeneedham.apphub.data

import android.content.Context
import com.lukeneedham.apphub.domain.Feature
import com.lukeneedham.apphub.domain.FeatureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Keeps the features as JSON in preferences, newest first, at most [maxKept] (the oldest that are over go first). */
class PrefsFeatureStore(context: Context, private val maxKept: Int = 100) : FeatureStore {

    private val prefs = context.getSharedPreferences("features", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Feature.serializer())
    private val _features = MutableStateFlow(load())

    override val features: StateFlow<List<Feature>> = _features.asStateFlow()

    override fun add(feature: Feature) {
        _features.update { (listOf(feature) + it).take(maxKept) }
        save()
    }

    override fun update(id: String, change: (Feature) -> Feature) {
        _features.update { list -> list.map { if (it.id == id) change(it) else it } }
        save()
    }

    override fun remove(id: String) {
        _features.update { list -> list.filterNot { it.id == id } }
        save()
    }

    private fun load(): List<Feature> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            // Written by another version of the app: start again
            emptyList()
        }
    }

    private fun save() {
        prefs.edit().putString(key, json.encodeToString(serializer, _features.value)).apply()
    }

    private companion object {
        const val key = "list"
    }
}
