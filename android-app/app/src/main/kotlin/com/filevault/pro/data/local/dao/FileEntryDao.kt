package com.filevault.pro.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.filevault.pro.data.local.entity.FileEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntryDao {

    @Upsert
    suspend fun upsert(entity: FileEntryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<FileEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfNotExists(entities: List<FileEntryEntity>)

    @Query("SELECT * FROM file_entries WHERE is_deleted_from_device = 0 ORDER BY last_modified DESC")
    fun getAllFilesFlow(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE file_type = 'PHOTO' AND is_deleted_from_device = 0 ORDER BY last_modified DESC")
    fun getAllPhotosFlow(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE file_type = 'VIDEO' AND is_deleted_from_device = 0 ORDER BY last_modified DESC")
    fun getAllVideosFlow(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE file_type NOT IN ('PHOTO','VIDEO') AND is_deleted_from_device = 0 ORDER BY last_modified DESC")
    fun getAllOtherFilesFlow(): Flow<List<FileEntryEntity>>

    @Query("""
        SELECT * FROM file_entries
        WHERE is_deleted_from_device = 0
        AND (:query = '' OR name LIKE '%' || :query || '%' OR folder_path LIKE '%' || :query || '%')
        AND (:fileType = '' OR file_type = :fileType)
        ORDER BY last_modified DESC
    """)
    fun searchFiles(query: String, fileType: String = ""): Flow<List<FileEntryEntity>>

    @Query("""
        SELECT * FROM file_entries
        WHERE file_type = 'PHOTO' AND is_deleted_from_device = 0
        AND (:query = '' OR name LIKE '%' || :query || '%' OR folder_path LIKE '%' || :query || '%')
        ORDER BY last_modified DESC
    """)
    fun searchPhotos(query: String): Flow<List<FileEntryEntity>>

    @Query("""
        SELECT * FROM file_entries
        WHERE file_type = 'VIDEO' AND is_deleted_from_device = 0
        AND (:query = '' OR name LIKE '%' || :query || '%' OR folder_path LIKE '%' || :query || '%')
        ORDER BY last_modified DESC
    """)
    fun searchVideos(query: String): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE folder_path = :folderPath AND is_deleted_from_device = 0 ORDER BY last_modified DESC")
    fun getFilesByFolder(folderPath: String): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): FileEntryEntity?

    @Query("SELECT DISTINCT folder_path, folder_name FROM file_entries WHERE is_deleted_from_device = 0")
    fun getAllFolders(): Flow<List<FolderRow>>

    @Query("SELECT COUNT(*) FROM file_entries WHERE is_deleted_from_device = 0")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_entries WHERE file_type = 'PHOTO' AND is_deleted_from_device = 0")
    fun getPhotoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_entries WHERE file_type = 'VIDEO' AND is_deleted_from_device = 0")
    fun getVideoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_entries WHERE file_type = 'AUDIO' AND is_deleted_from_device = 0")
    fun getAudioCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_entries WHERE file_type = 'DOCUMENT' AND is_deleted_from_device = 0")
    fun getDocumentCount(): Flow<Int>

    @Query("SELECT SUM(size_bytes) FROM file_entries WHERE is_deleted_from_device = 0")
    fun getTotalSizeBytes(): Flow<Long?>

    @Query("SELECT * FROM file_entries WHERE last_modified > :since AND is_deleted_from_device = 0")
    suspend fun getFilesModifiedSince(since: Long): List<FileEntryEntity>

    @Query("UPDATE file_entries SET is_deleted_from_device = 1 WHERE path = :path")
    suspend fun markDeleted(path: String)

    @Query("UPDATE file_entries SET last_synced_at = :syncedAt WHERE path IN (:paths)")
    suspend fun markSynced(paths: List<String>, syncedAt: Long)

    @Query("UPDATE file_entries SET is_sync_ignored = :ignored WHERE path = :path")
    suspend fun setSyncIgnored(path: String, ignored: Boolean)

    @Query("SELECT * FROM file_entries WHERE file_type = 'PHOTO'")
    fun getAllPhotosFlowAll(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries WHERE file_type = 'VIDEO'")
    fun getAllVideosFlowAll(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries")
    fun getAllFilesFlowAll(): Flow<List<FileEntryEntity>>

    @Query("SELECT path FROM file_entries WHERE is_deleted_from_device = 0")
    suspend fun getAllNonDeletedPaths(): List<String>

    @Query("UPDATE file_entries SET is_deleted_from_device = 1 WHERE path IN (:paths)")
    suspend fun markDeletedBatch(paths: List<String>)

    @Query("UPDATE file_entries SET is_deleted_from_device = 0 WHERE path = :path")
    suspend fun markRestored(path: String)

    @Query("UPDATE file_entries SET content_hash = :hash WHERE path = :path")
    suspend fun updateContentHash(path: String, hash: String)

    @Query("SELECT * FROM file_entries WHERE content_hash IS NOT NULL AND is_deleted_from_device = 0")
    suspend fun getFilesWithHash(): List<FileEntryEntity>

    @Query("""
        SELECT content_hash, COUNT(*) as cnt FROM file_entries
        WHERE content_hash IS NOT NULL AND is_deleted_from_device = 0
        GROUP BY content_hash HAVING cnt > 1
    """)
    suspend fun getDuplicateHashes(): List<HashCount>

    @Query("SELECT * FROM file_entries WHERE content_hash = :hash AND is_deleted_from_device = 0")
    suspend fun getFilesByHash(hash: String): List<FileEntryEntity>

    @Query("SELECT * FROM file_entries WHERE last_synced_at IS NULL AND is_sync_ignored = 0 AND is_deleted_from_device = 0")
    suspend fun getUnsyncedFiles(): List<FileEntryEntity>

    @Query("""
        SELECT * FROM file_entries
        WHERE last_synced_at IS NULL
        AND is_sync_ignored = 0
        AND is_deleted_from_device = 0
        AND file_type IN (:types)
    """)
    suspend fun getUnsyncedFilesByType(types: List<String>): List<FileEntryEntity>

    @Query("DELETE FROM file_entries WHERE path = :path")
    suspend fun deleteByPath(path: String)
}

data class FolderRow(
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "folder_name") val folderName: String
)

data class HashCount(
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val cnt: Int
)
