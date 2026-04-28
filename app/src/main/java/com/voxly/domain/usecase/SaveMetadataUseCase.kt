package com.voxly.domain.usecase

import timber.log.Timber
import com.voxly.data.local.metadata.RecoverableMediaStoreException
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.RecentEditsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for saving audio file metadata.
 * Handles SAF (Storage Access Framework) and MediaStore permissions.
 */
class SaveMetadataUseCase @Inject constructor(
    private val audioRepository: AudioRepository,
    private val safWriteAccessService: SafWriteAccessService,
    private val recentEditsRepository: RecentEditsRepository
) {
    private val TAG = "SaveMetadata"

    /**
     * Saves metadata to an audio file.
     * Handles permission requests for SAF and MediaStore.
     *
     * @param filePath Path to the audio file
     * @param originalMetadata Original metadata (for undo/history)
     * @param editedMetadata New metadata to save
     * @return Flow emitting save progress and result
     */
    suspend operator fun invoke(
        filePath: String,
        originalMetadata: AudioMetadata,
        editedMetadata: AudioMetadata
    ): Flow<SaveMetadataResult> = kotlinx.coroutines.flow.flow {
        Timber.tag("Voxly").i("SaveMetadataUseCase saving: filePath=$filePath")

        val result = audioRepository.updateMetadata(filePath, editedMetadata)

        result.fold(
            onSuccess = {
                Timber.tag("Voxly").i("SaveMetadataUseCase saved successfully")
                recentEditsRepository.addRecentEdit(filePath, originalMetadata, editedMetadata)
                emit(SaveMetadataResult.Success)
            },
            onFailure = { error ->
                Timber.e(TAG, "Save failed: ${error.message}")
                when (error) {
                    is RecoverableMediaStoreException -> {
                        emit(SaveMetadataResult.RecoverableError(
                            message = error.message ?: "Permission required",
                            intentSender = error.intentSender
                        ))
                    }
                    else -> {
                        emit(SaveMetadataResult.Error(error.message ?: "Save failed"))
                    }
                }
            }
        )
    }

    /**
     * Saves album art to an audio file.
     * @param filePath Path to the audio file
     * @param albumArtBytes Album art bytes to save
     * @return Result indicating success or failure
     */
    suspend fun saveAlbumArt(
        filePath: String,
        albumArtBytes: ByteArray
    ): Result<Unit> {
        Timber.d(TAG, "Saving album art: $filePath")
        return audioRepository.setAlbumArt(filePath, albumArtBytes)
            .onSuccess {
                Timber.d(TAG, "Album art saved successfully")
            }
            .onFailure { error ->
                Timber.e(TAG, "Failed to save album art: ${error.message}")
            }
    }

    /**
     * Removes album art from an audio file.
     * @param filePath Path to the audio file
     * @return Result indicating success or failure
     */
    suspend fun removeAlbumArt(filePath: String): Result<Unit> {
        Timber.d(TAG, "Removing album art: $filePath")
        return audioRepository.removeAlbumArt(filePath)
            .onSuccess {
                Timber.d(TAG, "Album art removed successfully")
            }
            .onFailure { error ->
                Timber.e(TAG, "Failed to remove album art: ${error.message}")
            }
    }

    /**
     * Extracts album art from an audio file.
     * @param filePath Path to the audio file
     * @return Result containing album art bytes or null
     */
    suspend fun extractAlbumArt(filePath: String): Result<ByteArray?> {
        return audioRepository.extractAlbumArt(filePath)
    }
}

/**
 * Sealed class representing save metadata results.
 */
sealed class SaveMetadataResult {
    data object Success : SaveMetadataResult()
    data class Error(val message: String) : SaveMetadataResult()
    data class RecoverableError(
        val message: String,
        val intentSender: android.content.IntentSender?
    ) : SaveMetadataResult()
}
