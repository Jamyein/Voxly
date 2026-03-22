package com.voxly.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.voxly.data.local.cache.*
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-based music library cache for optimized scanning and instant app startup.
 * 
 * Features:
 * - Fast database queries instead of JSON file parsing
 * - Album art thumbnail caching for instant display
 * - Efficient batch operations for large libraries
 * - Incremental scanning support
 * 
 * Replaces the previous JSON file-based caching for better performance.
 */
@Singleton
class MusicLibraryCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: MusicCacheDatabaseProvider
) {
    companion object {
        private const val TAG = "MusicLibraryCache"
        private const val THUMBNAIL_SIZE = 256  // 256x256 pixels
        private const val THUMBNAIL_QUALITY = 80  // JPEG quality
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
    
    private val gson = Gson()
    private val database: MusicCacheDatabase by lazy { databaseProvider.getDatabase() }
    private val audioFileDao: CachedAudioFileDao by lazy { database.audioFileDao() }
    private val albumThumbnailDao: AlbumThumbnailDao by lazy { database.albumThumbnailDao() }
    
    // ==================== Audio File Cache Operations ====================
    
    /**
     * Gets all cached audio files as a Flow for reactive UI updates.
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>> {
        return audioFileDao.getAllAudioFiles().map { entities ->
            entities.map { it.toAudioFile() }
        }
    }

    /**
     * Gets all cached audio files as a one-shot query.
     */
    suspend fun getCachedAudioFilesOnce(): List<AudioFile> = withContext(Dispatchers.IO) {
        audioFileDao.getAllAudioFilesOnce().map { it.toAudioFile() }
    }
    
    /**
     * Gets cached audio files for a specific directory.
     */
    fun getCachedAudioFilesByDirectory(directoryPath: String): Flow<List<AudioFile>> {
        return audioFileDao.getAudioFilesByDirectory(directoryPath).map { entities ->
            entities.map { it.toAudioFile() }
        }
    }
    
    /**
     * Gets a single cached audio file by path.
     */
    suspend fun getCachedFile(filePath: String): AudioFile? = withContext(Dispatchers.IO) {
        audioFileDao.getAudioFileByPath(filePath)?.toAudioFile()
    }
    
    /**
     * Gets the count of cached files.
     */
    suspend fun getCachedFileCount(): Int = withContext(Dispatchers.IO) {
        audioFileDao.getCachedFileCount()
    }
    
    /**
     * Gets the last scan timestamp.
     */
    suspend fun getLastScanTime(): Long? = withContext(Dispatchers.IO) {
        audioFileDao.getLastScanTime()
    }
    
    /**
     * Checks if cache has any data.
     */
    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) {
        audioFileDao.hasCache()
    }
    
    /**
     * Updates the cache with new audio files.
     * Uses efficient batch insert for large libraries.
     */
    suspend fun updateCache(audioFiles: List<AudioFile>) = withContext(Dispatchers.IO) {
        val entities = audioFiles.map { audioFile ->
            val file = File(audioFile.path)
            val customFieldsJson = if (audioFile.metadata.customFields.isNotEmpty()) {
                gson.toJson(audioFile.metadata.customFields)
            } else null
            
            CachedAudioFileEntity.fromAudioFile(
                audioFile = audioFile,
                fileLastModified = file.lastModified(),
                customFieldsJson = customFieldsJson
            )
        }
        
        // Use chunked insert for large libraries
        audioFileDao.insertAllChunked(entities)
        
        Timber.d(TAG, "Cached ${entities.size} audio files")
    }
    
    /**
     * Syncs a single file to cache (e.g., after metadata edit).
     */
    suspend fun syncFileToCache(audioFile: AudioFile) = withContext(Dispatchers.IO) {
        val file = File(audioFile.path)
        val customFieldsJson = if (audioFile.metadata.customFields.isNotEmpty()) {
            gson.toJson(audioFile.metadata.customFields)
        } else null
        
        val entity = CachedAudioFileEntity.fromAudioFile(
            audioFile = audioFile,
            fileLastModified = file.lastModified(),
            customFieldsJson = customFieldsJson
        )
        
        audioFileDao.insert(entity)
    }
    
    /**
     * Removes a file from cache.
     */
    suspend fun removeFromCache(filePath: String) = withContext(Dispatchers.IO) {
        audioFileDao.deleteByPath(filePath)
    }

    /**
     * Removes multiple files from cache.
     */
    suspend fun removeFromCache(filePaths: List<String>) = withContext(Dispatchers.IO) {
        if (filePaths.isNotEmpty()) {
            audioFileDao.deleteByPaths(filePaths)
        }
    }
    
    /**
     * Clears the entire cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        audioFileDao.deleteAll()
        albumThumbnailDao.deleteAll()
        Timber.d(TAG, "Cache cleared")
    }
    
    // ==================== Incremental Scan Support ====================
    
    /**
     * Gets files that need rescanning based on modification times.
     */
    suspend fun getFilesNeedingRescan(currentFiles: List<Pair<String, Long>>): List<String> = 
        withContext(Dispatchers.IO) {
            val cachedFiles = audioFileDao.getFilePathsWithModificationTimes()
            val cachedMap = cachedFiles.associate { it.path to it.fileLastModifiedAt }
            
            currentFiles.filter { (path, lastModified) ->
                val cached = cachedMap[path]
                cached == null || cached != lastModified
            }.map { it.first }
        }
    
    /**
     * Checks if a specific file needs rescanning.
     */
    suspend fun needsRescan(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            audioFileDao.deleteByPath(filePath)
            return@withContext true
        }
        
        val cached = audioFileDao.getAudioFileByPath(filePath)
        cached == null || cached.fileLastModifiedAt != file.lastModified()
    }
    
    /**
     * Cleans up deleted files from cache.
     */
    suspend fun cleanupDeletedFiles(currentPaths: List<String>): Int = withContext(Dispatchers.IO) {
        audioFileDao.deleteNotInPaths(currentPaths)
    }
    
    // ==================== Album Thumbnail Cache Operations ====================
    
    /**
     * Gets cached album thumbnail bytes.
     */
    suspend fun getAlbumThumbnail(albumId: Long): ByteArray? = withContext(Dispatchers.IO) {
        albumThumbnailDao.getThumbnailByAlbumId(albumId)?.thumbnailBytes
    }
    
    /**
     * Gets multiple cached album thumbnails.
     */
    suspend fun getAlbumThumbnails(albumIds: List<Long>): Map<Long, ByteArray> = 
        withContext(Dispatchers.IO) {
            albumThumbnailDao.getThumbnailsByAlbumIds(albumIds)
                .associate { it.albumId to it.thumbnailBytes }
        }
    
    /**
     * Checks if album has cached thumbnail.
     */
    suspend fun hasAlbumThumbnail(albumId: Long): Boolean = withContext(Dispatchers.IO) {
        albumThumbnailDao.hasThumbnail(albumId)
    }
    
    /**
     * Caches album thumbnail from bitmap.
     */
    suspend fun cacheAlbumThumbnail(
        albumId: Long,
        bitmap: Bitmap,
        sourceUri: String? = null
    ) = withContext(Dispatchers.IO) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        
        val entity = AlbumThumbnailEntity(
            albumId = albumId,
            thumbnailBytes = bytes,
            width = bitmap.width,
            height = bitmap.height,
            sourceUri = sourceUri
        )
        
        albumThumbnailDao.insert(entity)
        Timber.d(TAG, "Cached thumbnail for album $albumId (${bytes.size} bytes)")
    }
    
    /**
     * Caches album thumbnail from bytes (e.g., from MediaStore or embedded).
     */
    suspend fun cacheAlbumThumbnailFromBytes(
        albumId: Long,
        bytes: ByteArray,
        sourceUri: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
            
            val scaledBitmap = if (bitmap.width > THUMBNAIL_SIZE || bitmap.height > THUMBNAIL_SIZE) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (width, height) = if (ratio > 1) {
                    THUMBNAIL_SIZE to (THUMBNAIL_SIZE / ratio).toInt()
                } else {
                    (THUMBNAIL_SIZE * ratio).toInt() to THUMBNAIL_SIZE
                }
                Bitmap.createScaledBitmap(bitmap, width, height, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else bitmap
            
            cacheAlbumThumbnail(albumId, scaledBitmap, sourceUri)
            
            if (scaledBitmap != bitmap) scaledBitmap.recycle()
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to cache thumbnail for album $albumId", e)
        }
    }
    
    /**
     * Removes cached thumbnail for an album.
     */
    suspend fun removeAlbumThumbnail(albumId: Long) = withContext(Dispatchers.IO) {
        albumThumbnailDao.deleteByAlbumId(albumId)
    }
    
    /**
     * Gets the album art URI for a given album ID.
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
    }
}
