package com.lukeneedham.apphub.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.lukeneedham.apphub.domain.FeatureManager
import com.lukeneedham.common.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps [FeatureKeepAliveService] running exactly while some session is. */
class FeatureKeepAlive(
    private val context: Context,
    private val manager: FeatureManager,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            manager.features.map { list -> list.any { it.status.isActive } }.distinctUntilChanged().collect { running ->
                val intent = Intent(context, FeatureKeepAliveService::class.java)
                try {
                    if (running) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
                } catch (e: Exception) {
                    // The session still runs while the app is in front; only the protection in the background is missing
                    Logger.w("Could not change the notification of the running sessions", e)
                }
            }
        }
    }
}
