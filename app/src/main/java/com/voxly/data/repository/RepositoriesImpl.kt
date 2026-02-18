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

    /**
     * Perform incremental scan - only scan new/modified files.
     * Much faster for large libraries.
     */
    fun scanAudioFilesIncremental(): Flow<List<AudioFile>> {
        return audioFileScanner.scanIncremental()
    }

    /**
     * Force full refresh of scan cache.
     */
    fun scanAudioFilesForceRefresh(): Flow<List<AudioFile>> {
        return audioFileScanner.scanAudioFilesOptimized(forceRefresh = true)
    }

    /**
     * Get cached file count.
     */
    suspend fun getCachedFileCount(): Int = audioFileScanner.getCachedFileCount()

    /**
     * Clear scan cache.
     */
    suspend fun clearScanCache(): Int = audioFileScanner.clearCache()

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

                // Read detailed metadata
                val detailedMetadata = metadataProcessor.readMetadata(filePath)

                val enhancedAudioFile = audioFile.copy(
                    metadata = detailedMetadata ?: AudioMetadata()
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
                val metadata = metadataProcessor.readMetadata(filePath)
                if (metadata != null) {
                    Result.success(metadata)
                } else {
                    Result.failure(Exception("Failed to read metadata from: $filePath"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
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
