package com.voxly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
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
import com.voxly.domain.model.BatchResult
import com.voxly.domain.usecase.BatchEngine
import com.voxly.domain.usecase.BatchProgress
import com.voxly.domain.model.BatchStatus
import com.voxly.domain.usecase.ScanResult
import com.voxly.domain.usecase.ScanState
import com.voxly.domain.usecase.ScanTarget
import com.voxly.domain.usecase.UnifiedScanManager
import com.voxly.core.util.SortUtil
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
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for the library screens (FileBrowser, Albums, Artists).
 * Handles unified audio scanning, selection, and batch operations.
 * This is the single source of truth for all library-level data.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRepository: AudioRepository,
    private val audioFileScanner: com.voxly.data.local.AudioFileScanner,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val settingsDataStore: SettingsDataStore,
    private val unifiedScanManager: UnifiedScanManager,
    private val safWriteAccessService: SafWriteAccessService,
    private val albumCacheRepository: AlbumCacheRepository,
    private val artistCacheRepository: ArtistCacheRepository,
    private val batchEngine: BatchEngine<String>,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {
    companion object {
        private const val TAG = "LibraryViewModel"
        private const val STATE_FLOW_TIMEOUT_MS = 5000L
    }

    private data class AlbumAggregationKey(
        val album: String,
        val albumArtist: String
    )

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

    // Track loading state per directory for UI to show loading indicator during scan
    private val _directoryLoadingState = MutableStateFlow<Set<String>>(emptySet())
    val directoryLoadingState: StateFlow<Set<String>> = _directoryLoadingState.asStateFlow()

    private val _openedDirectoryUri = MutableStateFlow<String?>(null)
    val openedDirectoryUri: StateFlow<String?> = _openedDirectoryUri.asStateFlow()

    // Aggregated data for tabs
    private val _allAudios = MutableStateFlow<List<AudioFile>>(emptyList())
    val allAudios: StateFlow<List<AudioFile>> = _allAudios.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumGroup>>(emptyList())
    val albums: StateFlow<List<AlbumGroup>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistGroup>>(emptyList())
    val artists: StateFlow<List<ArtistGroup>> = _artists.asStateFlow()

    val artistSeparatorEnabled: StateFlow<Boolean> = settingsDataStore.artistSeparatorEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * Artist separators as Set<String> for splitArtist()
     */
    val artistSeparatorsSet: StateFlow<Set<String>> = settingsDataStore.artistSeparatorsSet
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = setOf("&", "/", "\\")
        )

    /**
     * Unified scan state exposed for all screens to observe loading/progress.
     */
    val scanState: StateFlow<ScanState> = unifiedScanManager.scanState

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

    // Replace _batchProgress and _batchError with unified result
    private val _batchResult = MutableStateFlow<BatchResult?>(null)
    val batchResult: StateFlow<BatchResult?> = _batchResult.asStateFlow()

    // Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var scanJob: Job? = null
    private var batchJob: Job? = null
    private var cachedGlobalFiles: List<AudioFile>? = null
    private val scrollPositions = mutableMapOf<String, ScrollPosition>()

    // Scan filter settings - observe changes to trigger auto-refresh
    // Note: Core settings (whitelistEnabled, blacklistEnabled, minDurationFilterEnabled)
    // are watched by MusicLibraryRefreshManager at app level
    private val minDurationFilterThresholdMs = settingsDataStore.minDurationFilterThresholdMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60000)
    private val selectedDirectoryUris = settingsDataStore.selectedDirectoryUris
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val blacklistDirectoryUris = settingsDataStore.blacklistDirectoryUris
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Track previous settings to detect changes
    private var lastMinDurationFilterThresholdMs = 60000
    private var lastSelectedDirectoryUris = listOf<String>()
    private var lastBlacklistDirectoryUris = listOf<String>()

    init {
        restoreSelectedDirectories()
        // Note: unifiedScanManager.startWatchingSettings() is called in AppViewModel
        // to avoid duplicate watching

        // Listen to scan state changes from UnifiedScanManager
        // Note: We don't call loadAudioFiles here because the scan results are already
        // handled in the scan() call itself - calling loadAudioFiles would cause
        // a loop (scan completes -> loadAudioFiles -> scan again -> ...)
        viewModelScope.launch {
            unifiedScanManager.scanState.collect { state ->
                when (state) {
                    is ScanState.Success -> {
                        Timber.d(TAG, "Scan completed")
                        // Data is already loaded in scan() result, no need to reload
                    }
                    is ScanState.Error -> {
                        Timber.tag(TAG).e("Scan error: ${state.message}")
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }

    /**
     * Loads all audio files from device storage.
     *
     * @param forceRefresh If true, bypass cache and perform full scan (refresh button).
     *                      If false, use cache when available.
     * @param isIncremental If true, perform incremental scan to detect new/modified files (pull-to-refresh).
     *                      If false, perform full scan based on forceRefresh.
     */
    fun loadAudioFiles(forceRefresh: Boolean = false, isIncremental: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val shouldShowRefresh = forceRefresh || isIncremental
            try {
                if (shouldShowRefresh) {
                    _isRefreshing.value = true
                }

                syncSelectedDirectoriesFromStorage()

                if (_selectedDirectories.value.isNotEmpty()) {
                    if (!forceRefresh && !isIncremental) {
                        val cachedDirectoryFiles = _directoryFiles.value
                        val hasAllDirectoryEntries = _selectedDirectories.value.all { selected ->
                            selected.uri in cachedDirectoryFiles
                        }
                        if (hasAllDirectoryEntries) {
                            val mergedFiles = cachedDirectoryFiles.values.flatten().distinctBy { it.path }
                            _uiState.value = if (mergedFiles.isEmpty()) {
                                FileBrowserUiState.Empty
                            } else {
                                FileBrowserUiState.Success(
                                    files = mergedFiles,
                                    selectedCount = _selectedFiles.value.size
                                )
                            }
                            return@launch
                        }
                    }

                    if (isIncremental) {
                        scanSelectedDirectoriesIncremental(_selectedDirectories.value)
                    } else {
                        scanSelectedDirectoriesIncremental(_selectedDirectories.value)
                    }
                    return@launch
                }

                // No selected directories - handle global/all files case
                if (!forceRefresh && !isIncremental && audioFileScanner.hasCachedData()) {
                    val cachedFiles = audioFileScanner.getCachedAudioFiles().first()
                    if (cachedFiles.isNotEmpty()) {
                        cachedGlobalFiles = cachedFiles
                        _directoryFiles.value = emptyMap()
                        _uiState.value = FileBrowserUiState.Success(
                            files = cachedFiles,
                            selectedCount = _selectedFiles.value.size
                        )
                        Timber.d(TAG, "Loaded ${cachedFiles.size} files from cache")
                        return@launch
                    }
                }

                if (!forceRefresh && !isIncremental) {
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

                if (forceRefresh || isIncremental || _uiState.value !is FileBrowserUiState.Success) {
                    _uiState.value = FileBrowserUiState.Loading
                }

                val result = if (isIncremental) {
                    unifiedScanManager.scan(target = ScanTarget.Incremental, force = true)
                } else {
                    unifiedScanManager.scan(target = ScanTarget.Global, force = forceRefresh)
                }

                when (result) {
                    is ScanResult.Success -> {
                        cachedGlobalFiles = result.files
                        _directoryFiles.value = emptyMap()
                        _uiState.value = if (result.files.isEmpty()) {
                            FileBrowserUiState.Empty
                        } else {
                            FileBrowserUiState.Success(
                                files = result.files,
                                selectedCount = _selectedFiles.value.size
                            )
                        }
                        audioFileScanner.updateAlbumsAndArtistsFromFiles(result.files)
                        if (isIncremental) {
                            aggregateData()
                        }
                    }
                    is ScanResult.Error -> {
                        Timber.tag(TAG).e("Audio scan failed: ${result.message}")
                        _uiState.value = FileBrowserUiState.Error(result.message)
                    }
                    is ScanResult.Cancelled -> Unit
                }
            } finally {
                if (shouldShowRefresh) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * Unified refresh entry point for all screens.
     * forceRefresh=true: full rescan
     * forceRefresh=false: incremental scan (new/modified files only)
     */
    fun refresh(forceRefresh: Boolean = false) {
        loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
    }

    /**
     * Performs incremental scan on selected directories.
     * Detects new/modified files by comparing modification times with cache.
     */
    private suspend fun scanSelectedDirectoriesIncremental(directories: List<SelectedDirectory>) {
        Timber.d(TAG, "Performing incremental scan on ${directories.size} directories")

        runCatching {
            val paths = directories.map { it.path }.filter { it.isNotBlank() }
            if (paths.isEmpty()) {
                emptyMap()
            } else {
                val resultFiles = audioFileScanner.scanIncrementalForDirectories(paths).first()
                val filesByDir = mutableMapOf<String, List<AudioFile>>()
                directories.forEach { dir ->
                    val dirFiles = resultFiles.filter { isFileInDirectory(it.path, dir.path) }
                    filesByDir[dir.uri] = dirFiles
                }
                filesByDir
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
            // Sync to AudioFileScanner so Album/Artist screens see the data
            audioFileScanner.updateAlbumsAndArtistsFromFiles(mergedFiles)
        }.onFailure { error ->
            if (error is CancellationException) {
                return@onFailure
            }
            Timber.tag(TAG).e("Incremental directory scan failed for ${directories.joinToString { it.path }}", error)
            // Fall back to full scan on error
            scanSelectedDirectories(directories, forceRefresh = true)
        }
    }

    /**
     * Observes scan settings changes that are specific to FileBrowser and triggers auto-refresh.
     * Note: Core settings (whitelistEnabled, blacklistEnabled, minDurationFilterEnabled)
     * are watched by UnifiedScanManager at app level.
     */
    private fun observeScanSettingsChanges() {
        viewModelScope.launch {
            // Observe min duration filter threshold changes
            launch {
                minDurationFilterThresholdMs.collect { threshold ->
                    if (lastMinDurationFilterThresholdMs != threshold) {
                        lastMinDurationFilterThresholdMs = threshold
                        Timber.d(TAG, "Min duration filter threshold changed to: $threshold, triggering auto-refresh")
                        unifiedScanManager.scanAsync(target = ScanTarget.Global, force = true)
                    }
                }
            }

            // Observe whitelist directory changes
            launch {
                selectedDirectoryUris.collect { uris ->
                    if (lastSelectedDirectoryUris != uris) {
                        lastSelectedDirectoryUris = uris
                        Timber.d(TAG, "Selected directories changed, triggering auto-refresh")
                        unifiedScanManager.scanAsync(target = ScanTarget.Global, force = true)
                    }
                }
            }

            // Observe blacklist directory changes
            launch {
                blacklistDirectoryUris.collect { uris ->
                    if (lastBlacklistDirectoryUris != uris) {
                        lastBlacklistDirectoryUris = uris
                        Timber.d(TAG, "Blacklist directories changed, triggering auto-refresh")
                        unifiedScanManager.scanAsync(target = ScanTarget.Global, force = true)
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

        val alreadyLoaded = uriString in _directoryFiles.value || uriString in _directoryLoadingState.value
        if (updatedDirectories.size == _selectedDirectories.value.size && alreadyLoaded) {
            return
        }

        _selectedDirectories.value = updatedDirectories
        persistSelectedDirectories(updatedDirectories)
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (alreadyLoaded) {
                scanSelectedDirectoriesIncremental(updatedDirectories)
            } else {
                scanSelectedDirectories(updatedDirectories)
            }
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

                    // Check if targetName contains pattern placeholders
                    var processedName = targetName
                    if (targetName.contains(Regex("\\{.*\\}"))) {
                        // Read metadata for pattern substitution
                        val metadata = audioRepository.readMetadata(filePath).getOrNull()

                        processedName = targetName
                            .replace("{title}", metadata?.title ?: file.nameWithoutExtension)
                            .replace("{artist}", metadata?.artist ?: "Unknown")
                            .replace("{album}", metadata?.album ?: "Unknown")
                            .replace("{track}", (metadata?.trackNumber ?: 1).toString().padStart(2, '0'))
                            .replace("{track00}", (metadata?.trackNumber ?: 1).toString().padStart(2, '0'))
                            .replace("{track000}", (metadata?.trackNumber ?: 1).toString().padStart(3, '0'))
                    }

                    val sanitizedName = processedName.trim()
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

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    // Read current metadata to get search query
                    val currentMetadata = audioRepository.readMetadata(filePath).getOrNull()
                        ?: return@execute Result.failure(Exception("Failed to read metadata"))

                    val searchTitle = currentMetadata.title ?: File(filePath).nameWithoutExtension
                    val artistQuery = currentMetadata.artist

                    // Search online metadata
                    val searchResult = onlineMetadataRepository.searchByTrack(searchTitle, artistQuery)

                    if (searchResult.isFailure) {
                        return@execute Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
                    }

                    val searchResults = searchResult.getOrNull()
                    if (searchResults.isNullOrEmpty()) {
                        return@execute Result.failure(Exception("No search results"))
                    }

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
                            Timber.tag(TAG).w("Failed to fetch album art for $filePath", e)
                        }
                    }

                    if (result.isSuccess) {
                        Result.success(Unit)
                    } else {
                        Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
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

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    val index = filePaths.indexOf(filePath)
                    val file = File(filePath)
                    if (!file.exists()) {
                        return@execute Result.failure(Exception("File does not exist"))
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
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("Rename failed"))
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
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

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isFailure) {
                        return@execute Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                    }

                    val metadata = metadataResult.getOrNull()
                        ?: return@execute Result.failure(Exception("Read failed"))
                    var updatedMetadata = metadata
                    var hasChanges = false

                    // Auto title case
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

                    // Remove extra spaces
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
                            Result.success(Unit)
                        } else {
                            Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                        }
                    } else {
                        Result.success(Unit) // No changes needed
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
            loadAudioFiles(forceRefresh = false)
        }
    }

    /**
     * Cancel current batch operation.
     */
    fun cancelBatchOperation() {
        batchJob?.cancel()
        _isBatchProcessing.value = false
        _batchResult.value = _batchResult.value?.copy(status = BatchStatus.CANCELLED)
    }

    /**
     * Reset batch operation state.
     */
    fun resetBatchOperation() {
        _isBatchProcessing.value = false
        _batchProgress.value = null
        _batchError.value = null
        _batchResult.value = null
    }

    /**
     * Clear batch error.
     */
    fun clearBatchError() {
        _batchError.value = null
    }

    /**
     * Retry failed items from the last batch operation.
     * Only works for batch operations that support retry (e.g., batchFetchOnlineMetadata).
     */
    fun retryFailedItems() {
        val failed = _batchResult.value?.failedItems ?: return
        if (failed.isEmpty()) return

        // Default retry implementation re-runs the batchFetchOnlineMetadata
        // This is a simplified version - actual retry logic depends on the operation type
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null

            batchEngine.execute(
                items = failed.map { it.filePath },
                operation = { filePath ->
                    val currentMetadata = audioRepository.readMetadata(filePath).getOrNull()
                        ?: return@execute Result.failure(Exception("Failed to read metadata"))

                    val searchTitle = currentMetadata.title ?: File(filePath).nameWithoutExtension
                    val artistQuery = currentMetadata.artist
                    val searchResult = onlineMetadataRepository.searchByTrack(searchTitle, artistQuery)

                    if (searchResult.isFailure) {
                        return@execute Result.failure(searchResult.exceptionOrNull() ?: Exception("Search failed"))
                    }

                    val searchResults = searchResult.getOrNull()
                    if (searchResults.isNullOrEmpty()) {
                        return@execute Result.failure(Exception("No search results"))
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
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
            loadAudioFiles(forceRefresh = false)
        }
    }

    /**
     * Batch set a field to the same value for all selected files.
     */
    fun batchSetUnifiedField(filePaths: List<String>, field: String, value: String) {
        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchError.value = null

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isFailure) {
                        return@execute Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                    }

                    val metadata = metadataResult.getOrNull()
                        ?: return@execute Result.failure(Exception("Read failed"))
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
                        Result.success(Unit)
                    } else {
                        Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
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

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isFailure) {
                        return@execute Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                    }

                    val metadata = metadataResult.getOrNull()
                        ?: return@execute Result.failure(Exception("Read failed"))
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
                            Result.success(Unit)
                        } else {
                            Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                        }
                    } else {
                        Result.success(Unit) // No changes needed
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
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

            batchEngine.execute(
                items = filePaths,
                operation = { filePath ->
                    val index = filePaths.indexOf(filePath)
                    val metadataResult = audioRepository.readMetadata(filePath)
                    if (metadataResult.isFailure) {
                        return@execute Result.failure(metadataResult.exceptionOrNull() ?: Exception("Read failed"))
                    }

                    val metadata = metadataResult.getOrNull()
                        ?: return@execute Result.failure(Exception("Read failed"))
                    val trackNumber = startNumber + index * step

                    val updatedMetadata = metadata.copy(
                        trackNumber = trackNumber,
                        totalTracks = totalTracks
                    )

                    val result = audioRepository.updateMetadata(filePath, updatedMetadata)
                    if (result.isSuccess) {
                        Result.success(Unit)
                    } else {
                        Result.failure(result.exceptionOrNull() ?: Exception("Update failed"))
                    }
                },
                itemName = { it }
            ).collect { result ->
                _batchResult.value = result
            }

            _isBatchProcessing.value = false
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
     * Split artist string by separators
     * @param artist The artist string to split
     * @param separators Set of separator strings (e.g., setOf("&", "/", "\\"))
     * @return List of split artist names (empty strings filtered out)
     */
    private fun splitArtist(artist: String, separators: Set<String>): List<String> {
        if (artist.isBlank()) return emptyList()
        if (separators.isEmpty()) return listOf(artist)

        // Sort by length descending to avoid short separators matching before long ones
        val sortedSeparators = separators.sortedByDescending { it.length }
        val regex = sortedSeparators.joinToString("|") { Regex.escape(it) }

        return artist.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }
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
            .groupBy { file ->
                AlbumAggregationKey(
                    album = file.metadata.album!!,
                    albumArtist = file.metadata.albumArtist
                        ?.takeIf { it.isNotBlank() }
                        ?: file.metadata.artist.orEmpty()
                )
            }
            .map { (key, files) ->
                val coverFile = files.firstOrNull { it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 }
                    ?: files.firstOrNull()
                AlbumGroup(
                    name = key.album,
                    artist = key.albumArtist.ifBlank { files.firstOrNull()?.metadata?.artist },
                    files = files.sortedBy { it.metadata.trackNumber },
                    coverPath = coverFile?.path
                )
            }
            .sortedBy { SortUtil.toSortablePinyin(it.name) }

        _albums.value = albumsMap

        // Aggregate artists
        val isSeparatorEnabled = artistSeparatorEnabled.value
        val customSeparators = artistSeparatorsSet.value

        val artistsMap = mutableMapOf<String, MutableList<AudioFile>>()

        allFiles
            .filter { it.metadata.artist?.isNotBlank() == true }
            .forEach { file ->
                val artistField = file.metadata.artist!!

                if (isSeparatorEnabled && customSeparators.isNotEmpty()) {
                    // Split artist field
                    val splitArtists = splitArtist(artistField, customSeparators)
                    splitArtists.forEach { artistName ->
                        artistsMap.getOrPut(artistName) { mutableListOf() }.add(file)
                    }
                } else {
                    // No splitting, use original artist field
                    artistsMap.getOrPut(artistField) { mutableListOf() }.add(file)
                }
            }

        val artistsList = artistsMap.map { (artistName, files) ->
            // 固定选择封面：优先选择有专辑封面的文件，否则使用第一个文件
            val coverFile = files.firstOrNull { it.metadata.album?.isNotBlank() == true }
                ?: files.firstOrNull()
            ArtistGroup(
                name = artistName,
                albums = files.mapNotNull { it.metadata.album }.distinct().sorted(),
                files = files.sortedBy { it.metadata.album },
                coverPath = coverFile?.path
            )
        }.sortedBy { SortUtil.toSortablePinyin(it.name) }

        _artists.value = artistsList

        // Cache artists for detail screen
        artistsList.forEach { artist ->
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
        // Set loading state for all directories being scanned
        val dirUris = directories.map { it.uri }.toSet()
        _directoryLoadingState.value = _directoryLoadingState.value + dirUris

        if (forceRefresh || _uiState.value !is FileBrowserUiState.Success) {
            _uiState.value = FileBrowserUiState.Loading
        }

        val previousDirectoryFiles = _directoryFiles.value

        runCatching {
            val paths = directories.map { it.path }.filter { it.isNotBlank() }
            if (paths.isEmpty()) {
                emptyMap()
            } else {
                // Use unifiedScanManager to scan directories
                val result = unifiedScanManager.scan(
                    target = ScanTarget.Directories(paths),
                    force = forceRefresh
                )
                when (result) {
                    is ScanResult.Success -> {
                        // Group files by directory URI
                        val filesByDir = mutableMapOf<String, List<AudioFile>>()
                        directories.forEach { dir ->
                            val dirFiles = result.files.filter { it.path.startsWith(dir.path) }
                            filesByDir[dir.uri] = dirFiles
                        }
                        filesByDir
                    }
                    is ScanResult.Error -> throw IllegalStateException(result.message)
                    is ScanResult.Cancelled -> emptyMap()
                }
            }
        }.onSuccess { filesByDirectory ->
            _isRefreshing.value = false
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
            // Sync to AudioFileScanner so Album/Artist screens see the data
            audioFileScanner.updateAlbumsAndArtistsFromFiles(mergedFiles)
        }.onFailure { error ->
            _isRefreshing.value = false
            if (error is CancellationException) {
                return@onFailure
            }
            Timber.tag(TAG).e( "Directory scan failed for ${directories.joinToString { it.path }}", error)
            _uiState.value = FileBrowserUiState.Error(error.message ?: "Unknown error")
        }.also {
            // Clear loading state when scan completes (success or failure)
            _directoryLoadingState.value = _directoryLoadingState.value - dirUris
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
