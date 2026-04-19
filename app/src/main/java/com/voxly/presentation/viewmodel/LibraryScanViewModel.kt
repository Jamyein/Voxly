package com.voxly.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.SortUtil
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.UiStateDataStore
import com.voxly.data.local.DirFileSortOption
import com.voxly.data.local.FileSortOption
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.usecase.ScanState
import com.voxly.domain.usecase.ScanTarget
import com.voxly.domain.usecase.UnifiedScanManager
import com.voxly.core.util.Constants
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * ViewModel for library scanning, paging, and SAF directory management.
 */
@HiltViewModel
class LibraryScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileScanner: com.voxly.data.local.AudioFileScanner,
    private val musicLibraryCache: com.voxly.data.local.MusicLibraryCache,
    private val settingsDataStore: SettingsDataStore,
    private val uiStateDataStore: UiStateDataStore,
    private val unifiedScanManager: UnifiedScanManager,
    private val safWriteAccessService: SafWriteAccessService,
    private val audioRepository: AudioRepository,
    private val libraryDataHolder: LibraryDataHolder
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryScanViewModel"
        private const val STATE_FLOW_TIMEOUT_MS = 5000L
    }

    // Keep original allAudios for backward compatibility
    @Suppress("DEPRECATION")
    val allAudios: StateFlow<List<AudioFile>> = audioFileScanner.filteredFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val scanState: StateFlow<ScanState> = unifiedScanManager.scanState

    val hasWhitelistDirectories: StateFlow<Boolean> = settingsDataStore.selectedDirectoryUris
        .map { uris -> uris.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = false
        )

    val currentFileSortOption: StateFlow<FileSortOption> = uiStateDataStore.fileBrowserSortOption
        .map { it.toFileSortOption() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = FileSortOption.NAME_ASC
        )

    val currentDirectorySortOption: StateFlow<DirFileSortOption> = uiStateDataStore.directoryFileSortOption
        .map { it.toDirFileSortOption() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = DirFileSortOption.NAME_ASC
        )

    val sortedAllAudios: StateFlow<List<AudioFile>> = combine(
        allAudios,
        currentFileSortOption
    ) { audios, sortOption ->
        audios to sortOption
    }.map { (audios, sortOption) ->
        sortAudioFiles(audios, sortOption)
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _selectedDirectories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val selectedDirectories: StateFlow<List<SelectedDirectory>> = _selectedDirectories.asStateFlow()

    private val _directoryFiles = MutableStateFlow<Map<String, List<AudioFile>>>(emptyMap())
    val directoryFiles: StateFlow<Map<String, List<AudioFile>>> = _directoryFiles.asStateFlow()

    val sortedDirectoryFiles: StateFlow<Map<String, List<AudioFile>>> = combine(
        directoryFiles,
        currentDirectorySortOption
    ) { filesByDirectory, sortOption ->
        filesByDirectory to sortOption
    }.map { (filesByDirectory, sortOption) ->
        filesByDirectory.mapValues { (_, files) ->
            sortAudioFiles(files, sortOption)
        }
    }.flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
            initialValue = emptyMap()
        )

    private val _directoryLoadingState = MutableStateFlow<Set<String>>(emptySet())
    val directoryLoadingState: StateFlow<Set<String>> = _directoryLoadingState.asStateFlow()

    private val _openedDirectoryUri = MutableStateFlow<String?>(null)
    val openedDirectoryUri: StateFlow<String?> = _openedDirectoryUri.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isInitialLoad = MutableStateFlow(true)

    data class FileBrowserUiState(
        val allAudios: List<AudioFile> = emptyList(),
        val selectedDirectories: List<SelectedDirectory> = emptyList(),
        val directoryFiles: Map<String, List<AudioFile>> = emptyMap(),
        val isRefreshing: Boolean = false,
        val hasWhitelistDirectories: Boolean = false
    )

    val fileBrowserUiState: StateFlow<FileBrowserUiState> = combine(
        allAudios,
        selectedDirectories,
        directoryFiles,
        isRefreshing,
        hasWhitelistDirectories
    ) { audios, dirs, files, refreshing, hasWhitelist ->
        FileBrowserUiState(
            allAudios = audios,
            selectedDirectories = dirs,
            directoryFiles = files,
            isRefreshing = refreshing,
            hasWhitelistDirectories = hasWhitelist
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = FileBrowserUiState()
    )

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            unifiedScanManager.startWatchingSettings()
        }

        viewModelScope.launch {
            libraryDataHolder.collectRefreshTriggers { forceRefresh ->
                loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
            }
        }

        viewModelScope.launch {
            unifiedScanManager.scanState.collect { state ->
                when (state) {
                    is ScanState.Success -> Timber.d(TAG, "Scan completed")
                    is ScanState.Error -> Timber.tag(TAG).e("Scan error: ${state.message}")
                    else -> { }
                }
            }
        }
    }

    /**
     * Entry point for app initialization during the splash screen.
     * Restores selected directories and performs the initial scan.
     */
    fun initializeApp() {
        restoreSelectedDirectories()
    }

    /**
     * Loads all audio files from device storage.
     *
     * At startup: prefer cache, no incremental scan by default.
     * User manual refresh (pull-to-refresh) triggers incremental scan.
     * Force refresh triggers full rescan.
     */
    fun loadAudioFiles(forceRefresh: Boolean = false, isIncremental: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val shouldShowRefresh = forceRefresh || _isInitialLoad.value
            try {
                if (shouldShowRefresh) {
                    _isRefreshing.update { true }
                }

                syncSelectedDirectoriesFromStorage()

                if (_selectedDirectories.value.isNotEmpty()) {
                    val hasCache = audioFileScanner.hasCachedData()
                    if (!forceRefresh && hasCache) {
                        _isRefreshing.update { false }
                        _isInitialLoad.update { false }
                        return@launch
                    }
                    val useIncremental = isIncremental && hasCache
                    scanSelectedDirectories(_selectedDirectories.value, useIncremental, forceRefresh)
                    _isInitialLoad.update { false }
                    return@launch
                }

                if (forceRefresh || !audioFileScanner.hasCachedData()) {
                    audioFileScanner.scan(
                        directoryPaths = emptyList(),
                        incremental = isIncremental,
                        forceRefresh = forceRefresh
                    )
                    _directoryFiles.update { emptyMap() }
                } else if (isIncremental) {
                    audioFileScanner.loadAudioFiles(isIncremental = true)
                } else {
                    _isRefreshing.update { false }
                }
                _isInitialLoad.update { false }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("Audio files load cancelled")
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load audio files")
            } finally {
                if (shouldShowRefresh) {
                    _isRefreshing.update { false }
                }
            }
        }
    }

    /**
     * Unified refresh entry point for all screens.
     * - forceRefresh=true: Full rescan, ignores cache
     * - forceRefresh=false: Incremental scan, detects new/modified files
     */
    fun refresh(forceRefresh: Boolean = false) {
        loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
    }

    /**
     * Scans audio files from a specific directory.
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

        _selectedDirectories.update { updatedDirectories }
        persistSelectedDirectories(updatedDirectories)
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanSelectedDirectories(
                directories = updatedDirectories,
                isIncremental = alreadyLoaded,
                forceRefresh = !alreadyLoaded
            )
        }
    }

    /**
     * Removes one selected directory and reloads.
     */
    fun removeDirectory(directoryUri: String) {
        val updatedDirectories = _selectedDirectories.value.filterNot { it.uri == directoryUri }
        _selectedDirectories.update { updatedDirectories }
        _directoryFiles.update { it - directoryUri }
        if (_openedDirectoryUri.value == directoryUri) {
            _openedDirectoryUri.update { null }
        }
        persistSelectedDirectories(updatedDirectories)
        loadAudioFiles()
    }

    /**
     * Clears selected directories and falls back to global scan.
     */
    fun clearDirectories() {
        _selectedDirectories.update { emptyList() }
        _directoryFiles.update { emptyMap() }
        _openedDirectoryUri.update { null }
        persistSelectedDirectories(emptyList())
        loadAudioFiles()
    }

    fun openDirectory(directoryUri: String) {
        _openedDirectoryUri.update { directoryUri }
    }

    fun closeOpenedDirectory() {
        _openedDirectoryUri.update { null }
    }

    private suspend fun scanSelectedDirectories(
        directories: List<SelectedDirectory>,
        isIncremental: Boolean = false,
        forceRefresh: Boolean = false
    ) {
        Timber.d(TAG, "Scanning ${directories.size} directories (incremental=$isIncremental, force=$forceRefresh)")

        val dirUris = directories.map { it.uri }.toSet()
        _directoryLoadingState.update { it + dirUris }

        try {
            val paths = directories.map { it.path }.filter { it.isNotBlank() }
            val filesByDir = if (paths.isEmpty()) {
                emptyMap<String, List<AudioFile>>()
            } else {
                val files = audioFileScanner.scan(
                    directoryPaths = paths,
                    incremental = isIncremental,
                    forceRefresh = forceRefresh
                )

                withContext(Dispatchers.Default) {
                    groupFilesBySelectedDirectory(files, directories)
                }
            }

            _directoryFiles.update { filesByDir }

            if (_openedDirectoryUri.value != null && _openedDirectoryUri.value !in filesByDir.keys) {
                _openedDirectoryUri.update { null }
            }
        } catch (e: CancellationException) {
            Timber.tag(TAG).d("Directory scan cancelled")
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Directory scan failed for ${directories.joinToString { it.path }}", e)
        } finally {
            _directoryLoadingState.update { it - dirUris }
        }
    }

    private fun groupFilesBySelectedDirectory(
        files: List<AudioFile>,
        directories: List<SelectedDirectory>
    ): Map<String, List<AudioFile>> {
        val normalizedDirectories = directories.map {
            NormalizedDirectory(
                uri = it.uri,
                path = it.path.trimEnd('/', '\\')
            )
        }.sortedByDescending { it.path.length }

        val groupedFiles = LinkedHashMap<String, MutableList<AudioFile>>(normalizedDirectories.size)
        normalizedDirectories.forEach { groupedFiles[it.uri] = mutableListOf() }

        files.forEach { file ->
            val normalizedFilePath = file.path.trimEnd('/', '\\')
            val matchedDirectory = normalizedDirectories.firstOrNull { directory ->
                normalizedFilePath == directory.path ||
                    normalizedFilePath.startsWith("${directory.path}/") ||
                    normalizedFilePath.startsWith("${directory.path}\\")
            }

            if (matchedDirectory != null) {
                groupedFiles.getValue(matchedDirectory.uri).add(file)
            }
        }

        return groupedFiles.mapValues { (_, grouped) -> grouped.toList() }
    }

    private data class NormalizedDirectory(
        val uri: String,
        val path: String
    )

    private fun sortAudioFiles(
        files: List<AudioFile>,
        sortOption: FileSortOption
    ): List<AudioFile> {
        return when (sortOption) {
            FileSortOption.NAME_ASC -> files.sortedBy {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            FileSortOption.NAME_DESC -> files.sortedByDescending {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            FileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            FileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
        }
    }

    private fun sortAudioFiles(
        files: List<AudioFile>,
        sortOption: DirFileSortOption
    ): List<AudioFile> {
        return when (sortOption) {
            DirFileSortOption.NAME_ASC -> files.sortedBy {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            DirFileSortOption.NAME_DESC -> files.sortedByDescending {
                SortUtil.toSortablePinyin(it.metadata.getDisplayTitle(it.name))
            }
            DirFileSortOption.SIZE_DESC -> files.sortedByDescending { it.size }
            DirFileSortOption.DURATION_DESC -> files.sortedByDescending { it.duration }
        }
    }

    private fun String.toFileSortOption(): FileSortOption {
        return try {
            FileSortOption.valueOf(this)
        } catch (_: IllegalArgumentException) {
            FileSortOption.NAME_ASC
        }
    }

    private fun String.toDirFileSortOption(): DirFileSortOption {
        return try {
            DirFileSortOption.valueOf(this)
        } catch (_: IllegalArgumentException) {
            DirFileSortOption.NAME_ASC
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
            _selectedDirectories.update { restored }
            _directoryFiles.update { it.filterKeys { key ->
                restored.any { it.uri == key }
            } }
            if (_openedDirectoryUri.value != null && restored.none { it.uri == _openedDirectoryUri.value }) {
                _openedDirectoryUri.update { null }
            }
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
            _selectedDirectories.update { restored }
            loadAudioFiles()
        }
    }

    private fun persistSelectedDirectories(directories: List<SelectedDirectory>) {
        viewModelScope.launch {
            settingsDataStore.setSelectedDirectoryUris(directories.map { it.uri })
        }
    }

    /**
     * Converts a content URI to a file path.
     */
    fun getPathFromUri(uri: Uri): String {
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

    fun renameSingleFile(
        filePath: String,
        targetName: String,
        onComplete: (success: Boolean, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)
                    var processedName = targetName
                    if (targetName.contains(Regex("\\{.*\\}"))) {
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
}
