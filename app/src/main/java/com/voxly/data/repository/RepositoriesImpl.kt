package com.voxly.data.repository

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.voxly.core.util.Constants
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.cover.CoverDiskCache
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.data.local.metadata.RecoverableMediaStoreException
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.metadata.TagWriteManager
import com.voxly.data.local.replaygain.ReplayGainScanner
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.model.parseMediaStoreTrackField
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioRepository using jaudiotagger for metadata operations.
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val tagWriteManager: TagWriteManager,
    private val libraryCache: MusicLibraryCache,
    private val coverUriProvider: CoverUriProvider,
    private val coverDiskCache: CoverDiskCache
) : AudioRepository {
    companion object {
        private const val TAG = "AudioRepositoryImpl"
    }

    override fun scanAudioFiles(
        directoryPath: String?,
        forceRefresh: Boolean
    ): Flow<List<AudioFile>> = flow {
        // Use unified scan API with optimized coroutine handling
        val files = audioFileScanner.scan(
            directoryPaths = directoryPath?.let { listOf(it) } ?: emptyList(),
            incremental = false,
            forceRefresh = forceRefresh
        )
        emit(files)
    }.flowOn(Dispatchers.IO)

    override suspend fun hasCachedData(): Boolean = audioFileScanner.hasCachedData()

    override fun getCachedAudioFiles(): Flow<List<AudioFile>> = audioFileScanner.getCachedAudioFiles()


    override suspend fun getAudioFile(
        filePath: String,
        includeAlbumArt: Boolean
    ): Result<AudioFile> =
        withContext(Dispatchers.IO) {
            try {
                val javaFile = java.io.File(filePath)
                val extension = filePath.substringAfterLast('.').lowercase()
                val fileLastModified = javaFile.lastModified()

                // Try Room cache first — skip TagLib I/O if cache is valid
                val cachedEntity = libraryCache.getCachedFileEntity(filePath)
                val cachedFile = cachedEntity?.toAudioFile()
                val isFileUnchanged = cachedEntity != null && javaFile.exists() &&
                    cachedEntity.fileLastModifiedAt == fileLastModified

                if (cachedFile != null && isFileUnchanged) {
                    val completeMetadata = metadataProcessor.readAllMetadata(
                        filePath,
                        includeAlbumArt = includeAlbumArt,
                        bypassCache = true
                    )
                    val mergedMeta = if (completeMetadata?.metadata != null) {
                        mergeWithFallback(completeMetadata.metadata, cachedFile.metadata)
                            ?: completeMetadata.metadata
                    } else {
                        cachedFile.metadata
                    }
                    val resultFile = cachedFile.copy(metadata = mergedMeta)
                    return@withContext Result.success(resultFile)
                }

                // Cache miss: run independent I/O operations in parallel
                val (completeMetadata, mediaStoreInfo) = coroutineScope {
                    val tagLibDeferred = async {
                        metadataProcessor.readAllMetadata(
                            filePath,
                            includeAlbumArt = includeAlbumArt,
                            bypassCache = true
                        )
                    }
                    val mediaStoreDeferred = async {
                        queryMediaStoreAudioInfo(filePath)
                    }
                    Pair(tagLibDeferred.await(), mediaStoreDeferred.await())
                }

                var finalDuration = mediaStoreInfo.duration
                var finalBitrate = mediaStoreInfo.bitrate
                var finalSampleRate = 0
                var finalChannels = 0

                completeMetadata?.audioInfo?.let { audioInfo ->
                    if (finalDuration == 0L) finalDuration = audioInfo.durationMs
                    if (finalBitrate == 0) finalBitrate = audioInfo.bitrate / Constants.BPS_TO_KBPS
                    finalSampleRate = audioInfo.sampleRate
                    finalChannels = audioInfo.channels
                }

                val audioFile = AudioFile(
                    id = filePath.hashCode().toString(),
                    path = filePath,
                    name = javaFile.name,
                    size = javaFile.length(),
                    duration = finalDuration,
                    format = extension.uppercase(),
                    bitrate = finalBitrate,
                    sampleRate = finalSampleRate,
                    channels = finalChannels,
                    metadata = completeMetadata?.metadata ?: AudioMetadata(),
                    mediaStoreAlbumId = mediaStoreInfo.mediaStoreAlbumId,
                    mediaStoreArtistId = mediaStoreInfo.mediaStoreArtistId
                )

                val mediaStoreFallbackMetadata = readMediaStoreBasicMetadata(filePath)
                val mergedMetadata = mergeWithFallback(audioFile.metadata, mediaStoreFallbackMetadata)
                val resultFile = audioFile.copy(metadata = mergedMetadata ?: AudioMetadata())

                // Persist complete metadata to cache for future reads
                libraryCache.syncFileToCache(resultFile)

                Result.success(resultFile)
            } catch (e: SecurityException) {
                Result.failure(Exception("File not accessible due to storage permission/scope: $filePath", e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getAudioFileDetail(filePath: String): Result<AudioFile> {
        return getAudioFile(filePath, includeAlbumArt = true)
    }

    private fun queryMediaStoreAudioInfo(filePath: String): AudioInfo {
        var duration = 0L
        var bitrate = 0
        var albumId: Long? = null
        var artistId: Long? = null
        try {
            val (selection, selectionArgs) = buildMediaStoreSelection(filePath)
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.BITRATE,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.ARTIST_ID
                ),
                selection,
                selectionArgs,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val bitrateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                    val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val artistIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    duration = it.getLong(durationCol)
                    bitrate = it.getInt(bitrateCol) / Constants.BPS_TO_KBPS
                    albumId = it.getLong(albumIdCol).takeIf { id -> id > 0 }
                    artistId = it.getLong(artistIdCol).takeIf { id -> id > 0 }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to query MediaStore audio info for: $filePath")
        }
        return AudioInfo(duration, bitrate, albumId, artistId)
    }

    private data class AudioInfo(
        val duration: Long,
        val bitrate: Int,
        val mediaStoreAlbumId: Long? = null,
        val mediaStoreArtistId: Long? = null
    )

    override suspend fun readMetadata(filePath: String): Result<AudioMetadata> =
        withContext(Dispatchers.IO) {
            try {
                val metadata = mergeWithFallback(
                    metadataProcessor.readMetadata(filePath),
                    readMediaStoreBasicMetadata(filePath)
                )
                if (metadata != null) {
                    Result.success(metadata)
                } else {
                    Result.failure(Exception("Failed to read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun readMediaStoreBasicMetadata(filePath: String): AudioMetadata? {
        return runCatching {
            val (selection, selectionArgs) = buildMediaStoreSelection(filePath)
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.TRACK
                ),
                selection,
                selectionArgs,
                null
            )
            fun parseTrackField(value: Int): Pair<Int?, Int?> = parseMediaStoreTrackField(value)
            cursor?.use {
                if (!it.moveToFirst()) {
                    return@use null
                }
                val trackValue = it.getInt(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
                val (parsedTrack, parsedTotal) = parseTrackField(trackValue)
                AudioMetadata(
                    title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                        ?.takeIf { value -> value.isNotBlank() },
                    artist = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                        ?.takeIf { value -> value.isNotBlank() },
                    album = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                        ?.takeIf { value -> value.isNotBlank() },
                    year = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR))
                        ?.takeIf { value -> value.isNotBlank() },
                    trackNumber = parsedTrack,
                    totalTracks = parsedTotal
                )
            }
        }.getOrNull()
    }

    private fun buildMediaStoreSelection(filePath: String): Pair<String, Array<String>> {
        val file = File(filePath)
        val relativePath = getRelativePathFromAbsolute(file.parentFile?.absolutePath.orEmpty())
        return if (relativePath != null) {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?" to
                arrayOf(file.name, relativePath)
        } else {
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?" to arrayOf(file.name)
        }
    }

    private fun getRelativePathFromAbsolute(absolutePath: String): String? {
        val normalized = absolutePath.replace('\\', '/').trimEnd('/')
        val primaryRoot = Environment.getExternalStorageDirectory().absolutePath
            .replace('\\', '/').trimEnd('/')
        if (!normalized.startsWith(primaryRoot)) return null
        val relative = normalized.removePrefix(primaryRoot).trimStart('/')
        return if (relative.isBlank()) "" else "$relative/"
    }

    private fun mergeWithFallback(
        primary: AudioMetadata?,
        fallback: AudioMetadata?
    ): AudioMetadata? {
        if (primary == null) return fallback
        if (fallback == null) return primary

        return primary.copy(
            title = primary.title.takeIf { !it.isNullOrBlank() } ?: fallback.title,
            artist = primary.artist.takeIf { !it.isNullOrBlank() } ?: fallback.artist,
            album = primary.album.takeIf { !it.isNullOrBlank() },
            albumArtist = primary.albumArtist.takeIf { !it.isNullOrBlank() },
            year = primary.year.takeIf { !it.isNullOrBlank() } ?: fallback.year,
            genre = primary.genre.takeIf { !it.isNullOrBlank() } ?: fallback.genre,
            trackNumber = primary.trackNumber ?: fallback.trackNumber,
            totalTracks = primary.totalTracks ?: fallback.totalTracks,
            discNumber = primary.discNumber ?: fallback.discNumber,
            totalDiscs = primary.totalDiscs ?: fallback.totalDiscs,
            composer = primary.composer.takeIf { !it.isNullOrBlank() } ?: fallback.composer,
            lyricist = primary.lyricist.takeIf { !it.isNullOrBlank() } ?: fallback.lyricist,
            conductor = primary.conductor.takeIf { !it.isNullOrBlank() } ?: fallback.conductor,
            originalArtist = primary.originalArtist.takeIf { !it.isNullOrBlank() } ?: fallback.originalArtist,
            comment = primary.comment.takeIf { !it.isNullOrBlank() } ?: fallback.comment,
            lyrics = primary.lyrics.takeIf { !it.isNullOrBlank() } ?: fallback.lyrics,
            albumArt = primary.albumArt ?: fallback.albumArt,
            customFields = primary.customFields.takeIf { it.isNotEmpty() } ?: fallback.customFields
        )
    }

    override suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Get old metadata before update for cache invalidation
                val oldFile = libraryCache.getCachedFile(filePath)
                val oldAlbumArtist = oldFile?.metadata?.albumArtist ?: oldFile?.metadata?.artist
                val oldAlbumName = oldFile?.metadata?.album

                // Use TagWriteManager for Android 16 safe write with whitelist support
                tagWriteManager.writeMetadata(filePath, metadata).fold(
                    onSuccess = {
                        // After metadata update, re-query MediaStore to get the CORRECT album ID
                        // because album/artist changes can result in a different MediaStore album ID
                        val correctAlbumId = audioFileScanner.queryMediaStoreAlbumId(filePath)
                        val updatedFile = getAudioFile(filePath, includeAlbumArt = true).getOrNull()

                        // If album/artist changed, invalidate old cover disk cache
                        val newAlbumArtist = metadata.albumArtist ?: metadata.artist
                        val newAlbumName = metadata.album
                        if (oldAlbumName != null &&
                            (oldAlbumName != newAlbumName || oldAlbumArtist != newAlbumArtist)) {
                            val oldCacheKey = coverDiskCache.generateCacheKey(oldAlbumArtist, oldAlbumName)
                            coverDiskCache.deleteThumbnail(oldCacheKey)
                            Timber.d(TAG, "Cleared old cover cache: $oldCacheKey")
                        }

                        // Always invalidate the correct album ID from MediaStore
                        // Use the old album ID from cache for comparison
                        val oldAlbumId = oldFile?.mediaStoreAlbumId
                        
                        if (correctAlbumId != null) {
                            CoverUriProvider.invalidateAlbumId(correctAlbumId)
                            Timber.d(TAG, "Invalidated correct album ID: $correctAlbumId")
                        }
                        
                        // Also invalidate old album ID if it differs from the new one
                        if (oldAlbumId != null && oldAlbumId != correctAlbumId) {
                            CoverUriProvider.invalidateAlbumId(oldAlbumId)
                            Timber.d(TAG, "Invalidated old album ID: $oldAlbumId")
                        }
                        
                        // Sync to cache with correct album ID
                        if (updatedFile != null) {
                            val fileToSync = if (correctAlbumId != null && updatedFile.mediaStoreAlbumId != correctAlbumId) {
                                updatedFile.copy(mediaStoreAlbumId = correctAlbumId)
                            } else {
                                updatedFile
                            }
                            libraryCache.syncFileToCache(fileToSync)
                        }
                        Result.success(Unit)
                    },
                    onFailure = { cause ->
                        Result.failure(
                            Exception("Failed to update metadata for: $filePath. ${cause.message}", cause)
                        )
                    }
                )
            } catch (e: RecoverableMediaStoreException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun extractAlbumArt(filePath: String): Result<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(metadataProcessor.extractAlbumArt(filePath))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun setAlbumArt(filePath: String, albumArtBytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val currentMetadata = metadataProcessor.readMetadata(filePath)
                if (currentMetadata != null) {
                    val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)
                    metadataProcessor.updateMetadata(filePath, updatedMetadata).fold(
                        onSuccess = {
                            // Re-read the updated file and sync to cache
                            val updatedFile = getAudioFile(filePath, includeAlbumArt = true).getOrNull()
                            if (updatedFile != null) {
                                libraryCache.syncFileToCache(updatedFile)
                            }
                            Result.success(Unit)
                        },
                        onFailure = { cause ->
                            Result.failure(
                                Exception("Failed to set album art for: $filePath. ${cause.message}", cause)
                            )
                        }
                    )
                } else {
                    Result.failure(Exception("Could not read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun removeAlbumArt(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val currentMetadata = metadataProcessor.readMetadata(filePath)
                if (currentMetadata != null) {
                    val updatedMetadata = currentMetadata.copy(albumArt = null)
                    metadataProcessor.updateMetadata(filePath, updatedMetadata).fold(
                        onSuccess = {
                            // Re-read the updated file and sync to cache
                            val updatedFile = getAudioFile(filePath, includeAlbumArt = true).getOrNull()
                            if (updatedFile != null) {
                                libraryCache.syncFileToCache(updatedFile)
                            }
                            Result.success(Unit)
                        },
                        onFailure = { cause ->
                            Result.failure(
                                Exception("Failed to remove album art from: $filePath. ${cause.message}", cause)
                            )
                        }
                    )
                } else {
                    Result.failure(Exception("Could not read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/**
 * Implementation of ReplayGainRepository.
 * Uses EBU R128 scanner (libebur128) for audio analysis and TagLib for tag writing.
 */
@Singleton
class ReplayGainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val replayGainScanner: ReplayGainScanner
) : ReplayGainRepository {

    override fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: com.voxly.domain.model.ReplayGainConfig
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGain(filePaths, scanQuality, targetLoudness, config)

    override fun scanReplayGainByAlbum(
        filesByAlbum: Map<String, List<String>>,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: com.voxly.domain.model.ReplayGainConfig
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGainByAlbum(filesByAlbum, scanQuality, targetLoudness, config)

    override fun scanReplayGainWithAlbumGrouping(
        filePaths: List<String>,
        scanQuality: ScanQuality,
        targetLoudness: Float,
        config: com.voxly.domain.model.ReplayGainConfig
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGainWithAlbumGrouping(filePaths, scanQuality, targetLoudness, config)

    override suspend fun applyReplayGain(
        filePaths: List<String>,
        applyToTrack: Boolean,
        applyToAlbum: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // This method is used for applying already scanned ReplayGain
            // For now, return success as the actual scan and save is done through scanReplayGain
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveReplayGain(
        filePath: String,
        replayGainInfo: ReplayGainInfo
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val success = replayGainScanner.saveReplayGainToFile(filePath, replayGainInfo)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to save ReplayGain information"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readReplayGain(filePath: String): Result<ReplayGainInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val info = replayGainScanner.readReplayGainFromFile(filePath)
                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
