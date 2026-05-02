package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.model.BatchResult
import com.voxly.domain.model.BatchStatus
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.usecase.BatchEngine
import com.voxly.domain.usecase.BatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for library batch operations.
 * Handles all batch metadata edits, renames, and online metadata fetching.
 */
@HiltViewModel
class LibraryBatchViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val batchEngine: BatchEngine<String>,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryBatchViewModel"
    }

    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing: StateFlow<Boolean> = _isBatchProcessing.asStateFlow()

    private val _batchProgress = MutableStateFlow<BatchProgress?>(null)
    val batchProgress: StateFlow<BatchProgress?> = _batchProgress.asStateFlow()

    private val _batchError = MutableSharedFlow<String>()
    val batchError: SharedFlow<String> = _batchError.asSharedFlow()

    private val _batchResult = MutableStateFlow<BatchResult?>(null)
    val batchResult: StateFlow<BatchResult?> = _batchResult.asStateFlow()

    private var batchJob: Job? = null

    /**
     * Generic batch execution template.
     * Cancels previous job, sets processing state, collects result, then requests a library refresh.
     */
    private fun executeBatch(
        items: List<String>,
        operation: suspend (String) -> Result<Unit>,
        itemName: (String) -> String = { it }
    ): Job {
        Timber.tag("Voxly").i("LibraryBatchViewModel batch started: itemCount=${items.size}")
        batchJob?.cancel()
        return viewModelScope.launch {
            _isBatchProcessing.update { true }
            try {
                batchEngine.execute(
                    items = items,
                    operation = operation,
                    itemName = itemName
                ).collect { result ->
                    _batchResult.update { result }
                }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("Batch operation cancelled")
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Batch operation failed")
            } finally {
                _isBatchProcessing.update { false }
                libraryDataHolder.requestRefresh(forceRefresh = false)
            }
        }.also { batchJob = it }
    }

    /**
     * Batch fetch online metadata for files.
     */
    fun batchFetchOnlineMetadata(
        filePaths: List<String>,
        options: com.voxly.presentation.screens.filebrowser.OnlineMetadataOptions
    ) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val currentMetadata = audioRepository.readMetadata(filePath).getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Failed to read metadata"))

                val searchTitle = currentMetadata.title ?: File(filePath).nameWithoutExtension
                val artistQuery = currentMetadata.artist

                val searchResult = onlineMetadataRepository.searchByTrack(searchTitle, artistQuery)
                if (searchResult.isFailure) {
                    return@executeBatch Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
                }

                val searchResults = searchResult.getOrNull()
                if (searchResults.isNullOrEmpty()) {
                    return@executeBatch Result.failure(Exception("No search results"))
                }

                val bestMatch = searchResults.first()
                val releaseDetailsResult = bestMatch.releaseId?.let {
                    onlineMetadataRepository.getReleaseDetails(it)
                }
                val releaseDetails = releaseDetailsResult?.getOrNull()

                val trackNumber = releaseDetails?.tracks?.find { track ->
                    track.title.equals(bestMatch.title, ignoreCase = true) ||
                    track.artist?.equals(bestMatch.artist, ignoreCase = true) == true
                }?.number

                val updatedMetadata = currentMetadata.copy(
                    title = if (options.overwriteExisting || currentMetadata.title.isNullOrBlank())
                        bestMatch.title else currentMetadata.title,
                    artist = if (options.overwriteExisting || currentMetadata.artist.isNullOrBlank())
                        bestMatch.artist else currentMetadata.artist,
                    album = if (options.overwriteExisting || currentMetadata.album.isNullOrBlank())
                        releaseDetails?.title ?: currentMetadata.album else currentMetadata.album,
                    year = if (options.overwriteExisting || currentMetadata.year == null)
                        releaseDetails?.year?.toString() else currentMetadata.year,
                    genre = if (options.overwriteExisting || currentMetadata.genre.isNullOrBlank())
                        releaseDetails?.genre ?: currentMetadata.genre else currentMetadata.genre,
                    trackNumber = if (options.overwriteExisting || currentMetadata.trackNumber == null)
                        trackNumber else currentMetadata.trackNumber
                )

                val result = audioRepository.updateMetadata(filePath, updatedMetadata)

                if (options.fetchAlbumArt && bestMatch.releaseId != null) {
                    try {
                        val coverArtResult = onlineMetadataRepository.getCoverArt(bestMatch.releaseId)
                        if (coverArtResult.isSuccess) {
                            coverArtResult.getOrNull()?.let { albumArtBytes ->
                                audioRepository.setAlbumArt(filePath, albumArtBytes)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to fetch album art for $filePath", e)
                    }
                }

                if (result.isSuccess) Result.success(Unit)
                else Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
            },
            itemName = { it }
        )
    }

    /**
     * Batch rename files based on pattern.
     */
    fun batchRenameFiles(filePaths: List<String>, pattern: String, startNumber: Int) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val index = filePaths.indexOf(filePath)
                val file = File(filePath)
                if (!file.exists()) {
                    return@executeBatch Result.failure(Exception("File does not exist"))
                }

                val metadata = audioRepository.readMetadata(filePath).getOrNull()
                var newName = pattern
                    .replace("{title}", metadata?.title ?: file.nameWithoutExtension)
                    .replace("{artist}", metadata?.artist ?: "Unknown")
                    .replace("{album}", metadata?.album ?: "Unknown")
                    .replace("{track}", (startNumber + index).toString().padStart(2, '0'))
                    .replace("{track00}", (startNumber + index).toString().padStart(2, '0'))
                    .replace("{track000}", (startNumber + index).toString().padStart(3, '0'))

                newName = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                newName = "$newName.${file.extension}"

                val newFile = File(file.parent, newName)
                if (file.renameTo(newFile)) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Rename failed"))
                }
            },
            itemName = { it }
        )
    }

    /**
     * Batch fix metadata (auto-correct common issues).
     */
    fun batchFixMetadata(
        filePaths: List<String>,
        options: com.voxly.presentation.screens.filebrowser.FixMetadataOptions
    ) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val metadataResult = audioRepository.readMetadata(filePath)
                if (metadataResult.isFailure) {
                    return@executeBatch Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                }

                val metadata = metadataResult.getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Read failed"))
                var updatedMetadata = metadata
                var hasChanges = false

                if (options.autoTitleCase) {
                    val newTitle = updatedMetadata.title?.toTitleCase()
                    val newArtist = updatedMetadata.artist?.toTitleCase()
                    val newAlbum = updatedMetadata.album?.toTitleCase()
                    updatedMetadata = updatedMetadata.copy(
                        title = newTitle ?: updatedMetadata.title,
                        artist = newArtist ?: updatedMetadata.artist,
                        album = newAlbum ?: updatedMetadata.album
                    )
                    if (newTitle != metadata.title || newArtist != metadata.artist || newAlbum != metadata.album) {
                        hasChanges = true
                    }
                }

                if (options.removeExtraSpaces) {
                    val newTitle = updatedMetadata.title?.trim()?.replace(Regex("\\s+"), " ")
                    val newArtist = updatedMetadata.artist?.trim()?.replace(Regex("\\s+"), " ")
                    val newAlbum = updatedMetadata.album?.trim()?.replace(Regex("\\s+"), " ")
                    updatedMetadata = updatedMetadata.copy(
                        title = newTitle ?: updatedMetadata.title,
                        artist = newArtist ?: updatedMetadata.artist,
                        album = newAlbum ?: updatedMetadata.album
                    )
                    if (newTitle != metadata.title || newArtist != metadata.artist || newAlbum != metadata.album) {
                        hasChanges = true
                    }
                }

                if (options.fixTrackNumbers) {
                    val trackNum = metadata.trackNumber
                    if (trackNum != null && trackNum < 1) {
                        updatedMetadata = updatedMetadata.copy(trackNumber = null)
                        hasChanges = true
                    }
                }

                if (hasChanges) {
                    val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                    if (result.isSuccess) Result.success(Unit)
                    else Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                } else {
                    Result.success(Unit)
                }
            },
            itemName = { it }
        )
    }

    /**
     * Batch set a field to the same value for all selected files.
     */
    fun batchSetUnifiedField(filePaths: List<String>, field: String, value: String) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val metadataResult = audioRepository.readMetadata(filePath)
                if (metadataResult.isFailure) {
                    return@executeBatch Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                }

                val metadata = metadataResult.getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Read failed"))
                val updatedMetadata = when (field) {
                    "artist" -> metadata.copy(artist = value)
                    "album" -> metadata.copy(album = value)
                    "albumArtist" -> metadata.copy(albumArtist = value)
                    "year" -> metadata.copy(year = value)
                    "genre" -> metadata.copy(genre = value)
                    "composer" -> metadata.copy(composer = value)
                    else -> metadata
                }

                val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                if (result.isSuccess) Result.success(Unit)
                else Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
            },
            itemName = { it }
        )
    }

    /**
     * Batch find and replace text in metadata fields.
     */
    fun batchReplaceText(
        filePaths: List<String>,
        field: String,
        searchText: String,
        replaceText: String,
        useRegex: Boolean
    ) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val metadataResult = audioRepository.readMetadata(filePath)
                if (metadataResult.isFailure) {
                    return@executeBatch Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                }

                val metadata = metadataResult.getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Read failed"))
                var updatedMetadata = metadata
                var hasChanges = false

                val replaceFunction: (String?) -> String? = { originalValue ->
                    if (originalValue != null) {
                        if (useRegex) {
                            originalValue.replace(Regex(searchText), replaceText)
                        } else {
                            originalValue.replace(searchText, replaceText)
                        }.also { if (it != originalValue) hasChanges = true }
                    } else null
                }

                when (field) {
                    "title" -> updatedMetadata = metadata.copy(title = replaceFunction(metadata.title))
                    "artist" -> updatedMetadata = metadata.copy(artist = replaceFunction(metadata.artist))
                    "album" -> updatedMetadata = metadata.copy(album = replaceFunction(metadata.album))
                    "all" -> {
                        updatedMetadata = metadata.copy(
                            title = replaceFunction(metadata.title),
                            artist = replaceFunction(metadata.artist),
                            album = replaceFunction(metadata.album),
                            albumArtist = replaceFunction(metadata.albumArtist),
                            genre = replaceFunction(metadata.genre),
                            composer = replaceFunction(metadata.composer)
                        )
                    }
                }

                if (hasChanges) {
                    val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                    if (result.isSuccess) Result.success(Unit)
                    else Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                } else {
                    Result.success(Unit)
                }
            },
            itemName = { it }
        )
    }

    /**
     * Batch auto-number tracks with sequential numbers.
     */
    fun batchAutoNumberTracks(
        filePaths: List<String>,
        startNumber: Int,
        step: Int,
        totalTracks: Int?
    ) {
        executeBatch(
            items = filePaths,
            operation = { filePath ->
                val index = filePaths.indexOf(filePath)
                val metadataResult = audioRepository.readMetadata(filePath)
                if (metadataResult.isFailure) {
                    return@executeBatch Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                }

                val metadata = metadataResult.getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Read failed"))
                val trackNumber = startNumber + index * step

                val updatedMetadata = metadata.copy(
                    trackNumber = trackNumber,
                    totalTracks = totalTracks
                )

                val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                if (result.isSuccess) Result.success(Unit)
                else Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
            },
            itemName = { it }
        )
    }

    /**
     * Cancel current batch operation.
     */
    fun cancelBatchOperation() {
        batchJob?.cancel()
        _isBatchProcessing.update { false }
        _batchResult.update { it?.copy(status = BatchStatus.CANCELLED) }
    }

    /**
     * Reset batch operation state.
     */
    fun resetBatchOperation() {
        _isBatchProcessing.update { false }
        _batchProgress.update { null }
        _batchResult.update { null }
    }

    /**
     * Clear batch error.
     */
    fun clearBatchError() {
    }

    /**
     * Retry failed items from the last batch operation.
     */
    fun retryFailedItems() {
        val failed = _batchResult.value?.failedItems ?: return
        if (failed.isEmpty()) return

        executeBatch(
            items = failed.map { it.filePath },
            operation = { filePath ->
                val currentMetadata = audioRepository.readMetadata(filePath).getOrNull()
                    ?: return@executeBatch Result.failure(Exception("Failed to read metadata"))

                val searchTitle = currentMetadata.title ?: File(filePath).nameWithoutExtension
                val artistQuery = currentMetadata.artist
                val searchResult = onlineMetadataRepository.searchByTrack(searchTitle, artistQuery)

                if (searchResult.isFailure) {
                    return@executeBatch Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
                }

                val searchResults = searchResult.getOrNull()
                if (searchResults.isNullOrEmpty()) {
                    return@executeBatch Result.failure(Exception("No search results"))
                }

                val bestMatch = searchResults.first()
                val releaseDetailsResult = bestMatch.releaseId?.let {
                    onlineMetadataRepository.getReleaseDetails(it)
                }
                val releaseDetails = releaseDetailsResult?.getOrNull()

                val updatedMetadata = currentMetadata.copy(
                    title = bestMatch.title,
                    artist = bestMatch.artist,
                    album = releaseDetails?.title ?: currentMetadata.album,
                    year = releaseDetails?.year?.toString() ?: currentMetadata.year,
                    genre = releaseDetails?.genre ?: currentMetadata.genre
                )
                audioRepository.updateMetadata(filePath, updatedMetadata)
                Result.success(Unit)
            },
            itemName = { it }
        )
    }

    private fun String.toTitleCase(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
