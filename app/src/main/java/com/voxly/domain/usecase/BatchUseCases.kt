package com.voxly.domain.usecase

import android.os.SystemClock
import com.voxly.core.util.Logger
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.BatchResult
import com.voxly.domain.model.BatchStatus
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.ScanQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for batch editing metadata across multiple files.
 */
class BatchEditMetadataUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    private val batchEngine: BatchEngine<String>
) {
    /**
     * Applies the same metadata fields to multiple files.
     * @param filePaths List of file paths to update
     * @param metadata Metadata to apply
     * @param fieldsToUpdate Which fields to update (null values will be ignored)
     * @return Flow emitting progress (0.0 to 1.0) and current file
     */
    operator fun invoke(
        filePaths: List<String>,
        metadata: AudioMetadata,
        fieldsToUpdate: Set<MetadataField> = MetadataField.ALL
    ): Flow<BatchProgress> = flow {
        val startedAt = SystemClock.elapsedRealtime()
        val totalFiles = filePaths.size
        Logger.i(
            "Batch metadata edit started. files=$totalFiles fields=${fieldsToUpdate.joinToString(",")}",
            "BatchEdit"
        )

        batchEngine.execute(
            items = filePaths,
            operation = { filePath ->
                // Read existing metadata
                val existingMetadataResult = audioRepository.readMetadata(filePath)

                if (existingMetadataResult.isSuccess) {
                    val existingMetadata = existingMetadataResult.getOrNull()!!

                    // Merge metadata based on fields to update
                    val updatedMetadata = mergeMetadata(
                        existing = existingMetadata,
                        new = metadata,
                        fieldsToUpdate = fieldsToUpdate
                    )

                    // Update the file
                    val updateResult = audioRepository.updateMetadata(filePath, updatedMetadata)

                    if (updateResult.isSuccess) {
                        Logger.v("Batch metadata edit success file=$filePath", "BatchEdit")
                        Result.success(Unit)
                    } else {
                        Logger.w(
                            "Batch metadata edit failed file=$filePath reason=${updateResult.exceptionOrNull()?.message ?: "unknown"}",
                            "BatchEdit"
                        )
                        Result.failure(updateResult.exceptionOrNull() ?: Exception("Update failed"))
                    }
                } else {
                    Logger.w(
                        "Batch metadata read failed file=$filePath reason=${existingMetadataResult.exceptionOrNull()?.message ?: "unknown"}",
                        "BatchEdit"
                    )
                    Result.failure(existingMetadataResult.exceptionOrNull() ?: Exception("Read failed"))
                }
            },
            itemName = { it }
        ).collect { result ->
            emit(result.toBatchProgress())
        }

        Logger.i(
            "Batch metadata edit finished. files=$totalFiles success=${batchEngine.getFailedItems().let { totalFiles - it.size }} failed=${batchEngine.getFailedItems().size} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            "BatchEdit"
        )
    }

    /**
     * Merges metadata based on which fields should be updated.
     */
    private fun mergeMetadata(
        existing: AudioMetadata,
        new: AudioMetadata,
        fieldsToUpdate: Set<MetadataField>
    ): AudioMetadata {
        return existing.copy(
            title = if (MetadataField.TITLE in fieldsToUpdate) new.title ?: existing.title else existing.title,
            artist = if (MetadataField.ARTIST in fieldsToUpdate) new.artist ?: existing.artist else existing.artist,
            album = if (MetadataField.ALBUM in fieldsToUpdate) new.album ?: existing.album else existing.album,
            albumArtist = if (MetadataField.ALBUM_ARTIST in fieldsToUpdate) new.albumArtist ?: existing.albumArtist else existing.albumArtist,
            year = if (MetadataField.YEAR in fieldsToUpdate) new.year ?: existing.year else existing.year,
            genre = if (MetadataField.GENRE in fieldsToUpdate) new.genre ?: existing.genre else existing.genre,
            composer = if (MetadataField.COMPOSER in fieldsToUpdate) new.composer ?: existing.composer else existing.composer,
            trackNumber = if (MetadataField.TRACK_NUMBER in fieldsToUpdate) new.trackNumber else existing.trackNumber,
            discNumber = if (MetadataField.DISC_NUMBER in fieldsToUpdate) new.discNumber else existing.discNumber
        )
    }

    private fun BatchResult.toBatchProgress(): BatchProgress {
        val currentFile = successCount + failedCount + if (status == BatchStatus.PROCESSING) 1 else 0
        return BatchProgress(
            currentFile = currentFile.coerceAtMost(totalFiles),
            totalFiles = totalFiles,
            percentage = if (totalFiles > 0) (successCount + failedCount).toFloat() / totalFiles else 0f,
            currentFilePath = lastUpdatedFile,
            status = status,
            successCount = successCount,
            failureCount = failedCount
        )
    }
}

/**
 * Use case for batch ReplayGain scanning.
 */
