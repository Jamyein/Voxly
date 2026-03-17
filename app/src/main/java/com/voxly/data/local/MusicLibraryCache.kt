package com.voxly.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.voxly.data.local.cache.*
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val metadataProcessor: TagLibMetadataProcessor
) {
    companion object {
        private const val TAG = "MusicLibraryCache"
        private const val THUMBNAIL_SIZE = 128  // 128x128 pixels - reduced for lower memory
        private const val THUMBNAIL_QUALITY = 75  // JPEG quality - slightly reduced for smaller size
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
    
    private val gson = Gson()
    private val database: MusicCacheDatabase by lazy { databaseProvider.getDatabase() }
    private val audioFileDao: CachedAudioFileDao by lazy { database.audioFileDao() }
    private val albumThumbnailDao: AlbumThumbnailDao by lazy { database.albumThumbnailDao() }
    private val albumYearDao: AlbumYearDao by lazy { database.albumYearDao() }
    
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

    // ==================== Album Year Cache Operations ====================

    /**
     * Gets all cached album years as a map for instant access.
     * Key: "${albumName}_${artist}", Value: year
     */
    suspend fun getAllAlbumYears(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            albumYearDao.getAllYears().associate { "${it.albumName}_${it.artist}" to it.year }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to get album years from cache", e)
            emptyMap()
        }
    }

    /**
     * Saves or updates album year in cache.
     */
    suspend fun saveAlbumYear(albumName: String, artist: String?, year: String) = withContext(Dispatchers.IO) {
        try {
            albumYearDao.insertOrUpdate(
                AlbumYearEntity(
                    albumName = albumName,
                    artist = artist,
                    year = year
                )
            )
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to save album year: $albumName", e)
        }
    }

    /**
     * Saves multiple album years at once (batch operation).
     */
    suspend fun saveAlbumYears(albumYears: List<Triple<String, String?, String>>) = withContext(Dispatchers.IO) {
        try {
            val entities = albumYears.map { (albumName, artist, year) ->
                AlbumYearEntity(
                    albumName = albumName,
                    artist = artist,
                    year = year
                )
            }
            albumYearDao.insertOrUpdateAll(entities)
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to save album years batch", e)
        }
    }

    /**
     * Clears all cached album years.
     */
    suspend fun clearAlbumYearCache() = withContext(Dispatchers.IO) {
        try {
            albumYearDao.clearAll()
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to clear album year cache", e)
        }
    }

    // ==================== Batch Album Art Preload ====================

    /**
     * Preloads album art for all unique albums in the background.
     * This should be called after a full scan completes for optimal performance.
     * Only loads one track per album to minimize I/O.
     *
     * @param audioFiles List of scanned audio files
     * @param maxConcurrency Maximum parallel loading (default: CPU cores, max 8)
     */
    suspend fun preloadAlbumArts(audioFiles: List<AudioFile>, maxConcurrency: Int = 8) = coroutineScope {
        // Group by album to get unique albums
        val uniqueAlbums = audioFiles
            .filter { it.metadata.album?.isNotBlank() == true }
            .groupBy { it.metadata.album to it.metadata.artist }
            .values
            .map { it.first() } // One track per album

        Timber.d(TAG, "Preloading album art for ${uniqueAlbums.size} unique albums")

        // Process in parallel batches
        uniqueAlbums.chunked(maxConcurrency).forEach { batch ->
            val deferred = batch.map { audioFile ->
                async(Dispatchers.IO) {
                    try {
                        // Skip if already cached
                        val albumId = audioFile.mediaStoreAlbumId
                        if (albumId != null && albumId > 0 && hasAlbumThumbnail(albumId)) {
                            return@async
                        }

                        // Extract album art from file
                        val artBytes = metadataProcessor.extractAlbumArt(audioFile.path)
                        if (artBytes != null && albumId != null && albumId > 0) {
                            // Cache to Room database
                            cacheAlbumThumbnailFromBytes(albumId, artBytes, sourceUri = audioFile.path)
                            Timber.d(TAG, "Preloaded album art for: ${audioFile.metadata.album}")
                        }
                    } catch (e: Exception) {
                        Timber.w(TAG, "Failed to preload album art: ${audioFile.path}", e)
                    }
                }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Preloads audio properties (sampleRate, channels) for files that don't have them cached.
     * This runs in background after initial scan to avoid blocking the UI.
     *
     * @param audioFiles List of scanned audio files
     * @param maxConcurrency Maximum parallel loading (default: 6)
     */
    suspend fun preloadAudioProperties(audioFiles: List<AudioFile>, maxConcurrency: Int = 6) = coroutineScope {
        // Filter files that need properties loaded (sampleRate or channels is 0)
        val filesNeedingProperties = audioFiles.filter { it.sampleRate == 0 || it.channels == 0 }

        if (filesNeedingProperties.isEmpty()) {
            Timber.d(TAG, "No files need audio properties preload")
            return@coroutineScope
        }

        Timber.d(TAG, "Preloading audio properties for ${filesNeedingProperties.size} files")

        // Process in parallel batches
        filesNeedingProperties.chunked(maxConcurrency).forEach { batch ->
            val deferred = batch.map { audioFile ->
                async(Dispatchers.IO) {
                    try {
                        val audioInfo = metadataProcessor.readAudioInfo(audioFile.path)
                        if (audioInfo != null) {
                            // Update the cached entity with audio properties
                            audioFileDao.updateAudioProperties(
                                path = audioFile.path,
                                sampleRate = audioInfo.sampleRate,
                                channels = audioInfo.channels
                            )
                            Timber.d(TAG, "Preloaded audio props for: ${audioFile.name}")
                        }
                    } catch (e: Exception) {
                        Timber.w(TAG, "Failed to preload audio props: ${audioFile.path}", e)
                    }
                }
            }
            deferred.awaitAll()
        }
    }
}
