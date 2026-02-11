package com.mp3tag.android.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.domain.model.AudioFile
import com.mp3tag.android.domain.model.AudioMetadata
import com.mp3tag.android.domain.repository.AudioRepository
import com.mp3tag.android.domain.repository.ReplayGainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the metadata editor screen.
 * Handles loading, editing, and saving audio file metadata.
 */
@HiltViewModel
class MetadataEditorViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = savedStateHandle.get<String>("filePath") ?: ""

    private val _uiState = MutableStateFlow<MetadataEditorUiState>(MetadataEditorUiState.Loading)
    val uiState: StateFlow<MetadataEditorUiState> = _uiState.asStateFlow()

    private val _editedMetadata = MutableStateFlow<AudioMetadata?>(null)
    val editedMetadata: StateFlow<AudioMetadata?> = _editedMetadata.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    init {
        loadAudioFile()
    }

    /**
     * Loads the audio file and its metadata.
     */
    private fun loadAudioFile() {
        viewModelScope.launch {
            _uiState.value = MetadataEditorUiState.Loading

            val result = audioRepository.getAudioFile(filePath)

            result.fold(
                onSuccess = { audioFile ->
                    _editedMetadata.value = audioFile.metadata
                    _uiState.value = MetadataEditorUiState.Success(
                        audioFile = audioFile,
                        editedMetadata = audioFile.metadata
                    )
                },
                onFailure = { error ->
                    _uiState.value = MetadataEditorUiState.Error(
                        error.message ?: "Failed to load audio file"
                    )
                }
            )
        }
    }

    /**
     * Updates a specific metadata field.
     * @param field The metadata field to update
     * @param value The new value
     */
    fun updateMetadataField(field: MetadataField, value: String) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = when (field) {
            MetadataField.TITLE -> currentMetadata.copy(title = value.takeIf { it.isNotBlank() })
            MetadataField.ARTIST -> currentMetadata.copy(artist = value.takeIf { it.isNotBlank() })
            MetadataField.ALBUM -> currentMetadata.copy(album = value.takeIf { it.isNotBlank() })
            MetadataField.ALBUM_ARTIST -> currentMetadata.copy(albumArtist = value.takeIf { it.isNotBlank() })
            MetadataField.YEAR -> currentMetadata.copy(year = value.takeIf { it.isNotBlank() })
            MetadataField.GENRE -> currentMetadata.copy(genre = value.takeIf { it.isNotBlank() })
            MetadataField.COMPOSER -> currentMetadata.copy(composer = value.takeIf { it.isNotBlank() })
            MetadataField.LYRICIST -> currentMetadata.copy(lyricist = value.takeIf { it.isNotBlank() })
            MetadataField.CONDUCTOR -> currentMetadata.copy(conductor = value.takeIf { it.isNotBlank() })
            MetadataField.COMMENT -> currentMetadata.copy(comment = value.takeIf { it.isNotBlank() })
            MetadataField.LYRICS -> currentMetadata.copy(lyrics = value)
        }

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        // Update UI state
        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the track number.
     * @param trackNumber The new track number
     * @param totalTracks Total tracks (optional)
     */
    fun updateTrackNumber(trackNumber: Int?, totalTracks: Int?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            trackNumber = trackNumber,
            totalTracks = totalTracks
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the disc number.
     * @param discNumber The new disc number
     * @param totalDiscs Total discs (optional)
     */
    fun updateDiscNumber(discNumber: Int?, totalDiscs: Int?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            discNumber = discNumber,
            totalDiscs = totalDiscs
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the album art.
     * @param albumArtBytes The new album art bytes
     */
    fun updateAlbumArt(albumArtBytes: ByteArray?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Saves the edited metadata to the file.
     */
    fun saveMetadata() {
        val metadataToSave = _editedMetadata.value ?: return

        viewModelScope.launch {
            _uiState.value = MetadataEditorUiState.Saving

            val result = audioRepository.updateMetadata(filePath, metadataToSave)

            result.fold(
                onSuccess = {
                    _hasUnsavedChanges.value = false
                    _saveResult.value = SaveResult.Success
                    val currentSuccessState = _uiState.value as? MetadataEditorUiState.Success
                    _uiState.value = currentSuccessState?.copy(
                        editedMetadata = metadataToSave
                    ) ?: MetadataEditorUiState.Success(
                        audioFile = AudioFile(
                            id = "",
                            path = filePath,
                            name = "",
                            size = 0,
                            duration = 0L,
                            format = "",
                            bitrate = 0,
                            sampleRate = 0,
                            channels = 0,
                            metadata = metadataToSave
                        ),
                        editedMetadata = metadataToSave
                    )
                },
                onFailure = { error ->
                    _saveResult.value = SaveResult.Error(error.message ?: "Failed to save")
                    val currentState = _uiState.value
                    if (currentState is MetadataEditorUiState.Saving) {
                        _uiState.value = MetadataEditorUiState.Error(
                            error.message ?: "Failed to save metadata"
                        )
                    }
                }
            )
        }
    }

    /**
     * Resets all changes and reloads the original metadata.
     */
    fun discardChanges() {
        viewModelScope.launch {
            val result = audioRepository.readMetadata(filePath)
            result.onSuccess { originalMetadata ->
                _editedMetadata.value = originalMetadata
                _hasUnsavedChanges.value = false
                val currentState = _uiState.value
                if (currentState is MetadataEditorUiState.Success) {
                    _uiState.value = currentState.copy(editedMetadata = originalMetadata)
                }
            }
        }
    }

    /**
     * Clears the save result after it has been handled.
     */
    fun clearSaveResult() {
        _saveResult.value = null
    }

    private fun metadataToStorageState(metadata: AudioMetadata): AudioMetadata {
        // Return a copy that represents the saved state
        return metadata.copy()
    }
}

/**
 * Sealed class representing metadata editor UI states.
 */
sealed class MetadataEditorUiState {
    data object Loading : MetadataEditorUiState()
    data object Saving : MetadataEditorUiState()
    data class Success(
        val audioFile: AudioFile,
        val editedMetadata: AudioMetadata
    ) : MetadataEditorUiState()
    data class Error(val message: String) : MetadataEditorUiState()
}

/**
 * Enum representing editable metadata fields.
 */
enum class MetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    GENRE,
    COMPOSER,
    LYRICIST,
    CONDUCTOR,
    COMMENT,
    LYRICS
}

/**
 * Sealed class representing save operation results.
 */
sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}
