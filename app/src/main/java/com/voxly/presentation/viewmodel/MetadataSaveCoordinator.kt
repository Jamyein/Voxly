package com.voxly.presentation.viewmodel

import android.content.Context
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.ChangeSource
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.usecase.SaveMetadataResult
import com.voxly.domain.usecase.SaveMetadataUseCase
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class MetadataSaveCoordinatorResult {
    data class Success(
        val metadataToSave: AudioMetadata,
        val preservedRg: ReplayGainInfo?
    ) : MetadataSaveCoordinatorResult()
    data class RecoverableError(val message: String) : MetadataSaveCoordinatorResult()
    data class Error(
        val message: String,
        val requiresReauthorization: Boolean = false
    ) : MetadataSaveCoordinatorResult()
}

@Singleton
class MetadataSaveCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveMetadataUseCase: SaveMetadataUseCase,
    private val replayGainRepository: ReplayGainRepository,
    private val libraryDataHolder: LibraryDataHolder,
    private val musicLibraryCache: MusicLibraryCache,
    private val audioFileScanner: AudioFileScanner,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    private val TAG = "MetadataSaveCoordinator"

    suspend fun save(
        filePath: String,
        baseMetadata: AudioMetadata,
        originalMetadata: AudioMetadata?,
        pendingReplayGainInfo: ReplayGainInfo?,
        currentSuccessAudioFile: AudioFile?,
    ): MetadataSaveCoordinatorResult {
        val preservedRg: ReplayGainInfo? = pendingReplayGainInfo
            ?: replayGainRepository.readReplayGain(filePath).getOrNull()

        val metadataToSave = if (preservedRg != null) {
            val rgCustomFields = buildMap {
                put("REPLAYGAIN_TRACK_GAIN", String.format("%.2f dB", preservedRg.trackGain))
                put("REPLAYGAIN_TRACK_PEAK", String.format("%.6f", preservedRg.trackPeak))
                preservedRg.albumGain?.let { put("REPLAYGAIN_ALBUM_GAIN", String.format("%.2f dB", it)) }
                preservedRg.albumPeak?.let { put("REPLAYGAIN_ALBUM_PEAK", String.format("%.6f", it)) }
                preservedRg.trackLoudness?.let { put("REPLAYGAIN_TRACK_LOUDNESS", String.format("%.2f LUFS", it)) }
                preservedRg.albumLoudness?.let { put("REPLAYGAIN_ALBUM_LOUDNESS", String.format("%.2f LUFS", it)) }
                preservedRg.trackRange?.let { put("REPLAYGAIN_TRACK_RANGE", String.format("%.2f LU", it)) }
                preservedRg.albumRange?.let { put("REPLAYGAIN_ALBUM_RANGE", String.format("%.2f LU", it)) }
                put("REPLAYGAIN_REFERENCE_LOUDNESS", String.format("%.1f LUFS", preservedRg.referenceLoudness))
            }
            baseMetadata.copy(customFields = baseMetadata.customFields + rgCustomFields)
        } else {
            baseMetadata
        }

        var finalResult: MetadataSaveCoordinatorResult? = null

        saveMetadataUseCase(
            filePath = filePath,
            originalMetadata = originalMetadata ?: metadataToSave,
            editedMetadata = metadataToSave
        ).collect { result ->
            finalResult = when (result) {
                is SaveMetadataResult.Success -> {
                    if (pendingReplayGainInfo != null) {
                        val rgSuccess = replayGainRepository.saveReplayGain(filePath, pendingReplayGainInfo).isSuccess
                        if (!rgSuccess) {
                            Timber.w("Save replaygain failed file=$filePath", TAG)
                        }
                    }

                    applicationScope.launch {
                        libraryDataHolder.requestSingleFileSync(filePath, source = ChangeSource.FILE_EDIT)
                        musicLibraryCache.markFileAsEditedByUser(filePath)

                        val correctAlbumId = audioFileScanner.queryMediaStoreAlbumId(filePath)
                        val oldAlbumId = currentSuccessAudioFile?.mediaStoreAlbumId

                        correctAlbumId?.let { albumId ->
                            CoverUriProvider.invalidateAlbumId(albumId)
                        }
                        if (oldAlbumId != null && oldAlbumId != correctAlbumId) {
                            CoverUriProvider.invalidateAlbumId(oldAlbumId)
                        }

                        CoverUriProvider.invalidateFilePath(filePath)
                        SingletonImageLoader.get(context).memoryCache?.clear()
                        SingletonImageLoader.get(context).diskCache?.clear()
                    }

                    MetadataSaveCoordinatorResult.Success(
                        metadataToSave = metadataToSave,
                        preservedRg = preservedRg
                    )
                }
                is SaveMetadataResult.RecoverableError -> {
                    MetadataSaveCoordinatorResult.RecoverableError(message = result.message)
                }
                is SaveMetadataResult.Error -> {
                    val requiresReauthorization = result.message.contains("SAF write permission") ||
                        result.message.contains("Permission denied") ||
                        result.message.contains("EACCES") ||
                        result.message.contains("write permission")

                    MetadataSaveCoordinatorResult.Error(
                        message = result.message,
                        requiresReauthorization = requiresReauthorization
                    )
                }
            }
        }

        return finalResult ?: MetadataSaveCoordinatorResult.Error("Save completed with no result")
    }
}
