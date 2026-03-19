package com.filevault.pro.domain.repository

import com.filevault.pro.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface FileRepository {
    fun getAllPhotos(sortOrder: SortOrder, filter: FileFilter): Flow<List<FileEntry>>
    fun getAllVideos(sortOrder: SortOrder, filter: FileFilter): Flow<List<FileEntry>>
    fun getAllFiles(sortOrder: SortOrder, filter: FileFilter): Flow<List<FileEntry>>
    fun getStats(): Flow<CatalogStats>
    fun getFolders(): Flow<List<FolderInfo>>
    val isScanRunning: StateFlow<Boolean>
    val scanSavedCount: StateFlow<Int>
    suspend fun upsertFile(file: FileEntry)
    suspend fun upsertFiles(files: List<FileEntry>)
    suspend fun markDeleted(path: String)
    suspend fun markSynced(paths: List<String>, syncedAt: Long)
    suspend fun setSyncIgnored(path: String, ignored: Boolean)
    suspend fun getUnsyncedFiles(types: List<FileType> = emptyList()): List<FileEntry>
    suspend fun getDuplicates(): List<DuplicateGroup>
    suspend fun performMediaStoreScan(): Int
    suspend fun performFileSystemWalk(onProgress: suspend (folder: String, count: Int) -> Unit): Int
}
