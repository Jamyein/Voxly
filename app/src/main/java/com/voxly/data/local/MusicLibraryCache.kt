package com.voxly.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.gson.Gson
import com.voxly.data.local.cache.*
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

    private val cacheVersion = MutableStateFlow(0L)
    val cacheVersionFlow: StateFlow<Long> = cacheVersion.asStateFlow()
    
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
     * Gets raw cached entity for accessing metadata timestamps.
     */
    suspend fun getCachedFileEntity(filePath: String): CachedAudioFileEntity? = withContext(Dispatchers.IO) {
        audioFileDao.getAudioFileByPath(filePath)
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
        bumpCacheVersion()
        
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
        bumpCacheVersion()
    }
    
    /**
     * Removes a file from cache.
     */
    suspend fun removeFromCache(filePath: String) = withContext(Dispatchers.IO) {
        audioFileDao.deleteByPath(filePath)
        bumpCacheVersion()
    }

    /**
     * Removes multiple files from cache.
     */
    suspend fun removeFromCache(filePaths: List<String>) = withContext(Dispatchers.IO) {
        if (filePaths.isNotEmpty()) {
            audioFileDao.deleteByPaths(filePaths)
            bumpCacheVersion()
        }
    }
    
    /**
     * Clears the entire cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        audioFileDao.deleteAll()
        albumThumbnailDao.deleteAll()
        bumpCacheVersion()
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
        val deletedCount = audioFileDao.deleteNotInPaths(currentPaths)
        if (deletedCount > 0) {
            bumpCacheVersion()
        }
        deletedCount
    }

    private fun bumpCacheVersion() {
        cacheVersion.update { current -> current + 1 }
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
            val bitmap = decodeThumbnailBitmap(bytes) ?: return@withContext
            cacheAlbumThumbnail(albumId, bitmap, sourceUri)
            bitmap.recycle()
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to cache thumbnail for album $albumId", e)
        }
    }

    private fun decodeThumbnailBitmap(bytes: ByteArray): Bitmap? {
        val source = ImageDecoder.createSource(bytes, 0, bytes.size)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = info.size
            val (targetWidth, targetHeight) = calculateTargetSize(size.width, size.height)
            decoder.setTargetSize(targetWidth, targetHeight)
        }
    }

    private fun calculateTargetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return THUMBNAIL_SIZE to THUMBNAIL_SIZE
        if (width <= THUMBNAIL_SIZE && height <= THUMBNAIL_SIZE) return width to height
        val ratio = width.toFloat() / height.toFloat()
        return if (ratio > 1f) {
            THUMBNAIL_SIZE to (THUMBNAIL_SIZE / ratio).toInt().coerceAtLeast(1)
        } else {
            (THUMBNAIL_SIZE * ratio).toInt().coerceAtLeast(1) to THUMBNAIL_SIZE
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
