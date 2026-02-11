package com.mp3tag.android.domain.usecase

import com.mp3tag.android.domain.model.AudioMetadata
import com.mp3tag.android.domain.repository.AudioRepository
import com.mp3tag.android.domain.repository.ReplayGainRepository
import com.mp3tag.android.domain.repository.ScanQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for batch editing metadata across multiple files.
 */
class BatchEditMetadataUseCase @Inject constructor(
    private val audioRepository: AudioRepository
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
        val totalFiles = filePaths.size
        var processedFiles = 0
        var successCount = 0
        var failureCount = 0

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

            try {
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
                        successCount++
                    } else {
                        failureCount++
                    }
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                failureCount++
            }

            processedFiles++
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
}

/**
 * Use case for batch ReplayGain scanning.
 */
class BatchReplayGainUseCase @Inject constructor(
    private val replayGainRepository: ReplayGainRepository
) {
    /**
     * Scans multiple files for ReplayGain.
     * @param filePaths List of file paths to scan
     * @param scanQuality Quality level for scanning
     * @return Flow emitting scan progress
     */
    operator fun invoke(
        filePaths: List<String>,
        scanQuality: ScanQuality = ScanQuality.NORMAL
    ): Flow<com.mp3tag.android.domain.repository.ScanProgress> {
        return replayGainRepository.scanReplayGain(filePaths, scanQuality)
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
    val status: BatchStatus,
    val successCount: Int = 0,
    val failureCount: Int = 0
)

/**
 * Enum representing batch operation status.
 */
enum class BatchStatus {
    PROCESSING,
    COMPLETED,
    CANCELLED
}
