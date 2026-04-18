package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * Slimmed ViewModel for the library screens.
 * Retains list state, search/filter coordination, selection state, and navigation events.
 * Scan, batch, and settings concerns have been moved to dedicated ViewModels.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val MAX_SCROLL_POSITIONS = 30
    }

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _currentDirectory = MutableStateFlow<String?>(null)
    val currentDirectory: StateFlow<String?> = _currentDirectory.asStateFlow()

    private val scrollPositions = LinkedHashMap<String, ScrollPosition>(MAX_SCROLL_POSITIONS, 0.75f, true)

    /**
     * Toggles selection of a file.
     */
    fun toggleFileSelection(filePath: String) {
        _selectedFiles.update {
            if (filePath in it) {
                it - filePath
            } else {
                it + filePath
            }
        }

        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.update {
                currentState.copy(
                    selectedCount = _selectedFiles.value.size
                )
            }
        }
    }

    /**
     * Selects all files.
     */
    fun selectAll() {
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _selectedFiles.update { currentState.files.map { it.path }.toSet() }
            _uiState.update {
                currentState.copy(
                    selectedCount = _selectedFiles.value.size
                )
            }
        }
    }

    fun selectFilePaths(filePaths: List<String>) {
        _selectedFiles.update { filePaths.toSet() }
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.update { currentState.copy(
                selectedCount = _selectedFiles.value.size
            ) }
        }
    }

    /**
     * Clears all file selections.
     */
    fun clearSelection() {
        _selectedFiles.update { emptySet() }
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.update { currentState.copy(selectedCount = 0) }
        }
    }

    /**
     * Gets selected file paths.
     */
    fun getSelectedFilePaths(): List<String> {
        return _selectedFiles.value.toList()
    }

    fun saveScrollPosition(listKey: String, index: Int, offset: Int) {
        scrollPositions[listKey] = ScrollPosition(index = index, offset = offset)
        while (scrollPositions.size > MAX_SCROLL_POSITIONS) {
            scrollPositions.keys.firstOrNull()?.let { scrollPositions.remove(it) } ?: break
        }
    }

    fun getScrollPosition(listKey: String): ScrollPosition {
        return scrollPositions[listKey] ?: ScrollPosition()
    }

    /**
     * Updates the UI state directly (used by screens to coordinate list state).
     */
    fun setUiState(state: FileBrowserUiState) {
        _uiState.value = state
    }

    /**
     * Sets the current directory path.
     */
    fun setCurrentDirectory(path: String?) {
        _currentDirectory.value = path
    }
}

data class SelectedDirectory(
    val uri: String,
    val path: String
)

data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

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
