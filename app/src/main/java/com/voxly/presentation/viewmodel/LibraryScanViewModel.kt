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
import kotlinx.coroutines.flow.first
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
    private val unifiedScanManager: UnifiedScanManager,
    private val safWriteAccessService: SafWriteAccessService,
    private val albumCacheRepository: AlbumCacheRepository,
    private val artistCacheRepository: ArtistCacheRepository,
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

    private val _selectedDirectories = MutableStateFlow<List<SelectedDirectory>>(emptyList())
    val selectedDirectories: StateFlow<List<SelectedDirectory>> = _selectedDirectories.asStateFlow()

    private val _directoryFiles = MutableStateFlow<Map<String, List<AudioFile>>>(emptyMap())
    val directoryFiles: StateFlow<Map<String, List<AudioFile>>> = _directoryFiles.asStateFlow()

    private val _directoryLoadingState = MutableStateFlow<Set<String>>(emptySet())
    val directoryLoadingState: StateFlow<Set<String>> = _directoryLoadingState.asStateFlow()

    private val _openedDirectoryUri = MutableStateFlow<String?>(null)
    val openedDirectoryUri: StateFlow<String?> = _openedDirectoryUri.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
        restoreSelectedDirectories()

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
     * Loads all audio files from device storage.
     */
    fun loadAudioFiles(forceRefresh: Boolean = false, isIncremental: Boolean = false) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val shouldShowRefresh = forceRefresh || isIncremental
            try {
                if (shouldShowRefresh) {
                    _isRefreshing.update { true }
                }

                syncSelectedDirectoriesFromStorage()

                if (_selectedDirectories.value.isNotEmpty()) {
                    scanSelectedDirectories(_selectedDirectories.value, isIncremental, forceRefresh)
                    return@launch
                }

                if (forceRefresh || isIncremental || !audioFileScanner.hasCachedData()) {
                    val files = audioFileScanner.scan(
                        directoryPaths = emptyList(),
                        incremental = isIncremental,
                        forceRefresh = forceRefresh
                    )
                    _directoryFiles.update { files.groupBy { it.path } }
                }
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
                    buildMap<String, List<AudioFile>> {
                        directories.forEach { dir ->
                            val dirFiles = files.filter { isFileInDirectory(it.path, dir.path) }
                            put(dir.uri, dirFiles)
                        }
                    }
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

    fun cacheAlbum(album: AlbumGroup) {
        albumCacheRepository.cacheAlbum(album)
    }

    fun cacheArtist(artist: ArtistGroup) {
        artistCacheRepository.cacheArtist(artist)
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

    private fun isFileInDirectory(filePath: String, directoryPath: String): Boolean {
        val normalizedFile = filePath.trimEnd('/', '\\')
        val normalizedDirectory = directoryPath.trimEnd('/', '\\')
        return normalizedFile == normalizedDirectory ||
            normalizedFile.startsWith("$normalizedDirectory/") ||
            normalizedFile.startsWith("$normalizedDirectory\\")
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
