package com.voxly.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.gson.Gson
import com.voxly.data.local.SettingsDataStore
import timber.log.Timber
import com.voxly.data.local.cache.*
import com.voxly.data.local.cover.CoverDiskCache
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.CacheChange
import com.voxly.domain.model.CacheChangeKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource

@Singleton
class MusicLibraryCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val coverDiskCache: CoverDiskCache,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "MusicLibraryCache"
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
    
    private val gson = Gson()
    private val database: MusicCacheDatabase by lazy { databaseProvider.getDatabase() }
    private val audioFileDao: CachedAudioFileDao by lazy { database.audioFileDao() }
    private val albumThumbnailDao: AlbumThumbnailDao by lazy { database.albumThumbnailDao() }
    private val artistLinkDao: ArtistLinkDao by lazy { database.artistLinkDao() }
    private val enrichmentJobDao: EnrichmentJobDao by lazy { database.enrichmentJobDao() }

    private val cacheVersion = MutableStateFlow(0L)
    val cacheVersionFlow: StateFlow<Long> = cacheVersion.asStateFlow()

    private val _changeFlow = MutableStateFlow<CacheChange>(CacheChange.FullRefresh())
    val changeFlow: Flow<CacheChange> = _changeFlow.asStateFlow()

    // Hot in-memory cache for all audio files to avoid full entity re-mapping on every Room emission.
    private val hotCacheLock = Any()
    private var hotAudioFiles: List<AudioFile>? = null

    // Tracks whether warmup completed successfully - used to skip redundant hasCache() queries
    @Volatile
    private var wasWarmedUp = false

    private fun invalidateHotCache() {
        synchronized(hotCacheLock) {
            hotAudioFiles = null
        }
    }

    /**
     * Warms up the cache by ensuring database is initialized and cache data is preloaded.
     * Call this early in app startup to avoid first-access delays and race conditions.
     */
    suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            try {
                hasCache()
                getCachedAudioFilesOnce()
                wasWarmedUp = true
                Timber.d(TAG, "warmUp completed successfully, hotCache size: ${hotAudioFiles?.size ?: 0}")
            } catch (e: Exception) {
                Timber.e(TAG, "warmUp failed", e)
                wasWarmedUp = false
            }
        }
    }

    /**
     * Returns true if warmUp has completed successfully and cache is ready.
     * This avoids redundant hasCache() queries on startup.
     */
    fun isWarm(): Boolean = wasWarmedUp

    // ==================== Audio File Cache Operations ====================
    
    /**
     * Gets all cached audio files as a Flow for reactive UI updates.
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>> {
        return audioFileDao.getAllAudioFiles().map { entities ->
            synchronized(hotCacheLock) {
                hotAudioFiles?.let { return@map it }
            }
            entities.map { it.toAudioFile() }.also { mapped ->
                synchronized(hotCacheLock) {
                    hotAudioFiles = mapped
                }
            }
        }
    }

    /**
     * Gets all cached audio files as a one-shot query.
     */
    suspend fun getCachedAudioFilesOnce(): List<AudioFile> = withContext(Dispatchers.IO) {
        synchronized(hotCacheLock) {
            hotAudioFiles?.let { return@withContext it }
        }
        val mapped = audioFileDao.getAllAudioFiles().first().map { it.toAudioFile() }
        synchronized(hotCacheLock) {
            hotAudioFiles = mapped
        }
        mapped
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

        // Update artist links for split artists
        updateArtistLinksForFiles(audioFiles)

        invalidateHotCache()

        val albumKeys = audioFiles.mapNotNull { CacheChangeKeys.extractAlbumKey(it) }.toSet()
        val artistKeys = audioFiles.mapNotNull { CacheChangeKeys.extractArtistKey(it) }.toSet()
        _changeFlow.value = CacheChange.FilesBatchUpdated(
            filePaths = audioFiles.map { it.path },
            albumKeys = albumKeys,
            artistKeys = artistKeys
        )

        bumpCacheVersion()

        Timber.i("DB batch insert: ${entities.size} records")
    }
    
    /**
     * Syncs a single file to cache (e.g., after metadata edit).
     * Preserves lastEditedByUserAt if already set.
     */
    suspend fun syncFileToCache(audioFile: AudioFile) = withContext(Dispatchers.IO) {
        Timber.d("syncFileToCache: path=${audioFile.path}, album=${audioFile.metadata.album}, albumArtist=${audioFile.metadata.albumArtist}, albumId=${audioFile.mediaStoreAlbumId}")
        val file = File(audioFile.path)
        val customFieldsJson = if (audioFile.metadata.customFields.isNotEmpty()) {
            gson.toJson(audioFile.metadata.customFields)
        } else null

        val existingEntity = audioFileDao.getAudioFileByPath(audioFile.path)
        val lastEditedByUserAt = existingEntity?.lastEditedByUserAt

        val entity = CachedAudioFileEntity.fromAudioFile(
            audioFile = audioFile,
            fileLastModified = file.lastModified(),
            customFieldsJson = customFieldsJson,
            lastEditedByUserAt = lastEditedByUserAt
        )

        audioFileDao.insert(entity)
        Timber.d("syncFileToCache: inserted to DB, invalidating hotCache")
        invalidateHotCache()

        val albumKey = CacheChangeKeys.extractAlbumKey(audioFile)
        val artistKey = CacheChangeKeys.extractArtistKey(audioFile)
        _changeFlow.value = CacheChange.FileUpdated(
            filePath = audioFile.path,
            albumKey = albumKey,
            artistKey = artistKey
        )

        bumpCacheVersion()
        Timber.d("syncFileToCache: done, cacheVersion=${cacheVersion.value}")
    }

    /**
     * Marks a file as edited by user with current timestamp.
     * This prevents EnrichmentWorker from overwriting user's manual edits.
     */
    suspend fun markFileAsEditedByUser(filePath: String) = withContext(Dispatchers.IO) {
        audioFileDao.updateLastEditedByUserAt(filePath, System.currentTimeMillis())
        invalidateHotCache()
        bumpCacheVersion()
        Timber.d("markFileAsEditedByUser: path=$filePath")
    }

    /**
     * Removes a file from cache.
     */
    suspend fun removeFromCache(filePath: String) = withContext(Dispatchers.IO) {
        val existingFile = audioFileDao.getAudioFileByPath(filePath)
        val albumKey = existingFile?.let {
            val af = it.toAudioFile()
            CacheChangeKeys.extractAlbumKey(af)
        }
        val artistKey = existingFile?.let {
            val af = it.toAudioFile()
            CacheChangeKeys.extractArtistKey(af)
        }

        audioFileDao.deleteByPath(filePath)
        invalidateHotCache()

        _changeFlow.value = CacheChange.FileDeleted(
            filePath = filePath,
            albumKey = albumKey,
            artistKey = artistKey
        )

        bumpCacheVersion()
        Timber.i("DB delete: $filePath")
    }

    /**
     * Removes multiple files from cache.
     */
    suspend fun removeFromCache(filePaths: List<String>) = withContext(Dispatchers.IO) {
        if (filePaths.isNotEmpty()) {
            val existingFiles = filePaths.mapNotNull { audioFileDao.getAudioFileByPath(it) }
            val albumKeys = existingFiles.mapNotNull {
                val af = it.toAudioFile()
                CacheChangeKeys.extractAlbumKey(af)
            }.toSet()
            val artistKeys = existingFiles.mapNotNull {
                val af = it.toAudioFile()
                CacheChangeKeys.extractArtistKey(af)
            }.toSet()

            audioFileDao.deleteByPaths(filePaths)
            invalidateHotCache()

            _changeFlow.value = CacheChange.FilesBatchUpdated(
                filePaths = filePaths,
                albumKeys = albumKeys,
                artistKeys = artistKeys
            )

            bumpCacheVersion()
            Timber.i("DB batch delete: ${filePaths.size} files")
        }
    }

    /**
     * Clears the entire cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        audioFileDao.deleteAll()
        albumThumbnailDao.deleteAll()
        artistLinkDao.deleteAll()
        invalidateHotCache()
        bumpCacheVersion()
        Timber.i("DB: Cache cleared")
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
                cached == null || (cached / 1000) != (lastModified / 1000)
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
            invalidateHotCache()
            bumpCacheVersion()
        }
        deletedCount
    }

    internal fun bumpCacheVersion() {
        cacheVersion.update { current -> current + 1 }
    }
    
    // ==================== Album Thumbnail Cache Operations ====================
    
    /**
     * Gets cached album thumbnail bytes from disk cache.
     */
    suspend fun getAlbumThumbnail(albumId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val entity = albumThumbnailDao.getThumbnailByAlbumId(albumId) ?: return@withContext null
        entity.coverKey.let { coverDiskCache.getThumbnail(it) }
    }
    
    /**
     * Gets multiple cached album thumbnails from disk cache.
     */
    suspend fun getAlbumThumbnails(albumIds: List<Long>): Map<Long, ByteArray> = 
        withContext(Dispatchers.IO) {
            val entities = albumThumbnailDao.getThumbnailsByAlbumIds(albumIds)
            entities.mapNotNull { entity ->
                coverDiskCache.getThumbnail(entity.coverKey)?.let { entity.albumId to it }
            }.toMap()
        }
    
    /**
     * Checks if album has cached thumbnail.
     */
    suspend fun hasAlbumThumbnail(albumId: Long): Boolean = withContext(Dispatchers.IO) {
        albumThumbnailDao.hasThumbnail(albumId)
    }
    
    /**
     * Caches album thumbnail from bitmap to disk cache.
     */
    suspend fun cacheAlbumThumbnail(
        albumId: Long,
        albumArtist: String?,
        albumName: String,
        bitmap: Bitmap,
        sourceUri: String? = null
    ) = withContext(Dispatchers.IO) {
        val coverKey = coverDiskCache.generateCacheKey(albumArtist, albumName)
        coverDiskCache.saveThumbnail(coverKey, bitmap)
        
        val entity = AlbumThumbnailEntity(
            albumId = albumId,
            coverKey = coverKey,
            width = bitmap.width,
            height = bitmap.height,
            sourceUri = sourceUri
        )
        
        albumThumbnailDao.insert(entity)
        Timber.d(TAG, "Cached thumbnail for album $albumId (key: $coverKey)")
    }
    
    /**
     * Caches album thumbnail from bytes (e.g., from MediaStore or embedded).
     */
    suspend fun cacheAlbumThumbnailFromBytes(
        albumId: Long,
        albumArtist: String?,
        albumName: String,
        bytes: ByteArray,
        sourceUri: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeThumbnailBitmap(bytes, 512) ?: return@withContext
            cacheAlbumThumbnail(albumId, albumArtist, albumName, bitmap, sourceUri)
            bitmap.recycle()
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to cache thumbnail for album $albumId", e)
        }
    }

    private fun decodeThumbnailBitmap(bytes: ByteArray, maxSize: Int): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(bytes, 0, bytes.size)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                val (targetWidth, targetHeight) = calculateTargetSize(size.width, size.height, maxSize)
                decoder.setTargetSize(targetWidth, targetHeight)
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to decode thumbnail bitmap", e)
            null
        }
    }

    private fun calculateTargetSize(width: Int, height: Int, maxSize: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return maxSize to maxSize
        if (width <= maxSize && height <= maxSize) return width to height
        val ratio = width.toFloat() / height.toFloat()
        return if (ratio > 1f) {
            maxSize to (maxSize / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxSize * ratio).toInt().coerceAtLeast(1) to maxSize
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

    // ==================== Artist Link Operations ====================

    /**
     * Updates artist links for all files in cache.
     * Called after updateCache() to populate artist_links table.
     */
    private suspend fun updateArtistLinksForFiles(audioFiles: List<AudioFile>) = withContext(Dispatchers.IO) {
        try {
            val separatorEnabled = settingsDataStore.artistSeparatorEnabled.first()
            if (!separatorEnabled) return@withContext

            val separators = settingsDataStore.artistSeparatorsSet.first()
            
            audioFiles.forEach { file ->
                val artistString = file.metadata.artist ?: file.metadata.albumArtist
                if (!artistString.isNullOrBlank()) {
                    updateArtistLinks(file.id, artistString, separators)
                }
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to update artist links", e)
        }
    }

    /**
     * Updates artist links for a track.
     * Parses the artist string by separators and stores multiple records.
     */
    suspend fun updateArtistLinks(trackId: String, artistString: String?, separators: Set<String>) = withContext(Dispatchers.IO) {
        if (artistString.isNullOrBlank()) return@withContext

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        val artists = artistString.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        artistLinkDao.deleteByTrackId(trackId)
        val entities = artists.map { ArtistLinkEntity(trackId = trackId, artistName = it) }
        artistLinkDao.insertAll(entities)
    }

    /**
     * Gets all artist names.
     */
    fun getAllArtistNames(): Flow<List<String>> = artistLinkDao.getAllArtistNames()

    /**
     * Gets artist counts.
     */
    fun getArtistCounts(): Flow<List<ArtistLinkDao.ArtistCount>> = artistLinkDao.getArtistCounts()

    /**
     * Gets all tracks for an artist.
     */
    suspend fun getTrackIdsForArtist(artistName: String): List<String> = withContext(Dispatchers.IO) {
        artistLinkDao.getTrackIdsForArtist(artistName)
    }

    /**
     * Deletes all artist links.
     */
    suspend fun clearArtistLinks() = withContext(Dispatchers.IO) {
        artistLinkDao.deleteAll()
    }

    // ==================== Enrichment Job Queue ====================

    suspend fun enqueueEnrichmentJobs(filePaths: List<String>) = withContext(Dispatchers.IO) {
        val jobs = filePaths.map { path ->
            EnrichmentJobEntity(
                id = path.hashCode().toString(),
                filePath = path,
                status = EnrichmentJobEntity.STATUS_PENDING
            )
        }
        enrichmentJobDao.upsertPendingJobs(jobs)
        Timber.d("Enqueued ${jobs.size} enrichment jobs")
    }

    suspend fun getPendingEnrichmentJobs(limit: Int): List<EnrichmentJobEntity> = withContext(Dispatchers.IO) {
        enrichmentJobDao.getPendingJobs(limit)
    }

    suspend fun updateEnrichmentJobStatus(id: String, status: Int) = withContext(Dispatchers.IO) {
        enrichmentJobDao.updateStatus(id, status)
    }

    suspend fun hasEnrichmentJobForPath(path: String): Boolean = withContext(Dispatchers.IO) {
        enrichmentJobDao.hasJobForPath(path)
    }

    suspend fun clearCompletedEnrichmentJobs() = withContext(Dispatchers.IO) {
        enrichmentJobDao.deleteByStatus(EnrichmentJobEntity.STATUS_COMPLETED)
        Timber.d("Cleared completed enrichment jobs")
    }

    suspend fun clearFailedEnrichmentJobs() = withContext(Dispatchers.IO) {
        enrichmentJobDao.deleteByStatus(EnrichmentJobEntity.STATUS_FAILED)
        Timber.d("Cleared failed enrichment jobs")
    }

    // ==================== Paging Support ====================
    
    /**
     * Creates a Pager for paged audio files.
     * @param pageSize Number of items per page
     * @param directoryPath Optional directory filter
     */
    fun getPagedAudioFiles(
        pageSize: Int = 50,
        directoryPath: String? = null
    ): Flow<PagingData<CachedAudioFileEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false,
                initialLoadSize = pageSize * 2
            ),
            pagingSourceFactory = {
                AudioFilePagingSource(audioFileDao, directoryPath)
            }
        ).flow
    }
}
