/*
 * Copyright (c) 2026 Mohammed Ali Baaqail
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.veracrypt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileListActivity

internal class VeraCryptService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateNotification()
        
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UNMOUNT_ALL -> {
                unmountAll()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun unmountAll() {
        VeraCryptFileSystemProvider.unmountAll()
    }

    private fun updateNotification() {
        val channelId = "veracrypt_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VeraCrypt Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, FileListActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val unmountIntent = Intent(this, VeraCryptService::class.java).apply {
            action = ACTION_UNMOUNT_ALL
        }
        val unmountPendingIntent = PendingIntent.getService(this, 0, unmountIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.lock_icon_white_24dp)
            .setContentTitle("VeraCrypt Protection")
            .setContentText("Keeping containers open in background.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.close_icon_white_24dp, "Close All Containers", unmountPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP_SERVICE = "me.zhanghai.android.files.action.STOP_VERACRYPT_SERVICE"
        private const val ACTION_UNMOUNT_ALL = "me.zhanghai.android.files.action.UNMOUNT_ALL_VERACRYPT"

        fun start(context: Context) {
            val intent = Intent(context, VeraCryptService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VeraCryptService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
