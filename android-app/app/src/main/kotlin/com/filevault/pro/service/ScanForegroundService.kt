package com.filevault.pro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.filevault.pro.MainActivity
import com.filevault.pro.R
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.observer.MediaStoreObserver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScanForegroundService : Service() {
    private fun showScanResultNotification(fileCount: Int) {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Scan complete: $fileCount files cataloged at " +
            java.text.SimpleDateFormat("HH:mm, MMM d", java.util.Locale.getDefault()).format(java.util.Date())
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FileVault Pro Scan Results")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var mediaStoreObserver: MediaStoreObserver

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        private const val TAG = "ScanForegroundService"
        const val CHANNEL_ID = "scan_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.filevault.pro.action.START_SCAN"
        const val ACTION_STOP = "com.filevault.pro.action.STOP_SCAN"
        const val ACTION_START_MONITORING = "com.filevault.pro.action.START_MONITORING"

        fun startScan(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startMonitoring(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                mediaStoreObserver.unregister()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_MONITORING -> {
                startForegroundCompat(buildNotification("Monitoring for file changes…"))
                mediaStoreObserver.register()
                Log.d(TAG, "Real-time monitoring started via ContentObserver + FileObserver")
            }
            else -> {
                startForegroundCompat(buildNotification("Scanning your files…"))
                mediaStoreObserver.register()
                performScan()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun performScan() {
        scope.launch {
            Log.d(TAG, "Foreground scan starting")
            try {
                updateNotification("Reading MediaStore…")
                val mediaCount = fileRepository.performMediaStoreScan()

                appPreferences.setLastScanAt(System.currentTimeMillis())
                appPreferences.setLastScanCount(mediaCount)
                appPreferences.setInitialScanDone(true)

                updateNotification("Scan complete: $mediaCount files cataloged")
                showScanResultNotification(mediaCount)
                Log.d(TAG, "Foreground scan complete: $mediaCount files")
            } catch (e: Exception) {
                Log.e(TAG, "Scan error: ${e.message}", e)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ScanForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FileVault Pro")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "File Scan Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while scanning files in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaStoreObserver.unregister()
        scope.cancel()
    }
}
