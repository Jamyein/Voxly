package com.voxly.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.gson.Gson
import com.voxly.core.util.PathUtils
import com.voxly.data.local.SettingsDataStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import com.voxly.data.local.cache.*
import com.voxly.data.local.cover.CoverDiskCache
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.CacheChange
import com.voxly.domain.model.CacheChangeKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

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
    private val directorySnapshotDao: DirectorySnapshotDao by lazy { database.directorySnapshotDao() }

    private val cacheVersion = MutableStateFlow(0L)
    val cacheVersionFlow: StateFlow<Long> = cacheVersion.asStateFlow()

    /**
     * Cache change notifications.
     *
     * Uses [MutableSharedFlow] with `replay = 1` + `extraBufferCapacity = 64` +
     * `DROP_OLDEST` instead of [MutableStateFlow]. StateFlow conflates emissions:
     * if two events arrive while the only subscriber (e.g. AlbumArtistAggregator
     * running on `applicationScope` at `Dispatchers.Default`) is busy processing
     * the previous one, the intermediate event is silently overwritten and lost.
     * SharedFlow buffers up to 64 events; on overflow the oldest is dropped so the
     * latest always reaches the collector.
     *
     * `replay = 1` preserves the contract that late subscribers (e.g. ViewModels
     * created after an edit) still see the most recent change. New subscribers
     * that join before any event has been emitted receive nothing — collectors
     * that need an initial state (e.g. AlbumArtistAggregator) must trigger their
     * own initial FullRefresh.
     */
    private val _changeFlow = MutableSharedFlow<CacheChange>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val changeFlow: SharedFlow<CacheChange> = _changeFlow.asSharedFlow()

    // Hot in-memory cache for all audio files to avoid full entity re-mapping on every Room emission.
    // Uses java.lang.ref.SoftReference so the GC can reclaim this under memory pressure.
    // Under normal conditions with adequate heap this stays populated and gives fast access.
    private val hotCacheLock = Any()
    private var hotAudioFilesRef = java.lang.ref.SoftReference<List<AudioFile>>(null)

    /** Snapshot of the hot cache under the hotCacheLock. Returns null if never populated or reclaimed by GC. */
    private var hotAudioFiles: List<AudioFile>?
        get() = synchronized(hotCacheLock) { hotAudioFilesRef.get() }
        set(value) = synchronized(hotCacheLock) { hotAudioFilesRef = java.lang.ref.SoftReference(value) }

    // Serializes write paths (updateCache / syncFileToCache / removeFromCache / clearCache)
    // so a long-running scanner batch insert cannot interleave with a per-file edit
    // or rename happening in the foreground. Reads are unaffected.
    private val writeMutex = Mutex()

    // Tracks whether warmup completed successfully - used to skip redundant hasCache() queries
    @Volatile
    private var wasWarmedUp = false

    private fun invalidateHotCache() {
        synchronized(hotCacheLock) {
            hotAudioFiles = null
        }
    }

    /**
     * Normalizes a file path before it is used as a cache key.
     *
     * `path` is now the primary key of `cached_audio_files` and `enrichment_jobs`
     * (replacing the lossy 32-bit `path.hashCode()` ID — see lesson.md #24 + #25).
     * Two callers that pass paths differing only in `./` segments, case
     * (case-insensitive filesystems), or NFC/NFD Unicode must hit the same row,
     * not two duplicate rows. Centralising the normalization here guarantees
     * every read/write path applies the same rule.
     */
    private fun normalizedPath(filePath: String): String = PathUtils.normalizeFilePath(filePath)

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
                Timber.i(TAG, "warmUp completed successfully, hotCache size: ${hotAudioFiles?.size ?: 0}")
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
        audioFileDao.getAudioFileByPath(normalizedPath(filePath))?.toAudioFile()
    }

    /**
     * Gets raw cached entity for accessing metadata timestamps.
     */
    suspend fun getCachedFileEntity(filePath: String): CachedAudioFileEntity? = withContext(Dispatchers.IO) {
        audioFileDao.getAudioFileByPath(normalizedPath(filePath))
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
     *
     * DB writes (cached_audio_files + artist_links) happen inside a single Room transaction
     * so a process crash between them cannot leave the tables inconsistent.
     */
    suspend fun updateCache(audioFiles: List<AudioFile>) = withContext(Dispatchers.IO) {
        writeMutex.withLock { updateCacheInternal(audioFiles) }
    }

    private suspend fun updateCacheInternal(audioFiles: List<AudioFile>) {
        if (audioFiles.isEmpty()) {
            invalidateHotCache()
            return
        }

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

        // Read settings once before opening the transaction (don't do I/O inside withTransaction)
        val separatorEnabled = settingsDataStore.artistSeparatorEnabled.first()
        val separators = if (separatorEnabled) {
            settingsDataStore.artistSeparatorsSet.first()
        } else emptySet()

        // DB writes (audio file rows + artist links) in one atomic transaction
        database.withTransaction {
            audioFileDao.insertAllChunked(entities)
            if (separatorEnabled) {
                updateArtistLinksForFilesInternal(audioFiles, separators)
            }
        }

        invalidateHotCache()

        val albumKeys = audioFiles.mapNotNull { CacheChangeKeys.extractAlbumKey(it) }.toSet()
        val artistKeys = audioFiles.mapNotNull { CacheChangeKeys.extractArtistKey(it) }.toSet()
        _changeFlow.tryEmit(
            CacheChange.FilesBatchUpdated(
                filePaths = audioFiles.map { it.path },
                albumKeys = albumKeys,
                artistKeys = artistKeys
            )
        )

        bumpCacheVersion()

        Timber.i("DB batch insert: ${entities.size} records")
    }

    /**
     * Internal helper that updates artist_links inside an existing transaction.
     * Reads track IDs to delete in one batch, inserts new links in one batch.
     *
     * `trackId` is now the file's path (the previous `file.id` — a 32-bit
     * `path.hashCode()` — was the source of cross-workspace cache collisions;
     * see lesson.md #24 + #25).
     */
    private suspend fun updateArtistLinksForFilesInternal(
        audioFiles: List<AudioFile>,
        separators: Set<String>
    ) {
        val trackIds = audioFiles.map { it.path }
        if (trackIds.isEmpty()) return

        artistLinkDao.deleteByTrackIds(trackIds)

        if (separators.isEmpty()) return

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        val newLinks = audioFiles.flatMap { file ->
            val artistString = file.metadata.artist ?: file.metadata.albumArtist ?: return@flatMap emptyList()
            artistString.split(Regex(regex))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { ArtistLinkEntity(trackId = file.path, artistName = it) }
        }
        if (newLinks.isNotEmpty()) {
            artistLinkDao.insertAll(newLinks)
        }
    }
    
    /**
     * Syncs a single file to cache (e.g., after metadata edit).
     * Preserves lastEditedByUserAt if already set.
     * DB operations are wrapped in a transaction for atomicity.
     */
    suspend fun syncFileToCache(audioFile: AudioFile) = withContext(Dispatchers.IO) {
        writeMutex.withLock { syncFileToCacheInternal(audioFile) }
    }

    private suspend fun syncFileToCacheInternal(audioFile: AudioFile) {
        // Normalize the path at the persistence boundary. `path` is now the
        // primary key — without normalization two equivalent paths
        // (e.g. `/foo/./bar` vs `/foo/bar`, or differing NFC/NFD forms) would
        // create two cache rows. See lesson.md #25.
        val normalized = audioFile.copy(path = normalizedPath(audioFile.path))
        Timber.i("syncFileToCache: path=${normalized.path}, album=${normalized.metadata.album}, albumArtist=${normalized.metadata.albumArtist}, albumId=${normalized.mediaStoreAlbumId}")

        // Transaction ensures atomic read-modify-write on the entity
        database.withTransaction {
            val file = File(normalized.path)
            val customFieldsJson = if (normalized.metadata.customFields.isNotEmpty()) {
                gson.toJson(normalized.metadata.customFields)
            } else null

            val existingEntity = audioFileDao.getAudioFileByPath(normalized.path)
            val lastEditedByUserAt = existingEntity?.lastEditedByUserAt

            val entity = CachedAudioFileEntity.fromAudioFile(
                audioFile = normalized,
                fileLastModified = file.lastModified(),
                customFieldsJson = customFieldsJson,
                lastEditedByUserAt = lastEditedByUserAt
            )

            audioFileDao.insert(entity)
        }
        // Non-DB operations outside transaction - cache invalidation and state updates
        Timber.d("syncFileToCache: inserted to DB, invalidating hotCache")
        invalidateHotCache()

        val albumKey = CacheChangeKeys.extractAlbumKey(normalized)
        val artistKey = CacheChangeKeys.extractArtistKey(normalized)
        _changeFlow.tryEmit(
            CacheChange.FileUpdated(
                filePath = normalized.path,
                albumKey = albumKey,
                artistKey = artistKey
            )
        )

        bumpCacheVersion()
        Timber.d("syncFileToCache: done, cacheVersion=${cacheVersion.value}")
    }

    /**
     * Marks a file as edited by user with current timestamp.
     * This prevents EnrichmentWorker from overwriting user's manual edits.
     */
    suspend fun markFileAsEditedByUser(filePath: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock { markFileAsEditedByUserInternal(filePath) }
    }

    private suspend fun markFileAsEditedByUserInternal(filePath: String) {
        val normalized = normalizedPath(filePath)
        audioFileDao.updateLastEditedByUserAt(normalized, System.currentTimeMillis())
        invalidateHotCache()
        bumpCacheVersion()
        Timber.i("markFileAsEditedByUser: path=$normalized")
    }

    /**
     * Removes a file from cache.
     */
    suspend fun removeFromCache(filePath: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock { removeFromCacheInternal(filePath) }
    }

    private suspend fun removeFromCacheInternal(filePath: String) {
        val normalized = normalizedPath(filePath)
        val existingFile = audioFileDao.getAudioFileByPath(normalized)
        val albumKey = existingFile?.let {
            val af = it.toAudioFile()
            CacheChangeKeys.extractAlbumKey(af)
        }
        val artistKey = existingFile?.let {
            val af = it.toAudioFile()
            CacheChangeKeys.extractArtistKey(af)
        }

        audioFileDao.deleteByPath(normalized)
        artistLinkDao.deleteByTrackId(normalized)
        invalidateHotCache()

        _changeFlow.tryEmit(
            CacheChange.FileDeleted(
                filePath = normalized,
                albumKey = albumKey,
                artistKey = artistKey
            )
        )

        bumpCacheVersion()
        Timber.i("DB delete: $normalized")
    }

    /**
     * Removes multiple files from cache.
     */
    suspend fun removeFromCache(filePaths: List<String>) = withContext(Dispatchers.IO) {
        if (filePaths.isEmpty()) return@withContext
        writeMutex.withLock { removeFromCacheBatchInternal(filePaths) }
    }

    private suspend fun removeFromCacheBatchInternal(filePaths: List<String>) {
        if (filePaths.isNotEmpty()) {
            val normalized = filePaths.map { normalizedPath(it) }
            val existingFiles = normalized.mapNotNull { audioFileDao.getAudioFileByPath(it) }
            val albumKeys = existingFiles.mapNotNull {
                val af = it.toAudioFile()
                CacheChangeKeys.extractAlbumKey(af)
            }.toSet()
            val artistKeys = existingFiles.mapNotNull {
                val af = it.toAudioFile()
                CacheChangeKeys.extractArtistKey(af)
            }.toSet()

            audioFileDao.deleteByPaths(normalized)
            artistLinkDao.deleteByTrackIds(normalized)
            invalidateHotCache()

            _changeFlow.tryEmit(
                CacheChange.FilesBatchUpdated(
                    filePaths = normalized,
                    albumKeys = albumKeys,
                    artistKeys = artistKeys
                )
            )

            bumpCacheVersion()
            Timber.i("DB batch delete: ${normalized.size} files")
        }
    }

    /**
     * Clears the entire cache.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            audioFileDao.deleteAll()
            albumThumbnailDao.deleteAll()
            artistLinkDao.deleteAll()
            invalidateHotCache()
            bumpCacheVersion()
            wasWarmedUp = false
            Timber.i("DB: Cache cleared")
        }
    }
    
    // ==================== Incremental Scan Support ====================
    
    /**
     * Gets files that need rescanning based on modification times.
     * Compares modification times directly (both in milliseconds).
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
        val normalized = normalizedPath(filePath)
        val file = File(normalized)
        if (!file.exists()) {
            audioFileDao.deleteByPath(normalized)
            artistLinkDao.deleteByTrackId(normalized)
            return@withContext true
        }

        val cached = audioFileDao.getAudioFileByPath(normalized)
        cached == null || cached.fileLastModifiedAt != file.lastModified()
    }
    
    /**
     * Cleans up deleted files from cache.
     */
    suspend fun cleanupDeletedFiles(currentPaths: List<String>): Int = withContext(Dispatchers.IO) {
        val normalizedPaths = currentPaths.map { normalizedPath(it) }
        val deletedCount = audioFileDao.deleteNotInPaths(normalizedPaths)
        artistLinkDao.deleteNotInTrackIds(normalizedPaths)
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
        Timber.i(TAG, "Cached thumbnail for album $albumId (key: $coverKey)")
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
                    // `trackId` is now the file's path (see updateArtistLinksForFilesInternal).
                    updateArtistLinks(file.path, artistString, separators)
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
            // `filePath` is the primary key of EnrichmentJobEntity — no separate
            // `id` hash is needed (and was the source of cross-workspace collisions
            // before this refactor; see lesson.md #24 + #25). Normalize so the
            // PK matches what later lookups will use.
            EnrichmentJobEntity(
                filePath = normalizedPath(path),
                status = EnrichmentJobEntity.STATUS_PENDING
            )
        }
        enrichmentJobDao.upsertPendingJobs(jobs)
        Timber.i("Enqueued ${jobs.size} enrichment jobs")
    }

    suspend fun getPendingEnrichmentJobs(limit: Int): List<EnrichmentJobEntity> = withContext(Dispatchers.IO) {
        enrichmentJobDao.getPendingJobs(limit)
    }

    suspend fun updateEnrichmentJobStatus(filePath: String, status: Int) = withContext(Dispatchers.IO) {
        enrichmentJobDao.updateStatus(normalizedPath(filePath), status)
    }

    suspend fun hasEnrichmentJobForPath(path: String): Boolean = withContext(Dispatchers.IO) {
        enrichmentJobDao.hasJobForPath(normalizedPath(path))
    }

    suspend fun clearCompletedEnrichmentJobs() = withContext(Dispatchers.IO) {
        enrichmentJobDao.deleteByStatus(EnrichmentJobEntity.STATUS_COMPLETED)
        Timber.i("Cleared completed enrichment jobs")
    }

    suspend fun clearFailedEnrichmentJobs() = withContext(Dispatchers.IO) {
        enrichmentJobDao.deleteByStatus(EnrichmentJobEntity.STATUS_FAILED)
        Timber.i("Cleared failed enrichment jobs")
    }

    suspend fun getDirectorySnapshot(directoryUri: String): DirectorySnapshotEntity? {
        return directorySnapshotDao.getSnapshot(directoryUri)
    }

    suspend fun saveDirectorySnapshot(directoryUri: String, fileCount: Int) {
        directorySnapshotDao.upsert(
            DirectorySnapshotEntity(
                directoryUri = directoryUri,
                fileCount = fileCount,
                lastCheckTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDirectorySnapshot(directoryUri: String) {
        directorySnapshotDao.delete(directoryUri)
    }

    suspend fun getAllDirectorySnapshots(): List<DirectorySnapshotEntity> {
        return directorySnapshotDao.getAllSnapshots()
    }
}
