package com.voxly.data.local

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.os.Environment
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioFormat
import com.voxly.domain.model.parseMediaStoreTrackField
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for scanning and accessing audio files from device storage.
 * Uses Android's MediaStore API for efficient file discovery.
 * 
 * Optimization features:
 * - Persistent caching with Room database
 * - Incremental scanning (only scan new/modified files)
 * - Parallel metadata reading
 * - Lazy metadata loading
 */
@Singleton
class AudioFileScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val libraryCache: MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore
) {
    private val contentResolver: ContentResolver = context.contentResolver

    // Simple memory cache for directory scans (respects forceRefresh)
    private val directoryScanCache = mutableMapOf<String, List<AudioFile>>()

    companion object {
        private const val TAG = "AudioFileScanner"
        private val AUDIO_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

        /** Collator for Chinese pinyin sorting */
        private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }

        // Helper function to parse MediaStore TRACK field correctly
        // Uses shared implementation from domain model
        fun parseTrackField(value: Int): Pair<Int?, Int?> = parseMediaStoreTrackField(value)

        // Fast projection - only MediaStore columns, no file parsing needed
        private val FAST_PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.TRACK
        )

        // Legacy projection - kept for compatibility
        private val PROJECTION = FAST_PROJECTION
    }

    /**
     * Bundle of scan settings to reduce multiple first() calls.
     */
    private data class SettingsBundle(
        val minDurationFilterEnabled: Boolean,
        val minDurationFilterThresholdMs: Long,
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val selectedDirectoryUris: List<String>,
        val blacklistDirectoryUris: List<String>
    )

    /**
     * Scans audio files within a specific directory.
     * @param directoryPath The directory path to scan
     * @param forceRefresh If true, bypass cache and re-scan from MediaStore
     * @return Flow emitting lists of audio files found
     */
    fun scanDirectory(directoryPath: String, forceRefresh: Boolean = false): Flow<List<AudioFile>> = flow {
        val normalizedDirectory = directoryPath.trimEnd('/', '\\')

        // Check cache first (unless forceRefresh)
        if (!forceRefresh && normalizedDirectory in directoryScanCache) {
            Timber.d(TAG, "Using directory cache: $normalizedDirectory")
            emit(directoryScanCache[normalizedDirectory]!!)
            return@flow
        }

        val audioFiles = mutableListOf<AudioFile>()

        // Get duration filter settings using combine for efficiency
        val scanSettings = combine(
            settingsDataStore.minDurationFilterEnabled,
            settingsDataStore.minDurationFilterThresholdMs
        ) { minDurationEnabled, minDurationThreshold ->
            Pair(minDurationEnabled, minDurationThreshold)
        }.first()
        val minDurationFilterEnabled = scanSettings.first
        val minDurationFilterThresholdMs = scanSettings.second


        // Fast path: query MediaStore by directory prefix
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND (")
            append("${MediaStore.Audio.Media.DATA} = ?")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE ?")
            append(" OR ${MediaStore.Audio.Media.DATA} LIKE ?")
            append(")")
        }
        val selectionArgs = arrayOf(
            normalizedDirectory,
            "$normalizedDirectory/%",
            "$normalizedDirectory\\%"
        )

        val cursor: Cursor? = contentResolver.query(
            AUDIO_URI,
            FAST_PROJECTION,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val yearColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val bitrateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
            val trackColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (it.moveToNext()) {
                val filePath = it.getString(dataColumn) ?: continue
                val extension = filePath.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val albumId = it.getLong(albumIdColumn).takeIf { value -> value > 0L }
                    // Parse MediaStore TRACK field: trackNumber | (totalTracks << 16)
                    val (parsedTrack, parsedTotal) = parseTrackField(it.getInt(trackColumn))
                    val metadata = com.voxly.domain.model.AudioMetadata(
                        title = it.getString(titleColumn)?.takeIf { value -> value.isNotBlank() },
                        artist = it.getString(artistColumn)?.takeIf { value -> value.isNotBlank() },
                        album = it.getString(albumColumn)?.takeIf { value -> value.isNotBlank() },
                        year = it.getString(yearColumn)?.takeIf { value -> value.isNotBlank() },
                        trackNumber = parsedTrack,
                        totalTracks = parsedTotal,
                        albumArt = null,
                        albumArtist = null,
                        genre = null,
                        discNumber = null,
                        totalDiscs = null,
                        composer = null,
                        lyricist = null,
                        conductor = null,
                        originalArtist = null,
                        comment = null,
                        lyrics = null,
                        customFields = emptyMap()
                    )

                    // Apply minimum duration filter
                    val duration = it.getLong(durationColumn)
                    if (duration != 0L && (!minDurationFilterEnabled || duration >= minDurationFilterThresholdMs)) {
                        // sampleRate and channels are not in MediaStore - load on-demand when needed
                        // Don't read from file here as it's too slow for large libraries
                        audioFiles.add(
                            AudioFile(
                                id = it.getLong(idColumn).toString(),
                                path = filePath,
                                name = it.getString(nameColumn) ?: filePath.substringAfterLast('/'),
                                size = it.getLong(sizeColumn),
                                duration = it.getLong(durationColumn),
                                format = extension.uppercase(),
                                bitrate = it.getInt(bitrateColumn) / 1000,
                                sampleRate = 0,  // Loaded on-demand
                                channels = 0,    // Loaded on-demand
                                mediaStoreAlbumId = albumId,
                                metadata = metadata
                            )
                        )
                    }
                }
            }
        }

        // Fallback for files not yet indexed by MediaStore
        if (audioFiles.isEmpty()) {
            val directory = File(directoryPath)
            if (directory.exists() && directory.isDirectory) {
                scanDirectoryRecursive(directory, audioFiles)
            }
        }

        // Load album years from file tags for albums with empty MediaStore year
        // This is done here instead of in ViewModel for instant album year display
        loadAlbumYearsFromTags(audioFiles)

        // Cache the result
        val sortedFiles = audioFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
        directoryScanCache[normalizedDirectory] = sortedFiles

        emit(sortedFiles)
    }.flowOn(Dispatchers.IO)

    /**
     * Loads detailed metadata on-demand (lazy loading).
     * Call this when user views song details, edits metadata, or needs lyrics.
     * 
     * @param filePath Path to the audio file
     * @param includeAlbumArt Whether to include album art bytes
     * @return Full AudioMetadata including lyrics, ReplayGain, composer, etc.
     */
    suspend fun loadDetailedMetadata(
        filePath: String, 
        includeAlbumArt: Boolean = false
    ): com.voxly.domain.model.AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            metadataProcessor.readMetadata(filePath, includeAlbumArt)
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to load detailed metadata: $filePath", e)
            null
        }
    }

    /**
     * Loads audio properties on-demand (lazy loading).
     * Call this when precise sample rate, channels, or accurate bitrate is needed.
     * 
     * @param filePath Path to the audio file
     * @return AudioInfo with bitrate, sampleRate, channels, durationMs
     */
    suspend fun loadAudioProperties(filePath: String): TagLibMetadataProcessor.AudioInfo? = 
        withContext(Dispatchers.IO) {
            try {
                metadataProcessor.readAudioInfo(filePath)
            } catch (e: Exception) {
                Timber.w(TAG, "Failed to load audio properties: $filePath", e)
                null
            }
        }

    /**
     * Recursively scans a directory for audio files.
     * Now reads full metadata including lyrics from file tags.
     */
    private suspend fun scanDirectoryRecursive(directory: File, audioFiles: MutableList<AudioFile>) {
        val audioExtensions = setOf("mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "ape", "opus")

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectoryRecursive(file, audioFiles)
            } else {
                val extension = file.name.substringAfterLast('.').lowercase()
                if (extension in audioExtensions && file.canRead()) {
                    val audioFile = createAudioFileFromPath(file.absolutePath)
                    audioFiles.add(audioFile)
                }
            }
        }
    }

    /**
     * Creates an AudioFile from a file path without querying MediaStore.
     * Reads full metadata including lyrics from file tags.
     * Used for files not in MediaStore database.
     */
    private suspend fun createAudioFileFromPath(filePath: String): AudioFile {
        val file = File(filePath)
        val extension = file.name.substringAfterLast('.').lowercase()

        // Read full metadata including lyrics from file tags
        val fullMetadata = metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
            ?: com.voxly.domain.model.AudioMetadata()

        // Try to get duration from MediaStore first, fallback to TagLib if not found
        var duration = 0L
        var bitrate = 0
        try {
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            val cursor = contentResolver.query(
                AUDIO_URI,
                arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.BITRATE),
                selection,
                selectionArgs,
                null
            )

        cursor?.use {
                if (it.moveToFirst()) {
                    val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val bitrateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                    duration = it.getLong(durationCol)
                    // MediaStore returns bitrate in bps, convert to kbps
                    bitrate = it.getInt(bitrateCol) / 1000
                }
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to query MediaStore for: $filePath", e)
        }

        // Fallback: if MediaStore has no data, use TagLib to read audio properties
        var sampleRate = 0
        var channels = 0
        if (duration == 0L) {
            val audioInfo = metadataProcessor.readAudioInfo(filePath)
            duration = audioInfo?.durationMs ?: 0L
            if (bitrate == 0) {
                // TagLib returns bitrate in bps, convert to kbps
                bitrate = (audioInfo?.bitrate ?: 0) / 1000
            }
            sampleRate = audioInfo?.sampleRate ?: 0
            channels = audioInfo?.channels ?: 0
        } else {
            // MediaStore doesn't provide sampleRate and channels, always need to read from file
            val audioInfo = metadataProcessor.readAudioInfo(filePath)
            sampleRate = audioInfo?.sampleRate ?: 0
            channels = audioInfo?.channels ?: 0
        }

        return AudioFile(
            id = filePath.hashCode().toString(),
            path = filePath,
            name = file.name,
            size = file.length(),
            duration = duration,
            format = extension.uppercase(),
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            metadata = fullMetadata
        )
    }

    /**
     * Parses basic metadata from MediaStore cursor data.
     */
    private fun parseBasicMetadata(
        title: String?,
        artist: String?,
        album: String?,
        year: String?,
        albumId: Long
    ): com.voxly.domain.model.AudioMetadata {
        return com.voxly.domain.model.AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            // Album art URI will be resolved lazily
            albumArt = null
        )
    }

    /**
     * Gets the album art URI for a specific album ID.
     */
    fun getAlbumArtUri(albumId: Long): Uri {
        return Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())
    }

    // ============== OPTIMIZED SCANNING METHODS ==============

    /**
     * Get cached audio files immediately (fast, from database).
     * Use this for initial display while scanning in background.
     */
    fun getCachedAudioFiles(): Flow<List<AudioFile>> = libraryCache.getCachedAudioFiles()

    /**
     * Check if cache has data.
     */
    suspend fun hasCachedData(): Boolean = libraryCache.hasCache()

    /**
     * Get count of cached files.
     */
    suspend fun getCachedFileCount(): Int = libraryCache.getCachedFileCount()

    /**
     * Get last scan timestamp.
     */
    suspend fun getLastScanTime(): Long? = libraryCache.getLastScanTime()

    /**
     * Optimized full scan that uses cache when available.
     * - If cache exists and forceRefresh is false, return cache immediately (no scan)
     * - If forceRefresh is true or no cache exists, perform full scan and update cache
     * 
     * @param forceRefresh If true, ignore cache and rescan everything
     * @return Flow emitting scan results
     */
    fun scanAudioFilesOptimized(forceRefresh: Boolean = false): Flow<List<AudioFile>> = flow {
        // Quick check: if not forcing refresh, try to use cache first
        if (!forceRefresh) {
            // Fast check: get count first to avoid loading all data if cache is empty
            val cachedCount = libraryCache.getCachedFileCount()
            if (cachedCount > 0) {
                Timber.d(TAG, "Using cache: $cachedCount files")
                val cachedFiles = libraryCache.getCachedAudioFiles().first()
                if (cachedFiles.isNotEmpty()) {
                    // Cache is already sorted by title in DAO query
                    emit(cachedFiles)
                    return@flow  // Return cache only - no full scan needed
                }
            } else {
                Timber.d(TAG, "No cache found, performing full scan")
            }
        }

        // No cache or force refresh: perform full scan and cache results
        // Emit empty list first to show loading state
        emit(emptyList())

        val allFiles = mutableListOf<AudioFile>()
        scanAllFilesForCacheWithProgress(allFiles) { current, _ ->
            // Yield periodically to prevent blocking the main thread
            // Note: yield is handled by flowOn(Dispatchers.IO) below
        }
        
        Timber.d(TAG, "Full scan complete: ${allFiles.size} files found")
        
        // Update cache with all scanned files
        libraryCache.updateCache(allFiles)
        
        // MediaStore query already returns sorted data by title,
        // but we re-sort to ensure correct order after building the list
        allFiles.sortWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) })
        emit(allFiles)
    }.flowOn(Dispatchers.IO)

    /**
     * Incremental scan - only scans new or modified files.
     * Much faster than full scan for large libraries.
     * 
     * @param onProgress Progress callback for UI updates
     * @return Flow emitting updated files
     */
    fun scanIncremental(
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Flow<List<AudioFile>> = flow {
        // Get all current files with their modification times
        val currentFiles = mutableListOf<Pair<String, Long>>()
        scanAllAudioFilesInternal(currentFiles)
        
        // Find files that need rescanning
        val pathsNeedingRescan = libraryCache.getFilesNeedingRescan(currentFiles)
        
        Timber.i(TAG, "Incremental scan: ${pathsNeedingRescan.size} files need rescanning")
        
        // Get cached files that don't need rescan
        val cachedFiles = libraryCache.getCachedAudioFiles()
            .first()
            .filter { it.path !in pathsNeedingRescan }
        
        // Rescan only changed files in parallel
        val updatedFiles = if (pathsNeedingRescan.isNotEmpty()) {
            val newlyScanned = scanFilesInParallel(pathsNeedingRescan)
            libraryCache.updateCache(newlyScanned)
            newlyScanned
        } else {
            emptyList()
        }
        
        // Combine cached + updated files
        val allFiles = (cachedFiles + updatedFiles).distinctBy { it.path }
        
        // Clean up deleted files
        libraryCache.cleanupDeletedFiles(currentFiles.map { it.first })
        
        emit(allFiles.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name) }))
    }.flowOn(Dispatchers.IO)

    /**
     * Scan files in parallel for better performance.
     * Uses coroutines to read multiple metadata simultaneously.
     */
    private suspend fun scanFilesInParallel(
        filePaths: List<String>,
        maxConcurrency: Int = 4
    ): List<AudioFile> = coroutineScope {
        val results = mutableListOf<AudioFile>()
        
        // Process in batches to avoid memory pressure
        filePaths.chunked(maxConcurrency * 2).forEach { batch ->
            val deferred = batch.map { path ->
                async(Dispatchers.IO) {
                    try {
                        createAudioFileFromPath(path)
                    } catch (e: Exception) {
                        Timber.w(TAG, "Failed to scan: $path", e)
                        null
                    }
                }
            }
            
            val scanned = deferred.awaitAll().filterNotNull()
            results.addAll(scanned)
        }
        
        results
    }

    /**
     * Lightweight scan that only gets file paths and modification times.
     * Used for determining what needs rescanning.
     */
    private suspend fun scanAllAudioFilesInternal(
        output: MutableList<Pair<String, Long>>
    ) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        val cursor: Cursor? = contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media._ID),
            selection,
            null,
            null
        )
        
        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            
            while (it.moveToNext()) {
                val filePath = it.getString(dataColumn)
                val extension = filePath.substringAfterLast('.', "")
                
                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val file = File(filePath)
                    if (file.exists()) {
                        output.add(filePath to file.lastModified())
                    }
                }
            }
        }
    }

    /**
     * Scan all audio files and return full AudioFile objects.
     * OPTIMIZED: Uses MediaStore only - no file-level metadata reading.
     * Detailed metadata (lyrics, ReplayGain, etc.) is loaded on-demand.
     */
    private suspend fun scanAllFilesForCache(output: MutableList<AudioFile>) {
        scanAllFilesForCacheWithProgress(output) { _, _ -> }
    }

    /**
     * Scan all audio files with progress callback.
     * OPTIMIZED: Uses MediaStore only - no file-level metadata reading.
     * Includes periodic yielding to prevent blocking.
     * 
     * @param output List to collect scanned files
     * @param onProgress Progress callback (current, total)
     */
    private suspend fun scanAllFilesForCacheWithProgress(
        output: MutableList<AudioFile>,
        onProgress: (current: Int, total: Int) -> Unit
    ) {

        // Get all settings using combine for efficiency
        val allSettings = combine(
            settingsDataStore.minDurationFilterEnabled,
            settingsDataStore.minDurationFilterThresholdMs,
            settingsDataStore.whitelistEnabled,
            settingsDataStore.blacklistEnabled,
            settingsDataStore.selectedDirectoryUris,
            settingsDataStore.blacklistDirectoryUris
        ) { values ->
            SettingsBundle(
                minDurationFilterEnabled = values[0] as Boolean,
                minDurationFilterThresholdMs = values[1] as Long,
                whitelistEnabled = values[2] as Boolean,
                blacklistEnabled = values[3] as Boolean,
                selectedDirectoryUris = values[4] as List<String>,
                blacklistDirectoryUris = values[5] as List<String>
            )
        }.first()

        val whitelistDirs = if (allSettings.whitelistEnabled) {
            allSettings.selectedDirectoryUris
                .map { getPathFromUri(Uri.parse(it)) }
                .filter { it.isNotBlank() }
        } else emptyList()
        val blacklistDirs = if (allSettings.blacklistEnabled) {
            allSettings.blacklistDirectoryUris
                .map { getPathFromUri(Uri.parse(it)) }
                .filter { it.isNotBlank() }
        } else emptyList()

        val minDurationFilterEnabled = allSettings.minDurationFilterEnabled
        val minDurationFilterThresholdMs = allSettings.minDurationFilterThresholdMs
        val whitelistEnabled = allSettings.whitelistEnabled
        val blacklistEnabled = allSettings.blacklistEnabled

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        
        // First, get the count for progress reporting
        val countCursor = contentResolver.query(
            AUDIO_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            null,
            null
        )
        val totalCount = countCursor?.count ?: 0
        countCursor?.close()
        
        Timber.d(TAG, "Starting full scan of $totalCount audio files")

        // Now scan with actual data
        val cursor: Cursor? = contentResolver.query(
            AUDIO_URI,
            FAST_PROJECTION,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val yearColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val bitrateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
            val trackColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            var currentIndex = 0
            while (it.moveToNext()) {
                currentIndex++
                
                // Report progress periodically
                if (currentIndex % 50 == 0 || currentIndex == totalCount) {
                    onProgress(currentIndex, totalCount)
                }

                val filePath = it.getString(dataColumn)
                val extension = filePath.substringAfterLast('.', "")

                if (AudioFormat.fromExtension(extension) != AudioFormat.OTHER) {
                    val albumId = it.getLong(albumIdColumn).takeIf { albumId -> albumId > 0L }
                    
                    // Parse MediaStore TRACK field: trackNumber | (totalTracks << 16)
                    val (parsedTrack, parsedTotal) = parseTrackField(it.getInt(trackColumn))
                    
                    // FAST: Use MediaStore data directly - no file parsing
                    val metadata = com.voxly.domain.model.AudioMetadata(
                        title = it.getString(titleColumn)?.takeIf { s -> s.isNotBlank() },
                        artist = it.getString(artistColumn)?.takeIf { s -> s.isNotBlank() },
                        album = it.getString(albumColumn)?.takeIf { s -> s.isNotBlank() },
                        year = it.getString(yearColumn)?.takeIf { s -> s.isNotBlank() },
                        trackNumber = parsedTrack,
                        totalTracks = parsedTotal,
                        // Album art URI is built from albumId - no bytes loaded
                        albumArt = null,
                        // Detailed fields (lyrics, ReplayGain, composer, etc.) loaded on-demand
                        albumArtist = null,
                        genre = null,
                        discNumber = null,
                        totalDiscs = null,
                        composer = null,
                        lyricist = null,
                        conductor = null,
                        originalArtist = null,
                        comment = null,
                        lyrics = null,
                        customFields = emptyMap()
                    )

                    val audioFile = com.voxly.domain.model.AudioFile(
                        id = it.getLong(idColumn).toString(),
                        path = filePath,
                        name = it.getString(nameColumn) ?: filePath.substringAfterLast('/'),
                        size = it.getLong(sizeColumn),
                        duration = it.getLong(durationColumn),
                        format = extension.uppercase(),
                        bitrate = it.getInt(bitrateColumn),
                        sampleRate = 0,  // Not in MediaStore, loaded on-demand
                        channels = 0,    // Not in MediaStore, loaded on-demand
                        mediaStoreAlbumId = albumId,
                        metadata = metadata
                    )


                    // Apply minimum duration filter
                    val duration = it.getLong(durationColumn)
                    if (duration == 0L || (minDurationFilterEnabled && duration < minDurationFilterThresholdMs)) {
                        // Skip files that are too short or have no duration
                    } else {
                        // Apply whitelist/blacklist filter
                        if (shouldIncludeFile(filePath, whitelistEnabled, blacklistEnabled, whitelistDirs, blacklistDirs)) {
                            output.add(audioFile)
                        }
                    }

                }
            }
        }
        
        Timber.d(TAG, "Full scan complete: ${output.size} files found")
    }

    /**
     * Clear the scan cache.
     */
    suspend fun clearCache(): Unit = libraryCache.clearCache()

    /**
     * Remove a file from cache (e.g., when file is deleted).
     */
    suspend fun removeFromCache(filePath: String): Unit = libraryCache.removeFromCache(filePath)

    /**
     * Update cache for a single file (e.g., after metadata edit).
     */
    suspend fun syncFileToCache(audioFile: AudioFile): Unit = libraryCache.syncFileToCache(audioFile)

    /**
     * Checks if a file exists and is readable.
     */
    suspend fun isFileAccessible(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.exists() && file.canRead()
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Converts a content URI to filesystem path.
     * Used for whitelist/blacklist directory filtering.
     */
    private fun getPathFromUri(uri: Uri): String {
        return runCatching {
            if (uri.scheme == "file") return@runCatching uri.path.orEmpty()
            if (uri.scheme != "content") return@runCatching uri.path.orEmpty()

            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId.startsWith("raw:")) {
                return@runCatching documentId.removePrefix("raw:")
            }

            val idParts = documentId.split(":", limit = 2)
            val volume = idParts.firstOrNull().orEmpty()
            val relativePath = idParts.getOrNull(1)?.trim('/').orEmpty()

            when {
                volume.equals("primary", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    if (relativePath.isEmpty()) externalRoot else "$externalRoot/$relativePath"
                }
                volume.equals("home", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    val documentsRoot = "$externalRoot/Documents"
                    if (relativePath.isEmpty()) documentsRoot else "$documentsRoot/$relativePath"
                }
                volume.isNotEmpty() -> {
                    if (relativePath.isEmpty()) "/storage/$volume" else "/storage/$volume/$relativePath"
                }
                else -> uri.path.orEmpty()
            }
        }.getOrElse {
            uri.path.orEmpty()
        }
    }

    /**
     * Checks if a file path should be included based on whitelist/blacklist settings.
     * Whitelist is applied first, then blacklist removes from the result.
     */
    private fun shouldIncludeFile(
        filePath: String,
        whitelistEnabled: Boolean,
        blacklistEnabled: Boolean,
        whitelistDirs: List<String>,
        blacklistDirs: List<String>
    ): Boolean {
        // Whitelist check: if enabled and not empty, file must be in whitelist
        if (whitelistEnabled && whitelistDirs.isNotEmpty()) {
            val inWhitelist = whitelistDirs.any { dir ->
                filePath.startsWith(dir.trimEnd('/') + "/") || filePath == dir
            }
            if (!inWhitelist) return false
        }

        // Blacklist check: if enabled and not empty, file must NOT be in blacklist
        if (blacklistEnabled && blacklistDirs.isNotEmpty()) {
            val inBlacklist = blacklistDirs.any { dir ->
                filePath.startsWith(dir.trimEnd('/') + "/") || filePath == dir
            }
            if (inBlacklist) return false
        }

        return true
    }

    /**
     * Loads album years from file tags for albums where MediaStore year is empty.
     * Uses readMetadata with includeAlbumArt=false for fast tag-only reading.
     * This populates the year data during scan so it's instantly available when albums are displayed.
     */
    private suspend fun loadAlbumYearsFromTags(audioFiles: MutableList<AudioFile>) {
        // Group files by album to find unique albums
        val albumsWithEmptyYear = audioFiles
            .filter { it.metadata.album?.isNotBlank() == true }
            .groupBy { it.metadata.album to it.metadata.artist }
            .filter { (_, files) ->
                files.any { it.metadata.year.isNullOrBlank() }
            }
            .map { (_, files) -> files.first() }
            .take(50) // Limit to first 50 albums to avoid long scan time

        if (albumsWithEmptyYear.isEmpty()) return

        // Load year for each album (parallel for speed)
        coroutineScope {
            albumsWithEmptyYear.map { file ->
                async {
                    try {
                        val metadata = metadataProcessor.readMetadata(file.path, includeAlbumArt = false)
                        metadata?.year
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().forEachIndexed { index, year ->
                if (!year.isNullOrBlank()) {
                    val file = albumsWithEmptyYear[index]
                    // Update the file's metadata with year from tags
                    val updatedMetadata = file.metadata.copy(year = year)
                    // Find and update the file in the list
                    val fileIndex = audioFiles.indexOfFirst { it.path == file.path }
                    if (fileIndex >= 0) {
                        audioFiles[fileIndex] = file.copy(metadata = updatedMetadata)
                    }
                }
            }
        }
    }
}
