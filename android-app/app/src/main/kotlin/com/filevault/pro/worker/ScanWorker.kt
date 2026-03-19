package com.filevault.pro.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
    }

    private suspend fun updateProgress(count: Int, stage: String) {
        setProgressAsync(workDataOf(KEY_PROGRESS_COUNT to count, KEY_PROGRESS_STAGE to stage))
    }

    override suspend fun doWork(): Result {
        val isInitial = inputData.getBoolean(KEY_IS_INITIAL, false)
        Log.d(TAG, "ScanWorker starting (initial=$isInitial)")
        return try {
            var totalCount = 0

            updateProgress(0, "Scanning MediaStore…")
            val mediaCount = fileRepository.performMediaStoreScan()
            totalCount += mediaCount
            Log.d(TAG, "MediaStore scan: $mediaCount files")
            updateProgress(mediaCount, "Scanning MediaStore…")

            val fsCount = fileRepository.performFileSystemWalk(suspend { folder, count ->
                if (count % 500 == 0) {
                    Log.v(TAG, "Walking: $folder ($count files so far)")
                    updateProgress(count, "Walking filesystem…")
                }
            })
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
