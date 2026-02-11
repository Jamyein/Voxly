package com.mp3tag.android.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp3tag.android.domain.model.AudioFile
import com.mp3tag.android.domain.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the file browser screen.
 * Handles audio file scanning and selection.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _currentDirectory = MutableStateFlow<String?>(null)
    val currentDirectory: StateFlow<String?> = _currentDirectory.asStateFlow()

    /**
     * Loads all audio files from device storage.
     */
    fun loadAudioFiles() {
        viewModelScope.launch {
            _uiState.value = FileBrowserUiState.Loading

            audioRepository.scanAudioFiles()
                .catch { e ->
                    _uiState.value = FileBrowserUiState.Error(e.message ?: "Unknown error")
                }
                .collect { files ->
                    _uiState.value = if (files.isEmpty()) {
                        FileBrowserUiState.Empty
                    } else {
                        FileBrowserUiState.Success(
                            files = files,
                            selectedCount = _selectedFiles.value.size
                        )
                    }
                }
        }
    }

    /**
     * Scans audio files from a specific directory.
     * @param directoryUri URI of the directory to scan
     */
    fun loadFromDirectory(directoryUri: Uri) {
        viewModelScope.launch {
            _uiState.value = FileBrowserUiState.Loading

            // Convert URI to file path
            val filePath = getPathFromUri(directoryUri)

            audioRepository.scanAudioFiles(filePath)
                .catch { e ->
                    _uiState.value = FileBrowserUiState.Error(e.message ?: "Unknown error")
                }
                .collect { files ->
                    _currentDirectory.value = filePath
                    _uiState.value = if (files.isEmpty()) {
                        FileBrowserUiState.Empty
                    } else {
                        FileBrowserUiState.Success(
                            files = files,
                            selectedCount = _selectedFiles.value.size
                        )
                    }
                }
        }
    }

    /**
     * Toggles selection of a file.
     * @param filePath Path of the file to toggle
     */
    fun toggleFileSelection(filePath: String) {
        _selectedFiles.value = if (filePath in _selectedFiles.value) {
            _selectedFiles.value - filePath
        } else {
            _selectedFiles.value + filePath
        }

        // Update UI state with new selection count
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.value = currentState.copy(
                selectedCount = _selectedFiles.value.size
            )
        }
    }

    /**
     * Selects all files.
     */
    fun selectAll() {
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _selectedFiles.value = currentState.files.map { it.path }.toSet()
            _uiState.value = currentState.copy(
                selectedCount = _selectedFiles.value.size
            )
        }
    }

    /**
     * Clears all file selections.
     */
    fun clearSelection() {
        _selectedFiles.value = emptySet()
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.value = currentState.copy(selectedCount = 0)
        }
    }

    /**
     * Gets selected file paths.
     */
    fun getSelectedFilePaths(): List<String> {
        return _selectedFiles.value.toList()
    }

    /**
     * Converts a content URI to a file path.
     */
    private fun getPathFromUri(uri: Uri): String {
        // For simplicity, we'll use a basic implementation
        // In production, you'd want to handle different URI schemes properly
        return uri.path ?: ""
    }
}

/**
 * Sealed class representing file browser UI states.
 */
sealed class FileBrowserUiState {
    data object Loading : FileBrowserUiState()
    data object Empty : FileBrowserUiState()
    data class Success(
        val files: List<AudioFile>,
        val selectedCount: Int = 0
    ) : FileBrowserUiState()
    data class Error(val message: String) : FileBrowserUiState()
}
