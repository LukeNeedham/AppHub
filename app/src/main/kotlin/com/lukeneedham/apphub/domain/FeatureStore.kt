package com.lukeneedham.apphub.domain

import kotlinx.coroutines.flow.StateFlow

/** The features and tweaks, kept between app starts. Newest first. */
interface FeatureStore {
    val features: StateFlow<List<Feature>>

    fun add(feature: Feature)

    /** Changes the feature [id] with [change]; nothing happens if it is gone. */
    fun update(id: String, change: (Feature) -> Feature)

    fun remove(id: String)
}

fun FeatureStore.find(id: String): Feature? = features.value.firstOrNull { it.id == id }
