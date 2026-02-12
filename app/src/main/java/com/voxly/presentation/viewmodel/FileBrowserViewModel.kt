package com.voxly.presentation.viewmodel

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.AudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the file browser screen.
 * Handles audio file scanning and selection.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    companion object {
        private const val TAG = "FileBrowserViewModel"
    }

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _currentDirectory = MutableStateFlow<String?>(null)
    val currentDirectory: StateFlow<String?> = _currentDirectory.asStateFlow()

    private val _selectedDirectories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val selectedDirectories: StateFlow<List<SelectedDirectory>> = _selectedDirectories.asStateFlow()

    private val _directoryFiles = MutableStateFlow<Map<String, List<AudioFile>>>(emptyMap())
    val directoryFiles: StateFlow<Map<String, List<AudioFile>>> = _directoryFiles.asStateFlow()

    private val _openedDirectoryUri = MutableStateFlow<String?>(null)
    val openedDirectoryUri: StateFlow<String?> = _openedDirectoryUri.asStateFlow()

    private var scanJob: Job? = null

    init {
        restoreSelectedDirectories()
    }

    /**
     * Loads all audio files from device storage.
     */
    fun loadAudioFiles() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (_selectedDirectories.value.isNotEmpty()) {
                scanSelectedDirectories(_selectedDirectories.value)
                return@launch
            }

            _uiState.value = FileBrowserUiState.Loading

            audioRepository.scanAudioFiles()
                .catch { e ->
                    if (e is CancellationException) return@catch
                    Log.e(TAG, "Global audio scan failed", e)
                    _uiState.value = FileBrowserUiState.Error(e.message ?: "Unknown error")
                }
                .collect { files ->
                    _directoryFiles.value = emptyMap()
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
        addDirectory(directoryUri)
    }

    /**
     * Adds one directory to the selected directory set and reloads.
     */
    fun addDirectory(directoryUri: Uri) {
        val filePath = getPathFromUri(directoryUri)
        if (filePath.isBlank()) {
            _uiState.value = FileBrowserUiState.Error("Invalid directory URI")
            return
        }

        val uriString = directoryUri.toString()
        val updatedDirectories = (_selectedDirectories.value + SelectedDirectory(
            uri = uriString,
            path = filePath
        )).distinctBy { it.uri }

        _selectedDirectories.value = updatedDirectories
        persistSelectedDirectories(updatedDirectories)
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanSelectedDirectories(updatedDirectories)
        }
    }

    /**
     * Removes one selected directory and reloads.
     */
    fun removeDirectory(directoryUri: String) {
        val updatedDirectories = _selectedDirectories.value.filterNot { it.uri == directoryUri }
        _selectedDirectories.value = updatedDirectories
        _directoryFiles.value = _directoryFiles.value - directoryUri
        if (_openedDirectoryUri.value == directoryUri) {
            _openedDirectoryUri.value = null
            clearSelection()
        }
        persistSelectedDirectories(updatedDirectories)
        loadAudioFiles()
    }

    /**
     * Clears selected directories and falls back to global scan.
     */
    fun clearDirectories() {
        _selectedDirectories.value = emptyList()
        _directoryFiles.value = emptyMap()
        _openedDirectoryUri.value = null
        clearSelection()
        persistSelectedDirectories(emptyList())
        loadAudioFiles()
    }

    fun openDirectory(directoryUri: String) {
        _openedDirectoryUri.value = directoryUri
        clearSelection()
    }

    fun closeOpenedDirectory() {
        _openedDirectoryUri.value = null
        clearSelection()
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

    fun selectFilePaths(filePaths: List<String>) {
        _selectedFiles.value = filePaths.toSet()
        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
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
        return runCatching {
            // Handle direct file URIs first.
            if (uri.scheme == "file") {
                return@runCatching uri.path.orEmpty()
            }

            if (uri.scheme != "content") {
                return@runCatching uri.path.orEmpty()
            }

            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId.startsWith("raw:")) {
                return@runCatching documentId.removePrefix("raw:")
            }

            val idParts = documentId.split(":", limit = 2)
            val volume = idParts.firstOrNull().orEmpty()
            val relativePath = idParts.getOrNull(1)?.trim('/').orEmpty()

            when {
                volume.equals("primary", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    if (relativePath.isEmpty()) externalRoot else "$externalRoot/$relativePath"
                }
                volume.equals("home", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    val documentsRoot = "$externalRoot/Documents"
                    if (relativePath.isEmpty()) documentsRoot else "$documentsRoot/$relativePath"
                }
                volume.isNotEmpty() -> {
                    if (relativePath.isEmpty()) "/storage/$volume" else "/storage/$volume/$relativePath"
                }
                else -> uri.path.orEmpty()
            }
        }.getOrElse {
            uri.path.orEmpty()
        }
    }

    private fun restoreSelectedDirectories() {
        viewModelScope.launch {
            val uris = settingsDataStore.selectedDirectoryUris.first()
            val restored = uris.mapNotNull { uriString ->
                val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
                val path = getPathFromUri(parsed)
                if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
            }
            _selectedDirectories.value = restored
            if (restored.isNotEmpty()) {
                loadAudioFiles()
            }
        }
    }

    private fun persistSelectedDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setSelectedDirectoryUris(directories.map { it.uri })
        }
    }

    private suspend fun scanSelectedDirectories(directories: List<SelectedDirectory>) {
        _uiState.value = FileBrowserUiState.Loading
        runCatching {
            var globalScannedFiles: List<AudioFile>? = null
            directories.associate { directory ->
                val filesForDirectory = if (directory.path.isBlank()) {
                    emptyList()
                } else {
                    val scannedByPath = audioRepository.scanAudioFiles(directory.path).first()
                    if (scannedByPath.isNotEmpty()) {
                        scannedByPath
                    } else {
                        val mediaStoreFiles = globalScannedFiles ?: audioRepository.scanAudioFiles().first()
                            .also { globalScannedFiles = it }
                        mediaStoreFiles.filter { audioFile ->
                            isInDirectory(directory.path, audioFile.path)
                        }
                    }
                }.distinctBy { it.path }
                directory.uri to filesForDirectory
            }
        }.onSuccess { filesByDirectory ->
            _directoryFiles.value = filesByDirectory
            _currentDirectory.value = directories.firstOrNull()?.path
            if (_openedDirectoryUri.value != null && _openedDirectoryUri.value !in filesByDirectory.keys) {
                _openedDirectoryUri.value = null
                clearSelection()
            }
            val mergedFiles = filesByDirectory.values.flatten().distinctBy { it.path }
            _uiState.value = if (mergedFiles.isEmpty()) {
                FileBrowserUiState.Empty
            } else {
                FileBrowserUiState.Success(
                    files = mergedFiles,
                    selectedCount = _selectedFiles.value.size
                )
            }
        }.onFailure { error ->
            if (error is CancellationException) {
                return@onFailure
            }
            Log.e(TAG, "Directory scan failed for ${directories.joinToString { it.path }}", error)
            _uiState.value = FileBrowserUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun isInDirectory(directoryPath: String, filePath: String): Boolean {
        val directoryCanonical = runCatching { File(directoryPath).canonicalPath }
            .getOrElse { File(directoryPath).absolutePath }
            .trimEnd(File.separatorChar)
        val fileCanonical = runCatching { File(filePath).canonicalPath }
            .getOrElse { File(filePath).absolutePath }
        return fileCanonical.startsWith("$directoryCanonical${File.separatorChar}")
    }
}

data class SelectedDirectory(
    val uri: String,
    val path: String
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
