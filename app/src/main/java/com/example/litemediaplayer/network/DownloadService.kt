package com.example.litemediaplayer.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.litemediaplayer.R

class DownloadService : Service() {
    private val downloadManager = NetworkDownloadManager.shared

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("ダウンロード待機中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activeCount = downloadManager.downloads.value.count { task ->
            task.status == NetworkDownloadManager.DownloadStatus.DOWNLOADING
        }
        val content = if (activeCount > 0) {
            "ダウンロード中: ${activeCount}件"
        } else {
            "ダウンロード待機中"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.tab_network))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ネットワークダウンロード",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "network_downloads"
        private const val NOTIFICATION_ID = 2001
    }
}
