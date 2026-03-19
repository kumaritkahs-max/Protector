package com.filevault.pro.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.filevault.pro.data.local.dao.ExcludedFolderDao
import com.filevault.pro.data.local.dao.FileEntryDao
import com.filevault.pro.data.local.entity.FileEntryEntity
import com.filevault.pro.data.local.entity.toDomain
import com.filevault.pro.data.local.entity.toEntity
import com.filevault.pro.domain.model.CatalogStats
import com.filevault.pro.domain.model.DuplicateGroup
import com.filevault.pro.domain.model.FileEntry
import com.filevault.pro.domain.model.FileFilter
import com.filevault.pro.domain.model.FileType
import com.filevault.pro.domain.model.FolderInfo
import com.filevault.pro.domain.model.SortField
import com.filevault.pro.domain.model.SortOrder
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileEntryDao: FileEntryDao,
    private val excludedFolderDao: ExcludedFolderDao
) : FileRepository {

    private companion object {
        const val TAG = "FileRepository"
    }

    override fun getAllPhotos(
        sortOrder: SortOrder,
        filter: FileFilter
    ): Flow<List<FileEntry>> =
        if (filter.searchQuery.isBlank()) fileEntryDao.getAllPhotosFlow()
            .map { list -> list.map { it.toDomain() }.applySortAndFilter(sortOrder, filter) }
        else fileEntryDao.searchPhotos(filter.searchQuery)
            .map { list -> list.map { it.toDomain() }.applySortAndFilter(sortOrder, filter) }

    override fun getAllVideos(sortOrder: SortOrder, filter: FileFilter): Flow<List<FileEntry>> =
        if (filter.searchQuery.isBlank()) fileEntryDao.getAllVideosFlow()
            .map { list -> list.map { it.toDomain() }.applySortAndFilter(sortOrder, filter) }
        else fileEntryDao.searchVideos(filter.searchQuery)
            .map { list -> list.map { it.toDomain() }.applySortAndFilter(sortOrder, filter) }

    override fun getAllFiles(sortOrder: SortOrder, filter: FileFilter): Flow<List<FileEntry>> =
        fileEntryDao.searchFiles(filter.searchQuery)
            .map { list -> list.map { it.toDomain() }.applySortAndFilter(sortOrder, filter) }

    override fun getStats(): Flow<CatalogStats> {
        return combine(
            fileEntryDao.getTotalCount(),
            fileEntryDao.getPhotoCount(),
            fileEntryDao.getVideoCount(),
            fileEntryDao.getAudioCount(),
            fileEntryDao.getDocumentCount(),
            fileEntryDao.getTotalSizeBytes()
        ) { values ->
            CatalogStats(
                totalFiles = values[0] as Int,
                totalPhotos = values[1] as Int,
                totalVideos = values[2] as Int,
                totalAudio = values[3] as Int,
                totalDocuments = values[4] as Int,
                totalOther = (values[0] as Int) - (values[1] as Int) - (values[2] as Int) -
                        (values[3] as Int) - (values[4] as Int),
                totalSizeBytes = (values[5] as Long?) ?: 0L,
                lastScanAt = null,
                lastSyncAt = null
            )
        }
    }

    override fun getFolders(): Flow<List<FolderInfo>> =
        fileEntryDao.getAllFolders().map { rows ->
            rows.map { row ->
                FolderInfo(
                    path = row.folderPath,
                    name = row.folderName,
                    fileCount = 0,
                    totalSizeBytes = 0L,
                    lastModified = 0L
                )
            }
        }

    override suspend fun upsertFile(file: FileEntry) = fileEntryDao.upsert(file.toEntity())

    override suspend fun upsertFiles(files: List<FileEntry>) =
        fileEntryDao.upsertAll(files.map { it.toEntity() })

    override suspend fun markDeleted(path: String) = fileEntryDao.markDeleted(path)

    override suspend fun markSynced(paths: List<String>, syncedAt: Long) =
        fileEntryDao.markSynced(paths, syncedAt)

    override suspend fun setSyncIgnored(path: String, ignored: Boolean) =
        fileEntryDao.setSyncIgnored(path, ignored)

    override suspend fun getUnsyncedFiles(types: List<FileType>): List<FileEntry> =
        if (types.isEmpty()) fileEntryDao.getUnsyncedFiles().map { it.toDomain() }
        else fileEntryDao.getUnsyncedFilesByType(types.map { it.name }).map { it.toDomain() }

    override suspend fun getDuplicates(): List<DuplicateGroup> {
        val hashCounts = fileEntryDao.getDuplicateHashes()
        return hashCounts.mapNotNull { hc ->
            val files = fileEntryDao.getFilesByHash(hc.contentHash).map { it.toDomain() }
            if (files.size > 1) DuplicateGroup(hc.contentHash, files.first().sizeBytes, files)
            else null
        }
    }

    override suspend fun performMediaStoreScan(): Int {
        var count = 0
        var skippedCount = 0
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DATA)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.BUCKET_ID)
            add(MediaStore.Files.FileColumns.WIDTH)
            add(MediaStore.Files.FileColumns.HEIGHT)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            "${MediaStore.Files.FileColumns.SIZE} > 0",
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )

        cursor?.use {
            val excluded = excludedFolderDao.getAllPaths().toSet()
            val entities = mutableListOf<FileEntryEntity>()

            while (it.moveToNext()) {
                val rawPath = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))
                } else null
                val displayName = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val path = when {
                    !rawPath.isNullOrBlank() -> rawPath
                    !relativePath.isNullOrBlank() && !displayName.isNullOrBlank() -> {
                        val rel = relativePath.trimStart('/').let { if (it.endsWith("/")) it else "$it/" }
                        "${Environment.getExternalStorageDirectory().absolutePath}/$rel$displayName"
                    }
                    else -> {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id).toString()
                    }
                }
                if (path.isBlank()) {
                    skippedCount++
                    continue
                }

                val parentPath = if (path.startsWith("content://")) null else File(path).parent
                if (parentPath != null && excluded.any { ex -> parentPath.startsWith(ex) }) continue

                val name = displayName ?: File(path).name
                val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                val modified = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)) * 1000L
                val mimeRaw = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: ""
                val mime = mimeRaw.ifBlank {
                    if (path.startsWith("content://")) "" else FileUtils.getMimeType(File(path))
                }
                val bucketName = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME))
                    ?: parentPath?.let { p -> File(p).name } ?: ""
                val width = runCatching { it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)) }.getOrNull()
                val height = runCatching { it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)) }.getOrNull()
                val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000L

                val fileType = if (mime.isNotBlank()) FileType.fromMimeType(mime)
                              else if (!path.startsWith("content://")) FileType.fromExtension(File(path).extension)
                              else FileType.OTHER

                val resolvedParentPath = parentPath ?: ""

                entities.add(
                    FileEntryEntity(
                        path = path,
                        name = name,
                        folderPath = resolvedParentPath,
                        folderName = bucketName,
                        sizeBytes = size,
                        lastModified = modified,
                        mimeType = mime,
                        fileType = fileType.name,
                        width = if ((width ?: 0) > 0) width else null,
                        height = if ((height ?: 0) > 0) height else null,
                        durationMs = null,
                        orientation = null,
                        cameraMake = null,
                        cameraModel = null,
                        hasGps = false,
                        dateTaken = null,
                        dateAdded = dateAdded,
                        isHidden = if (path.startsWith("content://")) false else FileUtils.isHidden(File(path)),
                        contentHash = null,
                        thumbnailCachePath = null,
                        isSyncIgnored = false,
                        lastSyncedAt = null,
                        isDeletedFromDevice = false
                    )
                )

                if (entities.size >= 500) {
                    fileEntryDao.upsertAll(entities.toList())
                    count += entities.size
                    entities.clear()
                }
            }

            if (entities.isNotEmpty()) {
                fileEntryDao.upsertAll(entities)
                count += entities.size
            }

            if (skippedCount > 0) {
                Log.d(TAG, "Skipped $skippedCount MediaStore entries because the file path could not be resolved")
            }
        }

        return count
    }

    override suspend fun performFileSystemWalk(
        onProgress: suspend (folder: String, count: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val excluded = excludedFolderDao.getAllPaths().toSet()
        val roots = FileUtils.getExternalStorageRoots()

        for (root in roots) {
            val buffer = mutableListOf<FileEntryEntity>()

            root.walkTopDown()
                .onEnter { dir ->
                    excluded.none { ex -> dir.absolutePath.startsWith(ex) } &&
                            !dir.name.startsWith(".thumbnails") &&
                            !dir.name.startsWith("thumbnails") &&
                            dir.canRead()
                }
                .filter { it.isFile && it.length() > 0 }
                .forEach { file ->
                    if (excluded.any { ex -> file.absolutePath.startsWith(ex) }) return@forEach
                    val mime = FileUtils.getMimeType(file)
                    val fileType = FileType.fromExtension(file.extension)
                    buffer.add(
                        FileEntryEntity(
                            path = file.absolutePath,
                            name = file.name,
                            folderPath = file.parent ?: "",
                            folderName = file.parentFile?.name ?: "",
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            mimeType = mime,
                            fileType = fileType.name,
                            width = null, height = null, durationMs = null, orientation = null,
                            cameraMake = null, cameraModel = null, hasGps = false, dateTaken = null,
                            dateAdded = System.currentTimeMillis(),
                            isHidden = FileUtils.isHidden(file),
                            contentHash = null, thumbnailCachePath = null,
                            isSyncIgnored = false, lastSyncedAt = null,
                            isDeletedFromDevice = false
                        )
                    )
                    if (buffer.size >= 500) {
                        fileEntryDao.upsertAll(buffer.toList())
                        count += buffer.size
                        onProgress(file.parent ?: "", count)
                        buffer.clear()
                    }
                }

            if (buffer.isNotEmpty()) {
                fileEntryDao.upsertAll(buffer.toList())
                count += buffer.size
                onProgress("", count)
                buffer.clear()
            }
        }
        count
    }

    private fun List<FileEntry>.applySortAndFilter(sort: SortOrder, filter: FileFilter): List<FileEntry> {
        var result = this
        if (filter.fileTypes.isNotEmpty()) result = result.filter { it.fileType in filter.fileTypes }
        if (filter.folderPaths.isNotEmpty()) result = result.filter { it.folderPath in filter.folderPaths }
        if (filter.dateFrom != null) result = result.filter { it.lastModified >= filter.dateFrom }
        if (filter.dateTo != null) result = result.filter { it.lastModified <= filter.dateTo }
        if (!filter.showHidden) result = result.filter { !it.isHidden }
        if (!filter.showDeleted) result = result.filter { !it.isDeletedFromDevice }
        if (filter.hasGpsOnly) result = result.filter { it.hasGps }
        if (filter.cameraMake != null) result = result.filter {
            it.cameraMake?.contains(filter.cameraMake, ignoreCase = true) == true
        }
        if (filter.minSizeBytes != null) result = result.filter { it.sizeBytes >= filter.minSizeBytes }
        if (filter.maxSizeBytes != null) result = result.filter { it.sizeBytes <= filter.maxSizeBytes }

        val sorted = when (sort.field) {
            SortField.DATE_MODIFIED -> result.sortedBy { it.lastModified }
            SortField.DATE_ADDED -> result.sortedBy { it.dateAdded }
            SortField.NAME -> result.sortedBy { it.name.lowercase() }
            SortField.SIZE -> result.sortedBy { it.sizeBytes }
            SortField.FOLDER -> result.sortedBy { it.folderName.lowercase() }
            SortField.DATE_TAKEN -> result.sortedBy { it.dateTaken ?: it.lastModified }
            SortField.DURATION -> result.sortedBy { it.durationMs ?: 0L }
        }
        return if (sort.ascending) sorted else sorted.reversed()
    }
}
