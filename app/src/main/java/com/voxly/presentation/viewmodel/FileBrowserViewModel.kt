package com.voxly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.data.repository.AlbumCacheRepository
import com.voxly.data.repository.ArtistCacheRepository
import com.voxly.data.repository.ArtistGroup as RepoArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.usecase.BatchProgress
import com.voxly.domain.usecase.BatchStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the file browser screen.
 * Handles audio file scanning, selection, and batch operations.
 */
@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRepository: AudioRepository,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val settingsDataStore: SettingsDataStore,
    private val appViewModel: AppViewModel,
    private val safWriteAccessService: SafWriteAccessService,
    private val albumCacheRepository: AlbumCacheRepository,
    private val artistCacheRepository: ArtistCacheRepository
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

    // Aggregated data for tabs
    private val _allAudios = MutableStateFlow<List<AudioFile>>(emptyList())
    val allAudios: StateFlow<List<AudioFile>> = _allAudios.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    /**
     * Cache an album to the repository for instant loading in AlbumDetailScreen.
     */
    fun cacheAlbum(album: AlbumGroup) {
        albumCacheRepository.cacheAlbum(album)
    }

    // Batch operation states
    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing: StateFlow<Boolean> = _isBatchProcessing.asStateFlow()

    private val _batchProgress = MutableStateFlow<BatchProgress?>(null)
    val batchProgress: StateFlow<BatchProgress?> = _batchProgress.asStateFlow()

    private val _batchError = MutableStateFlow<String?>(null)
    val batchError: StateFlow<String?> = _batchError.asStateFlow()

    private var scanJob: Job? = null
    private var batchJob: Job? = null
    private var cachedGlobalFiles: List<AudioFile>? = null
    private val scrollPositions = mutableMapOf<String, ScrollPosition>()

    // Scan filter settings - observe changes to trigger auto-refresh
    private val whitelistEnabled = settingsDataStore.whitelistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val blacklistEnabled = settingsDataStore.blacklistEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val minDurationFilterEnabled = settingsDataStore.minDurationFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val minDurationFilterThresholdMs = settingsDataStore.minDurationFilterThresholdMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60000)
    private val selectedDirectoryUris = settingsDataStore.selectedDirectoryUris
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val blacklistDirectoryUris = settingsDataStore.blacklistDirectoryUris
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Track previous settings to detect changes
    private var lastWhitelistEnabled = false
    private var lastBlacklistEnabled = false
    private var lastMinDurationFilterEnabled = false
    private var lastMinDurationFilterThresholdMs = 60000
    private var lastSelectedDirectoryUris = listOf<String>()
    private var lastBlacklistDirectoryUris = listOf<String>()

    init {
        restoreSelectedDirectories()
        observeScanSettingsChanges()
        // Also listen to app-level refresh events from AppViewModel
        viewModelScope.launch {
            appViewModel.libraryRefreshEvent.collect { _ ->
                Log.d(TAG, "Received library refresh event from AppViewModel")
                loadAudioFiles(forceRefresh = true)
            }
        }
    }

    /**
     * Loads all audio files from device storage.
     */
    fun loadAudioFiles(forceRefresh: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            syncSelectedDirectoriesFromStorage()

                if (_selectedDirectories.value.isNotEmpty()) {
                    scanSelectedDirectories(_selectedDirectories.value, forceRefresh)
                    return@launch
                }

                // Check cache BEFORE setting Loading state to avoid flash
                // Check database cache first (persisted, survives app restart)
                if (!forceRefresh && audioRepository.hasCachedData()) {
                    val cachedFiles = audioRepository.getCachedAudioFiles().first()
                    if (cachedFiles.isNotEmpty()) {
                        cachedGlobalFiles = cachedFiles
                        _directoryFiles.value = emptyMap()
                        _uiState.value = FileBrowserUiState.Success(
                            files = cachedFiles,
                            selectedCount = _selectedFiles.value.size
                        )
                        Log.d(TAG, "Loaded ${cachedFiles.size} files from cache")
                        return@launch
                    }
                }

                // Check in-memory cache
                if (!forceRefresh) {
                    cachedGlobalFiles?.let { files ->
                        _directoryFiles.value = emptyMap()
                        _uiState.value = if (files.isEmpty()) {
                            FileBrowserUiState.Empty
                        } else {
                            FileBrowserUiState.Success(
                                files = files,
                                selectedCount = _selectedFiles.value.size
                            )
                        }
                        return@launch
                    }
                }

                // Keep current list during non-forced refresh to avoid UI flicker on resume.
                if (forceRefresh || _uiState.value !is FileBrowserUiState.Success) {
                    _uiState.value = FileBrowserUiState.Loading
                }
                
                // No cache available - perform full scan
                runCatching {
                    audioRepository.scanAudioFiles(forceRefresh = forceRefresh).last()
                }
                    .onSuccess { files ->
                        cachedGlobalFiles = files
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
                    .onFailure { e ->
                        if (e is CancellationException) return@onFailure
                        Log.e(TAG, "Global audio scan failed", e)
                        _uiState.value = FileBrowserUiState.Error(e.message ?: "Unknown error")
                    }
        }
    }

    /**
     * Observes scan settings changes and triggers auto-refresh when relevant settings change.
     * This includes whitelist mode, blacklist mode, duration filter, and directory changes.
     */
    private fun observeScanSettingsChanges() {
        viewModelScope.launch {
            // Observe whitelist enabled changes
            launch {
                whitelistEnabled.collect { enabled ->
                    if (lastWhitelistEnabled != enabled) {
                        lastWhitelistEnabled = enabled
                        Log.d(TAG, "Whitelist enabled changed to: $enabled, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }

            // Observe blacklist enabled changes
            launch {
                blacklistEnabled.collect { enabled ->
                    if (lastBlacklistEnabled != enabled) {
                        lastBlacklistEnabled = enabled
                        Log.d(TAG, "Blacklist enabled changed to: $enabled, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }

            // Observe min duration filter enabled changes
            launch {
                minDurationFilterEnabled.collect { enabled ->
                    if (lastMinDurationFilterEnabled != enabled) {
                        lastMinDurationFilterEnabled = enabled
                        Log.d(TAG, "Min duration filter enabled changed to: $enabled, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }

            // Observe min duration filter threshold changes
            launch {
                minDurationFilterThresholdMs.collect { threshold ->
                    if (lastMinDurationFilterThresholdMs != threshold) {
                        lastMinDurationFilterThresholdMs = threshold
                        Log.d(TAG, "Min duration filter threshold changed to: $threshold, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }

            // Observe whitelist directory changes
            launch {
                selectedDirectoryUris.collect { uris ->
                    if (lastSelectedDirectoryUris != uris) {
                        lastSelectedDirectoryUris = uris
                        Log.d(TAG, "Selected directories changed, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }

            // Observe blacklist directory changes
            launch {
                blacklistDirectoryUris.collect { uris ->
                    if (lastBlacklistDirectoryUris != uris) {
                        lastBlacklistDirectoryUris = uris
                        Log.d(TAG, "Blacklist directories changed, triggering auto-refresh")
                        loadAudioFiles(forceRefresh = true)
                    }
                }
            }
        }
    }

    private suspend fun syncSelectedDirectoriesFromStorage() {
        val uris = settingsDataStore.selectedDirectoryUris.first()
        val restored = uris.mapNotNull { uriString ->
            val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@mapNotNull null
            val path = getPathFromUri(parsed)
            if (path.isBlank()) null else SelectedDirectory(uri = uriString, path = path)
        }
        if (restored.map { it.uri } != _selectedDirectories.value.map { it.uri }) {
            _selectedDirectories.value = restored
            _directoryFiles.value = _directoryFiles.value.filterKeys { key ->
                restored.any { it.uri == key }
            }
            if (_openedDirectoryUri.value != null && restored.none { it.uri == _openedDirectoryUri.value }) {
                _openedDirectoryUri.value = null
                clearSelection()
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

    fun renameSingleFile(
        filePath: String,
        targetName: String,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)

                    val sanitizedName = targetName.trim()
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (sanitizedName.isBlank()) {
                        return@runCatching false to "File name cannot be empty"
                    }

                    val extension = file.extension
                    val finalName = if (
                        extension.isNotBlank() &&
                        !sanitizedName.endsWith(".$extension", ignoreCase = true)
                    ) {
                        "$sanitizedName.$extension"
                    } else {
                        sanitizedName
                    }

                    if (file.name.equals(finalName, ignoreCase = true)) {
                        return@runCatching true to null
                    }

                    val targetFile = File(file.parentFile, finalName)
                    if (targetFile.absolutePath.equals(file.absolutePath, ignoreCase = true)) {
                        return@runCatching true to null
                    }
                    if (targetFile.exists()) {
                        return@runCatching false to "Target file already exists"
                    }

                    if (file.exists() && file.renameTo(targetFile)) {
                        return@runCatching true to null
                    }

                    val safResult = renameFileViaSaf(filePath, finalName)
                    safResult.fold(
                        onSuccess = { true to null },
                        onFailure = { false to (it.message ?: "Failed to rename file") }
                    )
                }.getOrElse { false to (it.message ?: "Failed to rename file") }
            }

            onComplete(result.first, result.second)
            if (result.first) {
                _selectedFiles.value = _selectedFiles.value - filePath
                loadAudioFiles(forceRefresh = true)
            }
        }
    }

    fun deleteSingleFile(
        filePath: String,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)

                    if (file.exists() && file.delete()) {
                        return@runCatching true to null
                    }

                    val safResult = deleteFileViaSaf(filePath)
                    safResult.fold(
                        onSuccess = { true to null },
                        onFailure = { false to (it.message ?: "Failed to delete file") }
                    )
                }.getOrElse { false to (it.message ?: "Failed to delete file") }
            }

            onComplete(result.first, result.second)
            if (result.first) {
                _selectedFiles.value = _selectedFiles.value - filePath
                loadAudioFiles(forceRefresh = true)
            }
        }
    }

    private fun renameFileViaSaf(filePath: String, targetDisplayName: String): Result<Unit> {
        val targetDocUri = safWriteAccessService.resolveWritableDocumentUri(filePath)
            ?: return Result.failure(IllegalStateException("No SAF write permission for this file"))

        return runCatching {
            val renamed = DocumentsContract.renameDocument(
                context.contentResolver,
                targetDocUri,
                targetDisplayName
            )
            if (renamed == null) {
                throw IllegalStateException("Failed to rename file")
            }
            Unit
        }
    }

    private fun deleteFileViaSaf(filePath: String): Result<Unit> {
        val targetDocUri = safWriteAccessService.resolveWritableDocumentUri(filePath)
            ?: return Result.failure(IllegalStateException("No SAF write permission for this file"))

        return runCatching {
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, targetDocUri)
            if (!deleted) {
                throw IllegalStateException("Failed to delete file")
            }
            Unit
        }
    }

    // ==================== Batch Operations ====================

    /**
     * Batch fetch online metadata for files.
     */
    fun batchFetchOnlineMetadata(
        filePaths: List<String>,
        options: com.voxly.presentation.screens.filebrowser.OnlineMetadataOptions
    ) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    // Read current metadata to get search query
                    val currentMetadata = audioRepository.readMetadata(filePath).getOrNull()
                    if (currentMetadata != null) {
                        val searchTitle = currentMetadata.title ?: File(filePath).nameWithoutExtension
                        val artistQuery = currentMetadata.artist

                        // Search online metadata
                        val searchResult = onlineMetadataRepository.searchByTrack(searchTitle, artistQuery)
                        
                        if (searchResult.isSuccess) {
                            val searchResults = searchResult.getOrNull()
                            if (!searchResults.isNullOrEmpty()) {
                                val bestMatch = searchResults.first()
                                
                                // Get release details for more complete metadata
                                val releaseDetailsResult = bestMatch.releaseId?.let {
                                    onlineMetadataRepository.getReleaseDetails(it)
                                }
                                
                                val releaseDetails = releaseDetailsResult?.getOrNull()
                                
                                // Try to find track number from release details
                                val trackNumber = releaseDetails?.tracks?.find { track ->
                                    track.title.equals(bestMatch.title, ignoreCase = true) ||
                                    track.artist?.equals(bestMatch.artist, ignoreCase = true) == true
                                }?.number
                                
                                // Only update if overwrite is enabled or field is empty
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

                                // Update metadata
                                val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                                
                                // Fetch album art if requested
                                if (options.fetchAlbumArt && bestMatch.releaseId != null) {
                                    try {
                                        val coverArtResult = onlineMetadataRepository.getCoverArt(bestMatch.releaseId)
                                        if (coverArtResult.isSuccess) {
                                            coverArtResult.getOrNull()?.let { albumArtBytes ->
                                                audioRepository.setAlbumArt(filePath, albumArtBytes)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to fetch album art for $filePath", e)
                                    }
                                }

                                if (result.isSuccess) {
                                    successCount++
                                } else {
                                    failureCount++
                                }
                            } else {
                                failureCount++
                            }
                        } else {
                            failureCount++
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false
            
            // Refresh files to show updated metadata
            loadAudioFiles(forceRefresh = false)
        }
    }

    /**
     * Batch rename files based on pattern.
     */
    fun batchRenameFiles(filePaths: List<String>, pattern: String, startNumber: Int) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        failureCount++
                        return@forEachIndexed
                    }

                    // Read metadata for pattern substitution
                    val metadata = audioRepository.readMetadata(filePath).getOrNull()
                    
                    // Generate new filename
                    var newName = pattern
                        .replace("{title}", metadata?.title ?: file.nameWithoutExtension)
                        .replace("{artist}", metadata?.artist ?: "Unknown")
                        .replace("{album}", metadata?.album ?: "Unknown")
                        .replace("{track}", (startNumber + index).toString().padStart(2, '0'))
                        .replace("{track00}", (startNumber + index).toString().padStart(2, '0'))
                        .replace("{track000}", (startNumber + index).toString().padStart(3, '0'))

                    // Sanitize filename
                    newName = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    
                    // Add original extension
                    newName = "$newName.${file.extension}"

                    // Rename file
                    val newFile = File(file.parent, newName)
                    if (file.renameTo(newFile)) {
                        successCount++
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rename $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false
            
            // Refresh files
            loadAudioFiles(forceRefresh = false)
        }
    }

    /**
     * Batch fix metadata (auto-correct common issues).
     */
    fun batchFixMetadata(
        filePaths: List<String>,
        options: com.voxly.presentation.screens.filebrowser.FixMetadataOptions
    ) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isSuccess) {
                        val metadata = metadataResult.getOrNull()!!
                        var updatedMetadata = metadata
                        var hasChanges = false

                        // Auto title case
                        if (options.autoTitleCase) {
                            updatedMetadata = updatedMetadata.copy(
                                title = metadata.title?.toTitleCase()?.also { if (it != metadata.title) hasChanges = true } ?: metadata.title,
                                artist = metadata.artist?.toTitleCase()?.also { if (it != metadata.artist) hasChanges = true } ?: metadata.artist,
                                album = metadata.album?.toTitleCase()?.also { if (it != metadata.album) hasChanges = true } ?: metadata.album
                            )
                        }

                        // Remove extra spaces
                        if (options.removeExtraSpaces) {
                            updatedMetadata = updatedMetadata.copy(
                                title = metadata.title?.trim()?.replace(Regex("\\s+"), " ")?.also { if (it != metadata.title) hasChanges = true } ?: metadata.title,
                                artist = metadata.artist?.trim()?.replace(Regex("\\s+"), " ")?.also { if (it != metadata.artist) hasChanges = true } ?: metadata.artist,
                                album = metadata.album?.trim()?.replace(Regex("\\s+"), " ")?.also { if (it != metadata.album) hasChanges = true } ?: metadata.album
                            )
                        }

                        // Fix track numbers (ensure proper format)
                        if (options.fixTrackNumbers) {
                            val trackNum = metadata.trackNumber
                            if (trackNum != null && trackNum < 1) {
                                updatedMetadata = updatedMetadata.copy(trackNumber = null)
                                hasChanges = true
                            }
                        }

                        // Apply changes if any
                        if (hasChanges) {
                            val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                            if (result.isSuccess) {
                                successCount++
                            } else {
                                failureCount++
                            }
                        } else {
                            successCount++ // No changes needed
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fix metadata for $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false
            
            // Refresh files
            loadAudioFiles(forceRefresh = false)
        }
    }

    /**
     * Cancel current batch operation.
     */
    fun cancelBatchOperation() {
        batchJob?.cancel()
        _isBatchProcessing.value = false
        _batchProgress.value = _batchProgress.value?.copy(status = BatchStatus.CANCELLED)
    }

    /**
     * Reset batch operation state.
     */
    fun resetBatchOperation() {
        _isBatchProcessing.value = false
        _batchProgress.value = null
        _batchError.value = null
    }

    /**
     * Clear batch error.
     */
    fun clearBatchError() {
        _batchError.value = null
    }

    /**
     * Batch set a field to the same value for all selected files.
     */
    fun batchSetUnifiedField(filePaths: List<String>, field: String, value: String) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isSuccess) {
                        val metadata = metadataResult.getOrNull()!!
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
                        if (result.isSuccess) {
                            successCount++
                        } else {
                            failureCount++
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set unified field for $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false

            // Refresh files
            loadAudioFiles(forceRefresh = false)
        }
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
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isSuccess) {
                        val metadata = metadataResult.getOrNull()!!
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
                            if (result.isSuccess) {
                                successCount++
                            } else {
                                failureCount++
                            }
                        } else {
                            successCount++ // No changes needed
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to replace text for $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false

            // Refresh files
            loadAudioFiles(forceRefresh = false)
        }
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
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null
            var successCount = 0
            var failureCount = 0

            filePaths.forEachIndexed { index, filePath ->
                _batchProgress.value = BatchProgress(
                    currentFile = index + 1,
                    totalFiles = filePaths.size,
                    percentage = (index + 1).toFloat() / filePaths.size,
                    currentFilePath = filePath,
                    status = BatchStatus.PROCESSING,
                    successCount = successCount,
                    failureCount = failureCount
                )

                try {
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isSuccess) {
                        val metadata = metadataResult.getOrNull()!!
                        val trackNumber = startNumber + index * step

                        val updatedMetadata = metadata.copy(
                            trackNumber = trackNumber,
                            totalTracks = totalTracks
                        )

                        val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                        if (result.isSuccess) {
                            successCount++
                        } else {
                            failureCount++
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-number track for $filePath", e)
                    failureCount++
                }
            }

            _batchProgress.value = BatchProgress(
                currentFile = filePaths.size,
                totalFiles = filePaths.size,
                percentage = 1f,
                currentFilePath = "",
                status = BatchStatus.COMPLETED,
                successCount = successCount,
                failureCount = failureCount
            )
            _isBatchProcessing.value = false

            // Refresh files
            loadAudioFiles(forceRefresh = false)
        }
    }

    // Helper function to convert string to Title Case
    private fun String.toTitleCase(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Converts a content URI to a file path.
     */
    private fun getPathFromUri(uri: Uri): String {
        return runCatching {
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

    /**
     * Aggregates audio files into albums and artists groups.
     */
    private fun aggregateData() {
        val allFiles = _directoryFiles.value.values.flatten()

        // Aggregate all audios
        _allAudios.value = allFiles

        // Aggregate albums
        val albumsMap = allFiles
            .filter { it.metadata.album?.isNotBlank() == true }
            .groupBy { it.metadata.album!! }
            .map { (albumName, files) ->
                val coverFile = files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ?: files.firstOrNull()
                AlbumGroup(
                    name = albumName,
                    artist = files.firstOrNull()?.metadata?.artist,
                    files = files.sortedBy { it.metadata.trackNumber },
                    coverPath = coverFile?.path
                )
            }
            .sortedBy { it.name.lowercase() }

        _albums.value = albumsMap

        // Aggregate artists
        val artistsMap = allFiles
            .filter { it.metadata.artist?.isNotBlank() == true }
            .groupBy { it.metadata.artist!! }
            .map { (artistName, files) ->
                val coverFile = files.randomOrNull()
                ArtistGroup(
                    name = artistName,
                    albums = files.mapNotNull { it.metadata.album }.distinct().sorted(),
                    files = files.sortedBy { it.metadata.album },
                    coverPath = coverFile?.path
                )
            }
            .sortedBy { it.name.lowercase() }

        _artists.value = artistsMap

        // Cache artists for detail screen
        artistsMap.forEach { artist ->
            val repoArtist = RepoArtistGroup(
                name = artist.name,
                files = artist.files,
                coverPath = artist.coverPath
            )
            artistCacheRepository.cacheArtist(repoArtist)
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
            // Always load audio files on first launch (before whitelist is set)
            // This ensures scanning happens after permission is granted
            loadAudioFiles()
        }
    }

    private fun persistSelectedDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setSelectedDirectoryUris(directories.map { it.uri })
        }
    }

    private suspend fun scanSelectedDirectories(
        directories: List<SelectedDirectory>,
        forceRefresh: Boolean = false
    ) {
        if (forceRefresh || _uiState.value !is FileBrowserUiState.Success) {
            _uiState.value = FileBrowserUiState.Loading
        }
        runCatching {
            val previousDirectoryFiles = _directoryFiles.value
            coroutineScope {
                directories.map { directory ->
                    async {
                        val filesForDirectory = when {
                            directory.path.isBlank() -> emptyList()
                            !forceRefresh && previousDirectoryFiles.containsKey(directory.uri) -> {
                                // Use previously scanned files for this directory if available
                                previousDirectoryFiles[directory.uri].orEmpty()
                            }
                            else -> {
                                // Always scan the directory to ensure we get all files,
                                // including files from newly added directories that aren't in cache yet.
                                // This fixes the issue where duplicate-filename files across
                                // directories wouldn't show up without manual refresh.
                                audioRepository.scanAudioFiles(
                                    directoryPath = directory.path,
                                    forceRefresh = forceRefresh
                                ).last()
                            }
                        }.distinctBy { it.path }
                        directory.uri to filesForDirectory
                    }
                }.awaitAll().toMap()
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
            aggregateData()
        }.onFailure { error ->
            if (error is CancellationException) {
                return@onFailure
            }
            Log.e(TAG, "Directory scan failed for ${directories.joinToString { it.path }}", error)
            _uiState.value = FileBrowserUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun isFileInDirectory(filePath: String, directoryPath: String): Boolean {
        val normalizedFile = filePath.trimEnd('/', '\\')
        val normalizedDirectory = directoryPath.trimEnd('/', '\\')
        return normalizedFile == normalizedDirectory ||
            normalizedFile.startsWith("$normalizedDirectory/") ||
            normalizedFile.startsWith("$normalizedDirectory\\")
    }

    fun saveScrollPosition(listKey: String, index: Int, offset: Int) {
        scrollPositions[listKey] = ScrollPosition(index = index, offset = offset)
    }

    fun getScrollPosition(listKey: String): ScrollPosition {
        return scrollPositions[listKey] ?: ScrollPosition()
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
