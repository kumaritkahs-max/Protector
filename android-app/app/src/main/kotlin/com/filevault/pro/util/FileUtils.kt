package com.filevault.pro.util

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.filevault.pro.domain.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileUtils {

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "md" -> "text/markdown"
                else -> "application/octet-stream"
            }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    fun getExifData(path: String): ExifData? {
        return try {
            val exif = ExifInterface(path)
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val hasGps = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ExifData(
                width = if (width > 0) width else null,
                height = if (height > 0) height else null,
                orientation = orientation,
                cameraMake = make?.trim(),
                cameraModel = model?.trim(),
                hasGps = hasGps,
                dateTimeStr = dateTime
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getVideoMetadata(path: String): VideoMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            VideoMeta(durationMs = duration ?: 0L, width = width, height = height)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun computeMd5(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(1024 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun isHidden(file: File): Boolean {
        var current: File? = file
        while (current != null) {
            if (current.name.startsWith(".")) return true
            current = current.parentFile
        }
        return false
    }

    /**
     * Returns all accessible external storage roots.
     * On Android 10+ uses StorageManager to enumerate all volumes
     * (primary + secondary/SD card + OTG) so nothing is missed.
     * Always falls back to the primary external storage directory.
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it) }
                "content" -> {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val displayName = cursor?.use {
                        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("_display_name")) else null
                    }
                    val fileName = displayName ?: "temp-${System.currentTimeMillis()}"
                    val outFile = File(context.cacheDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    outFile
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getExternalStorageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val volumes = storageManager.storageVolumes
            for (vol in volumes) {
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    vol.directory
                } else {
                    null
                }
                if (dir != null && dir.exists() && dir.canRead()) {
                    roots.add(dir)
                }
            }
        }

        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in externalDirs) {
            if (dir == null) continue
            var root: File = dir
            repeat(4) { root = root.parentFile ?: return@repeat }
            if (root.exists()) roots.add(root)
        }

        val primary = Environment.getExternalStorageDirectory()
        if (primary.exists()) roots.add(primary)

        return roots.toList()
    }

    fun getFileIcon(fileType: FileType, mimeType: String): String {
        return when (fileType) {
            FileType.PHOTO -> "🖼️"
            FileType.VIDEO -> "🎬"
            FileType.AUDIO -> "🎵"
            FileType.DOCUMENT -> when {
                mimeType.contains("pdf") -> "📕"
                mimeType.contains("word") || mimeType.contains("docx") -> "📝"
                mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "📊"
                mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "📋"
                else -> "📄"
            }
            FileType.ARCHIVE -> "🗜️"
            FileType.APK -> "📦"
            FileType.OTHER -> "📁"
        }
    }
}

data class ExifData(
    val width: Int?,
    val height: Int?,
    val orientation: Int,
    val cameraMake: String?,
    val cameraModel: String?,
    val hasGps: Boolean,
    val dateTimeStr: String?
)

data class VideoMeta(
    val durationMs: Long,
    val width: Int?,
    val height: Int?
)
