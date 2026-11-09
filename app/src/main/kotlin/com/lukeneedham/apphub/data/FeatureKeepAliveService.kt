package com.lukeneedham.apphub.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.lukeneedham.apphub.R

/**
 * Does nothing but show a notification while a session runs, which is what stops Android from ending the app (and the sessions with it) as
 * soon as the screen is off. The sessions themselves run in the application scope; [FeatureKeepAlive] starts and stops this.
 */
class FeatureKeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Feature sessions", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle("AppHub is working on a feature")
            .setContentText("It stops by itself when the CI is green.")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val channelId = "feature_sessions"
        const val notificationId = 1
    }
}
