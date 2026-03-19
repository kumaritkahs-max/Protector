package com.filevault.pro.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.filevault.pro.R
import com.filevault.pro.data.preferences.AppPreferences
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.domain.model.AppNotification
import com.filevault.pro.domain.model.NotificationType
import com.filevault.pro.presentation.screen.notifications.NotificationStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject


@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fileRepository: FileRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScanWorker"
        const val KEY_IS_INITIAL = "is_initial_scan"
        const val KEY_PROGRESS_STAGE = "scan_progress_stage"
        const val KEY_PROGRESS_COUNT = "scan_progress_count"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "scan_worker_channel"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo("Preparing scan…")

    private fun buildForegroundInfo(text: String): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "File Scan",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FileVault Pro")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun updateProgress(count: Int, stage: String) {
        setProgress(workDataOf(KEY_PROGRESS_COUNT to count, KEY_PROGRESS_STAGE to stage))
    }

    override suspend fun doWork(): Result {
        val isInitial = inputData.getBoolean(KEY_IS_INITIAL, false)
        Log.d(TAG, "ScanWorker starting (initial=$isInitial)")
        return try {
            setForeground(buildForegroundInfo("Starting scan…"))
            var totalCount = 0

            updateProgress(0, "Scanning MediaStore…")
            val mediaCount = fileRepository.performMediaStoreScan()
            totalCount += mediaCount
            Log.d(TAG, "MediaStore scan: $mediaCount files")
            updateProgress(mediaCount, "MediaStore done, walking filesystem…")

            val fsCount = fileRepository.performFileSystemWalk { folder, count ->
                if (count % 500 == 0) {
                    Log.v(TAG, "Walking: $folder ($count files so far)")
                    updateProgress(count, "Walking filesystem…")
                }
            }
            totalCount += fsCount
            Log.d(TAG, "File system walk: $fsCount files")
            updateProgress(totalCount, "Scan complete")

            appPreferences.setLastScanAt(System.currentTimeMillis())
            if (isInitial) {
                appPreferences.setInitialScanDone(true)
            }

            Log.d(TAG, "Scan complete. Total processed: $totalCount")

            NotificationStore.add(
                AppNotification(
                    type = NotificationType.SCAN,
                    title = "Scan Complete",
                    message = "Cataloged $totalCount files from your device storage"
                )
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed: ${e.message}", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
