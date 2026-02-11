package com.mp3tag.android.data.repository

import android.content.Context
import com.mp3tag.android.data.local.AudioFileScanner
import com.mp3tag.android.data.local.metadata.JaudiotaggerMetadataProcessor
import com.mp3tag.android.domain.model.AudioFile
import com.mp3tag.android.domain.model.AudioMetadata
import com.mp3tag.android.domain.model.ReplayGainInfo
import com.mp3tag.android.domain.repository.AudioRepository
import com.mp3tag.android.domain.repository.ReplayGainRepository
import com.mp3tag.android.domain.repository.ScanProgress
import com.mp3tag.android.domain.repository.ScanQuality
import com.mp3tag.android.domain.repository.ScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
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

    override fun scanAudioFiles(directoryPath: String?): Flow<List<AudioFile>> {
        return if (directoryPath == null) {
            audioFileScanner.scanAllAudioFiles()
        } else {
            audioFileScanner.scanDirectory(directoryPath)
        }.onEach { audioFiles ->
            // Enhance audio files with detailed metadata
            audioFiles.map { audioFile ->
                val detailedMetadata = metadataProcessor.readMetadata(audioFile.path)
                if (detailedMetadata != null) {
                    audioFile.copy(metadata = detailedMetadata)
                } else {
                    audioFile
                }
            }
        }
    }

    override suspend fun getAudioFile(filePath: String): Result<AudioFile> =
        withContext(Dispatchers.IO) {
            try {
                // Check if file is accessible
                if (!audioFileScanner.isFileAccessible(filePath)) {
                    return@withContext Result.failure(Exception("File not accessible: $filePath"))
                }

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
                    duration = audioInfo?.let { (bitrate, _, _) ->
                        // Estimate duration from file size and bitrate
                        val bytesPerSecond = bitrate / 8
                        if (bytesPerSecond > 0) (javaFile.length() / bytesPerSecond) * 1000 else 0L
                    } ?: 0L,
                    bitrate = audioInfo?.first ?: 0,
                    sampleRate = audioInfo?.second ?: 0,
                    channels = audioInfo?.third ?: 0
                )

                Result.success(enhancedAudioFile)
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
                val success = metadataProcessor.updateMetadata(filePath, metadata)
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to update metadata for: $filePath"))
                }
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
                    val success = metadataProcessor.updateMetadata(filePath, updatedMetadata)
                    if (success) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Failed to set album art for: $filePath"))
                    }
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
                    val success = metadataProcessor.updateMetadata(filePath, updatedMetadata)
                    if (success) {
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Failed to remove album art from: $filePath"))
                    }
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
 * Provides basic ReplayGain reading and writing capabilities.
 */
@Singleton
class ReplayGainRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: JaudiotaggerMetadataProcessor
) : ReplayGainRepository {

    override fun scanReplayGain(
        filePaths: List<String>,
        scanQuality: ScanQuality
    ): Flow<ScanProgress> = flow {
        var processedCount = 0
        val totalFiles = filePaths.size

        filePaths.forEachIndexed { index, filePath ->
            emit(
                ScanProgress(
                    currentFile = index + 1,
                    totalFiles = totalFiles,
                    percentage = (index + 1).toFloat() / totalFiles,
                    currentFilePath = filePath,
                    status = ScanStatus.SCANNING
                )
            )

            try {
                // TODO: Implement actual audio analysis for ReplayGain calculation
                // For now, we'll simulate progress
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                emit(
                    ScanProgress(
                        currentFile = index + 1,
                        totalFiles = totalFiles,
                        percentage = index.toFloat() / totalFiles,
                        currentFilePath = filePath,
                        status = ScanStatus.FAILED
                    )
                )
            }

            processedCount++
        }

        emit(
            ScanProgress(
                currentFile = totalFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = ScanStatus.COMPLETED
            )
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun applyReplayGain(
        filePaths: List<String>,
        applyToTrack: Boolean,
        applyToAlbum: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement actual ReplayGain tag writing
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readReplayGain(filePath: String): Result<ReplayGainInfo?> =
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implement actual ReplayGain reading from tags
                Result.success(null)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