class BatchReplayGainUseCase @Inject constructor(
    private val replayGainRepository: ReplayGainRepository,
    private val batchEngine: BatchEngine<String>
) {
    /**
     * Scans multiple files for ReplayGain.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz)
     * will be automatically downsampled for optimal performance.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level (determines max sample rate for scanning)
     * @param targetLoudness Target loudness in LUFS (default -14.0, standard ReplayGain)
     * @return Flow emitting scan progress
     */
    operator fun invoke(
        filePaths: List<String>,
        scanQuality: ScanQuality = ScanQuality.ACCURATE,
        targetLoudness: Float = -14f
    ): Flow<com.voxly.domain.repository.ScanProgress> = flow {
        batchEngine.execute(
            items = filePaths,
            operation = { filePath ->
                replayGainRepository.scanReplayGain(listOf(filePath), scanQuality, targetLoudness)
                Result.success(Unit)
            },
            itemName = { it }
        ).collect { result ->
            emit(
                com.voxly.domain.repository.ScanProgress(
                    currentFile = result.successCount + result.failedCount,
                    totalFiles = result.totalFiles,
                    percentage = (result.successCount + result.failedCount).toFloat() / result.totalFiles,
                    currentFilePath = result.lastUpdatedFile,
                    status = when (result.status) {
                        com.voxly.domain.model.BatchStatus.PROCESSING -> com.voxly.domain.repository.ScanStatus.SCANNING
                        com.voxly.domain.model.BatchStatus.COMPLETED -> com.voxly.domain.repository.ScanStatus.COMPLETED
                        com.voxly.domain.model.BatchStatus.CANCELLED -> com.voxly.domain.repository.ScanStatus.CANCELLED
                    }
                )
            )
        }
    }
}

/**
 * Use case for batch album art operations.
 */
class BatchAlbumArtUseCase @Inject constructor(
    private val audioRepository: AudioRepository
) {
    /**
     * Sets the same album art for multiple files.
     * @param filePaths List of file paths to update
     * @param albumArtBytes Album art bytes to set
     * @return Flow emitting progress
     */
    operator fun invoke(
        filePaths: List<String>,
        albumArtBytes: ByteArray
    ): Flow<BatchProgress> = flow {
        val totalFiles = filePaths.size
        var successCount = 0
        var failureCount = 0
        val startedAt = SystemClock.elapsedRealtime()
        Logger.i("Batch album art set started. files=$totalFiles", "BatchAlbumArt")

        filePaths.forEachIndexed { index, filePath ->
            emit(
                BatchProgress(
                    currentFile = index + 1,
                    totalFiles = totalFiles,
                    percentage = (index + 1).toFloat() / totalFiles,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )
            )

            val result = audioRepository.setAlbumArt(filePath, albumArtBytes)

            if (result.isSuccess) {
                successCount++
            } else {
                failureCount++
                Logger.w(
                    "Batch album art set failed file=$filePath reason=${result.exceptionOrNull()?.message ?: "unknown"}",
                    "BatchAlbumArt"
                )
            }
        }

        emit(
            BatchProgress(
                currentFile = totalFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
        )
        Logger.i(
            "Batch album art set finished. files=$totalFiles success=$successCount failed=$failureCount elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            "BatchAlbumArt"
        )
    }

    /**
     * Removes album art from multiple files.
     * @param filePaths List of file paths to update
     * @return Flow emitting progress
     */
    fun removeAlbumArt(filePaths: List<String>): Flow<BatchProgress> = flow {
        val totalFiles = filePaths.size
        var successCount = 0
        var failureCount = 0
        val startedAt = SystemClock.elapsedRealtime()
        Logger.i("Batch album art remove started. files=$totalFiles", "BatchAlbumArt")

        filePaths.forEachIndexed { index, filePath ->
            emit(
                BatchProgress(
                    currentFile = index + 1,
                    totalFiles = totalFiles,
                    percentage = (index + 1).toFloat() / totalFiles,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )
            )

            val result = audioRepository.removeAlbumArt(filePath)

            if (result.isSuccess) {
                successCount++
            } else {
                failureCount++
                Logger.w(
                    "Batch album art remove failed file=$filePath reason=${result.exceptionOrNull()?.message ?: "unknown"}",
                    "BatchAlbumArt"
                )
            }
        }

        emit(
            BatchProgress(
                currentFile = totalFiles,
                totalFiles = totalFiles,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
        )
        Logger.i(
            "Batch album art remove finished. files=$totalFiles success=$successCount failed=$failureCount elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            "BatchAlbumArt"
        )
    }
}

/**
 * Enum representing metadata fields that can be batch updated.
 */
enum class MetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    GENRE,
    COMPOSER,
    TRACK_NUMBER,
    DISC_NUMBER,
    ALBUM_ART;

    companion object {
        val ALL = values().toSet()
        val BASIC = setOf(TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, GENRE)
    }
}

/**
 * Data class representing batch operation progress.
 */
data class BatchProgress(
    val currentFile: Int,
    val totalFiles: Int,
    val percentage: Float,
    val currentFilePath: String,
    val status: com.voxly.domain.model.BatchStatus,
    val successCount: Int = 0,
    val failureCount: Int = 0
)
