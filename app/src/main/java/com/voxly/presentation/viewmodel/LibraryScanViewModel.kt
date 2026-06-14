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
import com.voxly.domain.model.CacheChange
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.usecase.ScanState
import com.voxly.domain.usecase.ScanTarget
import com.voxly.domain.usecase.UnifiedScanManager
import com.voxly.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
    private val mediaStoreVersionCache: com.voxly.data.local.MediaStoreVersionCache,
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

    private val _isInitialLoad = MutableStateFlow(true)

    /**
     * `isRefreshing` proxies [LibraryDataHolder.isRefreshing] — the global
     * scan-activity refcount (1+ while any scan is in flight). Each scan
     * lifetime here pairs [LibraryDataHolder.beginScan] with
     * [LibraryDataHolder.endScan] in a finally block; concurrent scans
     * correctly accumulate. External consumers (Files page, DirectoryContent,
     * MainActivity) observe this StateFlow directly.
     */
    val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing

    data class FileBrowserUiState(
        val allAudios: List<AudioFile> = emptyList(),
        val selectedDirectories: List<SelectedDirectory> = emptyList(),
        val directoryFiles: Map<String, List<AudioFile>> = emptyMap(),
        val isRefreshing: Boolean = false,
        val hasWhitelistDirectories: Boolean = false,
        val isInitialLoad: Boolean = true
    )

    val fileBrowserUiState: StateFlow<FileBrowserUiState> = combine(
        allAudios,
        selectedDirectories,
        directoryFiles,
        isRefreshing,
        hasWhitelistDirectories,
        _isInitialLoad
    ) { values: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        FileBrowserUiState(
            allAudios = values[0] as List<AudioFile>,
            selectedDirectories = values[1] as List<SelectedDirectory>,
            directoryFiles = values[2] as Map<String, List<AudioFile>>,
            isRefreshing = values[3] as Boolean,
            hasWhitelistDirectories = values[4] as Boolean,
            isInitialLoad = values[5] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        initialValue = FileBrowserUiState()
    )

    private var scanJob: Job? = null

    // Emitted when the scanner encounters an error. UI layers collect this to
    // show a Snackbar / error banner — previously these errors were only logged.
    private val _scanError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanError: SharedFlow<String> = _scanError.asSharedFlow()

    // Separate Job coordinator for directory-scoped scans. This guards every
    // entry point that calls scanSelectedDirectories() so concurrent
    // checkDirectorySnapshotsOnStart, refreshDirectoryIncremental, and addDirectory
    // calls cannot race.
    private var directoryScanJob: Job? = null

    /**
     * Runs a directory-scoped scan with proper cancellation of any prior run.
     * All call sites of [scanSelectedDirectories] should funnel through here.
     */
    private fun launchDirectoryScan(
        directories: List<SelectedDirectory>,
        isIncremental: Boolean,
        forceRefresh: Boolean
    ) {
        directoryScanJob?.cancel()
        directoryScanJob = viewModelScope.launch {
            libraryDataHolder.beginScan()
            try {
                scanSelectedDirectories(directories, isIncremental, forceRefresh)
            } finally {
                libraryDataHolder.endScan()
            }
        }
    }

    init {
        viewModelScope.launch {
            // collectLatest cancels the in-flight loadAudioFiles when a new
            // refresh request arrives. Combined with LibraryDataHolder's
            // conflated SharedFlow (extraBufferCapacity=1, DROP_OLDEST), this
            // collapses rapid bursts (e.g. user pulling on multiple screens in
            // succession) into the latest request and discards intermediate
            // work — preventing the "N concurrent scans queued" race.
            libraryDataHolder.refreshTriggers()
                .collectLatest { forceRefresh ->
                    loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
                }
        }

        // React to changes in the selected directory URIs (written by
        // DirectoryManagementViewModel via the settings screen). This keeps
        // _selectedDirectories in sync without requiring the settings flow
        // to call into LibraryScanViewModel directly.
        viewModelScope.launch {
            settingsDataStore.selectedDirectoryUris
                .map { uris -> uris.toSet() }
                .distinctUntilChanged()
                .collect { uris ->
                    val currentUris = _selectedDirectories.value.map { it.uri }.toSet()
                    if (uris != currentUris) {
                        syncSelectedDirectoriesFromStorage()
                    }
                }
        }

        viewModelScope.launch {
            delay(300L)
            unifiedScanManager.startWatchingSettings()
            checkDirectorySnapshotsOnStart()
            unifiedScanManager.scanState.collect { state ->
                when (state) {
                    is ScanState.Success -> Timber.d(TAG, "Scan completed")
                    is ScanState.Error -> {
                        Timber.tag(TAG).e("Scan error: ${state.message}")
                        _scanError.tryEmit(state.message)
                        libraryDataHolder.emitScanError(state.message)
                    }
                    else -> { }
                }
            }
        }

        viewModelScope.launch {
            delay(300L)
            musicLibraryCache.changeFlow.collect { change ->
                when (change) {
                    is CacheChange.FileUpdated -> {
                        val updatedFile = musicLibraryCache.getCachedFile(change.filePath)
                        updatedFile?.let { file ->
                            _directoryFiles.update { currentMap ->
                                currentMap.mapValues { (_, files) ->
                                    if (files.any { it.path == file.path }) {
                                        files.map { if (it.path == file.path) file else it }
                                    } else {
                                        files
                                    }
                                }
                            }
                        }
                    }
                    is CacheChange.FileDeleted -> {
                        _directoryFiles.update { currentMap ->
                            currentMap.mapValues { (_, files) ->
                                files.filter { it.path != change.filePath }
                            }.filterValues { it.isNotEmpty() }
                        }
                    }
                    else -> { }
                }
            }
        }
    }

    private suspend fun checkDirectorySnapshotsOnStart() {
        syncSelectedDirectoriesFromStorage()
        val dirs = _selectedDirectories.value
        if (dirs.isEmpty()) return

        val snapshots = musicLibraryCache.getAllDirectorySnapshots()
        val snapshotMap = snapshots.associateBy { it.directoryUri }

        val needsIncrementalScan = dirs.any { dir ->
            val snapshot = snapshotMap[dir.uri]
            val cachedCount = _directoryFiles.value[dir.uri]?.size
            snapshot == null || cachedCount == null || snapshot.fileCount != cachedCount
        }

        if (needsIncrementalScan) {
            launchDirectoryScan(dirs, isIncremental = true, forceRefresh = false)
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
            if (shouldShowRefresh) {
                libraryDataHolder.beginScan()
            }
            try {
                // _selectedDirectories is kept in sync with DataStore by the
                // collector at init block (lines 264-272), so this call is
                // intentionally omitted from the hot scan path. It runs only
                // when the DataStore value actually changes.

                // MediaStore version short-circuit: if the audio collection
                // version is unchanged since the last successful scan and we
                // already have cached data, the mtime diff inside the
                // incremental scan would be a no-op anyway. Bail out early
                // with the existing cache. Skipped on force-refresh so the
                // user can always force a re-scan.
                if (!forceRefresh && audioFileScanner.hasCachedData()) {
                    val currentVersion = mediaStoreVersionCache.current()
                    val lastVersion = settingsDataStore.lastKnownMediaStoreVersion.first()
                    if (lastVersion.isNotEmpty() && currentVersion == lastVersion) {
                        Timber.tag(TAG).d("MediaStore version unchanged ($currentVersion), skipping scan")
                        _isInitialLoad.update { false }
                        return@launch
                    }
                }

                if (_selectedDirectories.value.isNotEmpty()) {
                    val hasCache = audioFileScanner.hasCachedData()
                    if (!forceRefresh && hasCache) {
                        val cachedFiles = musicLibraryCache.getCachedAudioFilesOnce()
                        val grouped = groupFilesBySelectedDirectory(cachedFiles, _selectedDirectories.value)
                        _directoryFiles.update { grouped }
                    } else {
                        val useIncremental = isIncremental && hasCache
                        // Awaited synchronously so cancellation of scanJob
                        // (e.g. via collectLatest on a new refresh) also
                        // cancels the in-flight directory scan via
                        // structured concurrency.
                        scanSelectedDirectories(_selectedDirectories.value, useIncremental, forceRefresh)
                    }
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
                }
                _isInitialLoad.update { false }

                // Persist the current MediaStore version AFTER a successful
                // scan so subsequent calls can short-circuit. This is safe
                // even on the cache-served path: the cache IS the current
                // MediaStore state at the recorded version.
                if (!forceRefresh) {
                    val v = mediaStoreVersionCache.current()
                    if (v.isNotEmpty()) settingsDataStore.setLastKnownMediaStoreVersion(v)
                }
            } catch (e: CancellationException) {
                Timber.tag(TAG).d("Audio files load cancelled")
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load audio files")
            } finally {
                if (shouldShowRefresh) {
                    libraryDataHolder.endScan()
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
        Timber.tag("Voxly").i("LibraryScanViewModel scan triggered: incremental=${!forceRefresh}")
        loadAudioFiles(forceRefresh = forceRefresh, isIncremental = !forceRefresh)
    }

    fun refreshDirectoryIncremental(directoryUri: String) {
        val dirs = _selectedDirectories.value.filter { it.uri == directoryUri }
        if (dirs.isEmpty()) return
        launchDirectoryScan(dirs, isIncremental = true, forceRefresh = false)
    }

    fun syncAndScanDirectoriesIncremental() {
        viewModelScope.launch {
            syncSelectedDirectoriesFromStorage()
            if (_selectedDirectories.value.isNotEmpty()) {
                launchDirectoryScan(
                    _selectedDirectories.value,
                    isIncremental = true,
                    forceRefresh = false
                )
            }
        }
    }

    /**
     * Scans audio files from a specific directory.
     * Only triggers scan if directory is not already loaded.
     * If already loaded, uses cached data.
     */
    fun loadFromDirectory(directoryUri: Uri) {
        val uriString = directoryUri.toString()
        if (_selectedDirectories.value.any { it.uri == uriString }) {
            return
        }
        addDirectory(directoryUri)
    }

    /**
     * Adds one directory to the selected directory set and reloads.
     * If directory is already loaded, triggers incremental scan for new/deleted files.
     * If directory is new, triggers full scan.
     */
    fun addDirectory(directoryUri: Uri) {
        val filePath = getPathFromUri(directoryUri)
        if (filePath.isBlank()) {
            return
        }

        val uriString = directoryUri.toString()

        if (uriString in _directoryLoadingState.value) {
            return
        }

        val alreadySelected = _selectedDirectories.value.any { it.uri == uriString }
        val alreadyLoaded = uriString in _directoryFiles.value

        if (alreadySelected) {
            if (alreadyLoaded) {
                val dirs = _selectedDirectories.value.filter { it.uri == uriString }
                launchDirectoryScan(dirs, isIncremental = true, forceRefresh = false)
            } else {
                _directoryFiles.update { it - uriString }
                val dirs = _selectedDirectories.value.filter { it.uri == uriString }
                launchDirectoryScan(dirs, isIncremental = false, forceRefresh = true)
            }
            return
        }

        val updatedDirectories = (_selectedDirectories.value + SelectedDirectory(
            uri = uriString,
            path = filePath
        )).distinctBy { it.uri }

        _selectedDirectories.update { updatedDirectories }
        persistSelectedDirectories(updatedDirectories)
        launchDirectoryScan(
            directories = updatedDirectories,
            isIncremental = false,
            forceRefresh = true
        )
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

        val allDirsLoaded = directories.all { it.uri in _directoryFiles.value }
        if (allDirsLoaded && !forceRefresh && !isIncremental) {
            Timber.d(TAG, "All directories already loaded in _directoryFiles, skipping scan")
            // Refreshing lifecycle is managed by the caller
            // (loadAudioFiles / launchDirectoryScan) — no state to touch here.
            return
        }

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

            _directoryFiles.update { currentMap ->
                currentMap + filesByDir
            }

            withContext(Dispatchers.IO) {
                directories.forEach { dir ->
                    val fileCount = filesByDir[dir.uri]?.size ?: 0
                    musicLibraryCache.saveDirectorySnapshot(dir.uri, fileCount)
                }
            }

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
     * Delegates to the canonical implementation in PathUtils.
     */
    fun getPathFromUri(uri: Uri): String = com.voxly.core.util.PathUtils.getPathFromUri(uri)

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
                // Incremental: remove the old path from cache so the next scan
                // does a complete re-index of the renamed file, but skip the
                // expensive full-rescan path.
                viewModelScope.launch {
                    musicLibraryCache.removeFromCache(filePath)
                }
                loadAudioFiles(forceRefresh = false, isIncremental = true)
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
                viewModelScope.launch {
                    musicLibraryCache.removeFromCache(filePath)
                }
                loadAudioFiles(forceRefresh = false, isIncremental = true)
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
        }
    }
}
