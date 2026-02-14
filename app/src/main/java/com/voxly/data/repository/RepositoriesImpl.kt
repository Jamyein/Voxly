package com.voxly.data.repository

import android.content.Context
import android.util.Log
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.metadata.JaudiotaggerMetadataProcessor
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
    private val metadataProcessor: JaudiotaggerMetadataProcessor
) : AudioRepository {
    companion object {
        private const val TAG = "AudioRepositoryImpl"
    }

    override fun scanAudioFiles(directoryPath: String?): Flow<List<AudioFile>> {
        return if (directoryPath == null) {
            audioFileScanner.scanAllAudioFiles()
        } else {
            audioFileScanner.scanDirectory(directoryPath)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAudioFile(filePath: String): Result<AudioFile> =
        withContext(Dispatchers.IO) {
            try {
                // Read basic info from file
                val javaFile = java.io.File(filePath)
                val extension = filePath.substringAfterLast('.').lowercase()

                val audioFile = AudioFile(
                    id = filePath.hashCode().toString(),
                    path = filePath,
                    name = javaFile.name,
                    size = javaFile.length(),
                    duration = 0L,
                    format = extension.uppercase(),
                    bitrate = 0,
                    sampleRate = 0,
                    channels = 0,
                    metadata = AudioMetadata()
                )

                // Read detailed metadata
                val detailedMetadata = metadataProcessor.readMetadata(filePath)
                val audioInfo = metadataProcessor.readAudioInfo(filePath)

                val enhancedAudioFile = audioFile.copy(
                    metadata = detailedMetadata ?: AudioMetadata(),
                    duration = audioInfo?.durationMs ?: 0L,
                    bitrate = audioInfo?.bitrate ?: 0,
                    sampleRate = audioInfo?.sampleRate ?: 0,
                    channels = audioInfo?.channels ?: 0
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
    private val metadataProcessor: JaudiotaggerMetadataProcessor,
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readReplayGain(filePath: String): Result<ReplayGainInfo?> =
        replayGainScanner.readReplayGainFromFile(filePath)
            ?.let { Result.success(it) }
            ?: Result.success(null)
}
