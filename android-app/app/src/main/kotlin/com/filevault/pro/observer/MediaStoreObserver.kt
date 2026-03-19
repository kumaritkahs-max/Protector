package com.filevault.pro.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.filevault.pro.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileRepository: FileRepository
) {
    companion object {
        private const val TAG = "MediaStoreObserver"
        private const val DEBOUNCE_MS = 30_000L

        private val KEY_DIRECTORIES = listOf(
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Downloads",
            "/storage/emulated/0/WhatsApp/Media",
            "/storage/emulated/0/Telegram",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Music",
            "/storage/emulated/0/Documents"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null
    private var isRegistered = false

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Log.d(TAG, "MediaStore changed: $uri")
            triggerDebouncedScan()
        }
    }

    private val fileObservers = mutableListOf<LegacyFileObserver>()

    fun register() {
        if (isRegistered) return
        isRegistered = true

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                mediaObserver
            )
            Log.d(TAG, "ContentObserver registered on MediaStore URIs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register ContentObserver: ${e.message}")
        }

        registerFileObservers()
    }

    private fun registerFileObservers() {
        KEY_DIRECTORIES.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                try {
                    val observer = LegacyFileObserver(path) {
                        triggerDebouncedScan()
                    }
                    observer.startWatching()
                    fileObservers.add(observer)
                    Log.d(TAG, "FileObserver registered on: $path")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not watch $path: ${e.message}")
                }
            }
        }
    }

    fun unregister() {
        if (!isRegistered) return
        isRegistered = false
        try {
            context.contentResolver.unregisterContentObserver(mediaObserver)
        } catch (_: Exception) {}
        fileObservers.forEach {
            try { it.stopWatching() } catch (_: Exception) {}
        }
        fileObservers.clear()
        Log.d(TAG, "ContentObserver and FileObservers unregistered")
    }

    private fun triggerDebouncedScan() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            Log.d(TAG, "Running incremental scan from observer trigger")
            try {
                fileRepository.performMediaStoreScan()
            } catch (e: Exception) {
                Log.e(TAG, "Observer-triggered scan failed: ${e.message}")
            }
        }
    }
}

class LegacyFileObserver(
    path: String,
    private val onChange: () -> Unit
) : FileObserver(
    File(path),
    CREATE or CLOSE_WRITE or DELETE or MOVED_TO or MOVED_FROM
) {
    override fun onEvent(event: Int, path: String?) {
        onChange()
    }
}
