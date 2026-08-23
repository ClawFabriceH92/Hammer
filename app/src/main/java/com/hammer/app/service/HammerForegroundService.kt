package com.hammer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hammer.app.R

/**
 * Foreground service (§11/§12): keeps the run alive with the screen off/locked and exposes a
 * one-tap STOP action directly on the notification, without needing to reopen the app.
 */
class HammerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "hammer_run_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.hammer.app.action.STOP"
        const val EXTRA_TARGET_LABEL = "target_label"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): HammerForegroundService = this@HammerForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            StopRequestBus.requestStop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val targetLabel = intent?.getStringExtra(EXTRA_TARGET_LABEL).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(targetLabel, currentRps = 0, elapsedSeconds = 0, totalDurationSeconds = 0))
        return START_NOT_STICKY
    }

    fun updateNotification(targetLabel: String, currentRps: Long, elapsedSeconds: Int, totalDurationSeconds: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(targetLabel, currentRps, elapsedSeconds, totalDurationSeconds))
    }

    private fun buildNotification(
        targetLabel: String,
        currentRps: Long,
        elapsedSeconds: Int,
        totalDurationSeconds: Int
    ): Notification {
        val stopIntent = Intent(this, HammerForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title, targetLabel, currentRps.toString()))
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (totalDurationSeconds > 0) {
            builder.setContentText(getString(R.string.notification_progress, elapsedSeconds, totalDurationSeconds))
            builder.setProgress(totalDurationSeconds, elapsedSeconds, false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
