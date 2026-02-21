package com.voxly.data.repository

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.replaygain.ReplayGainScanner
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanProgress
import com.voxly.domain.repository.ScanQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AudioRepository using jaudiotagger for metadata operations.
 */
@Singleton
class AudioRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val metadataProcessor: TagLibMetadataProcessor
) : AudioRepository {
    companion object {
        private const val TAG = "AudioRepositoryImpl"
    }

    override fun scanAudioFiles(
        directoryPath: String?,
        forceRefresh: Boolean
    ): Flow<List<AudioFile>> {
        return if (directoryPath == null) {
            // Use optimized scanning with cache
            audioFileScanner.scanAudioFilesOptimized(forceRefresh = forceRefresh)
        } else {
            audioFileScanner.scanDirectory(directoryPath)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun hasCachedData(): Boolean = audioFileScanner.hasCachedData()

    override fun getCachedAudioFiles(): Flow<List<AudioFile>> = audioFileScanner.getCachedAudioFiles()


    override suspend fun getAudioFile(filePath: String): Result<AudioFile> =
        withContext(Dispatchers.IO) {
            try {
                // Read basic info from file
                val javaFile = java.io.File(filePath)
                val extension = filePath.substringAfterLast('.').lowercase()

                // Try to get duration and bitrate from MediaStore first
                var duration = 0L
                var bitrate = 0
                try {
                    val selection = "${MediaStore.Audio.Media.DATA} = ?"
                    val selectionArgs = arrayOf(filePath)
                    val cursor = context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
                    Log.w(TAG, "Failed to query MediaStore for: $filePath", e)
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

                val audioFile = AudioFile(
                    id = filePath.hashCode().toString(),
                    path = filePath,
                    name = javaFile.name,
                    size = javaFile.length(),
                    duration = duration,
                    format = extension.uppercase(),
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    channels = channels,
                    metadata = AudioMetadata()
                )

                val mediaStoreFallbackMetadata = readMediaStoreBasicMetadata(filePath)

                // Read detailed metadata
                val detailedMetadata = metadataProcessor.readMetadata(filePath)
                val mergedMetadata = mergeWithFallback(detailedMetadata, mediaStoreFallbackMetadata)

                val enhancedAudioFile = audioFile.copy(
                    metadata = mergedMetadata ?: AudioMetadata()
                )

                Result.success(enhancedAudioFile)
            } catch (e: SecurityException) {
                Result.failure(Exception("File not accessible due to storage permission/scope: $filePath", e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

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
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
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
                arrayOf(filePath),
                null
            )
            // Helper to parse MediaStore TRACK field: trackNumber | (totalTracks << 16)
            // Also handles corrupted track values like 1001 which should be 1
            fun parseTrackField(value: Int): Pair<Int?, Int?> {
                if (value <= 0) return Pair(null, null)
                var trackNumber = value and 0xFFFF
                val totalTracks = (value shr 16) and 0xFFFF
                
                // Normalize corrupted track numbers (some sources add 1000 offset incorrectly)
                // e.g., 1001 should be 1, 1012 should be 12
                if (trackNumber > 1000 && trackNumber < 10000) {
                    val normalized = trackNumber - 1000
                    if (normalized in 1..999) trackNumber = normalized
                }
                
                return Pair(
                    if (trackNumber > 0) trackNumber else null,
                    if (totalTracks > 0) totalTracks else null
                )
            }
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

    private fun mergeWithFallback(
        primary: AudioMetadata?,
        fallback: AudioMetadata?
    ): AudioMetadata? {
        if (primary == null) return fallback
        if (fallback == null) return primary

        return primary.copy(
            title = primary.title.takeIf { !it.isNullOrBlank() } ?: fallback.title,
            artist = primary.artist.takeIf { !it.isNullOrBlank() } ?: fallback.artist,
            album = primary.album.takeIf { !it.isNullOrBlank() } ?: fallback.album,
            year = primary.year.takeIf { !it.isNullOrBlank() } ?: fallback.year,
            trackNumber = primary.trackNumber ?: fallback.trackNumber
        )
    }

    override suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                metadataProcessor.updateMetadata(filePath, metadata).fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { cause ->
                        Result.failure(
                            Exception("Failed to update metadata for: $filePath. ${cause.message}", cause)
                        )
                    }
                )
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
                        onSuccess = { Result.success(Unit) },
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
                        onSuccess = { Result.success(Unit) },
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
 * Uses ReplayGainScanner for audio analysis and jaudiotagger for tag writing.
 */
@Singleton
class ReplayGainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val replayGainScanner: ReplayGainScanner
) : ReplayGainRepository {

    override fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality
    ): Flow<ScanProgress> = replayGainScanner.scanReplayGain(filePaths, scanQuality)

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
