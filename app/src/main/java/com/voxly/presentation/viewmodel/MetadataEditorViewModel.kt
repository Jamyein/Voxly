package com.voxly.presentation.viewmodel

import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.Constants
import com.voxly.core.util.Logger
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.saf.SafGrantType
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.data.repository.LyricsRepositoryImpl
import com.voxly.data.repository.LyricsRepositoryImpl.LyricsSourceResult
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.model.ScanModeConstants
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.ReplayGainRepository
import com.voxly.domain.repository.RecentEditsRepository
import com.voxly.domain.repository.ScanMode
import com.voxly.domain.usecase.UnifiedScanManager
import com.voxly.presentation.navigation.MetadataEditor
import com.voxly.presentation.viewmodel.SearchSeedHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

/**
 * Error types for ReplayGain scan failures.
 */
sealed class ReplayGainScanError {
    data class DecodeFailed(val reason: String, val filePath: String) : ReplayGainScanError()
    data class NoAudioTrack(val filePath: String) : ReplayGainScanError()
    data class PermissionDenied(val filePath: String) : ReplayGainScanError()
    data class AllFallbacksFailed(val filePath: String) : ReplayGainScanError()
    data class Unknown(val message: String) : ReplayGainScanError()
}

/**
 * ViewModel for the metadata editor screen.
 * Handles loading, editing, and saving audio file metadata.
 */
@HiltViewModel(assistedFactory = MetadataEditorViewModel.Factory::class)
class MetadataEditorViewModel @AssistedInject constructor(
    @Assisted val navKey: MetadataEditor,
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    private val lyricsRepository: LyricsRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    private val settingsDataStore: SettingsDataStore,
    private val safWriteAccessService: SafWriteAccessService,
    private val recentEditsRepository: RecentEditsRepository,
    private val unifiedScanManager: UnifiedScanManager,
    private val searchSeedHolder: SearchSeedHolder
) : ViewModel() {

    private val TAG = "MetadataEditorVM"

    // Get filePath from NavKey instead of SavedStateHandle
    private val filePath: String = navKey.filePath

    // Scan mode setting
    private val _scanMode = MutableStateFlow(ScanMode.TRACK_ONLY)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    // Initialize scan mode from settings
    init {
        viewModelScope.launch {
            val mode = settingsDataStore.scanMode.first()
            _scanMode.value = when (mode) {
                ScanModeConstants.SINGLE_ALBUM -> ScanMode.SINGLE_ALBUM
                ScanModeConstants.ALBUMS -> ScanMode.ALBUMS
                else -> ScanMode.TRACK_ONLY
            }
        }
    }

    private val _uiState = MutableStateFlow<MetadataEditorUiState>(MetadataEditorUiState.Loading)
    val uiState: StateFlow<MetadataEditorUiState> = _uiState.asStateFlow()

    private val _editedMetadata = MutableStateFlow<AudioMetadata?>(null)
    val editedMetadata: StateFlow<AudioMetadata?> = _editedMetadata.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _modifiedFields = MutableStateFlow<Set<MetadataField>>(emptySet())
    val modifiedFields: StateFlow<Set<MetadataField>> = _modifiedFields.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private val _onlineLyricsResults = MutableStateFlow<List<OnlineLyricsResult>>(emptyList())
    val onlineLyricsResults: StateFlow<List<OnlineLyricsResult>> = _onlineLyricsResults.asStateFlow()

    private val _isOnlineLyricsLoading = MutableStateFlow(false)
    val isOnlineLyricsLoading: StateFlow<Boolean> = _isOnlineLyricsLoading.asStateFlow()

    private val _onlineLyricsError = MutableStateFlow<String?>(null)
    val onlineLyricsError: StateFlow<String?> = _onlineLyricsError.asStateFlow()

    private val _lyricsSearchState = MutableStateFlow(LyricsSearchState())
    val lyricsSearchState: StateFlow<LyricsSearchState> = _lyricsSearchState.asStateFlow()

    private val _coverFetchMessage = MutableStateFlow<String?>(null)
    val coverFetchMessage: StateFlow<String?> = _coverFetchMessage.asStateFlow()

    private val _onlineCoverResults = MutableStateFlow<List<OnlineRecording>>(emptyList())
    val onlineCoverResults: StateFlow<List<OnlineRecording>> = _onlineCoverResults.asStateFlow()

    private val _isOnlineCoverLoading = MutableStateFlow(false)
    val isOnlineCoverLoading: StateFlow<Boolean> = _isOnlineCoverLoading.asStateFlow()

    private val _onlineCoverError = MutableStateFlow<String?>(null)
    val onlineCoverError: StateFlow<String?> = _onlineCoverError.asStateFlow()

    private val _coverSearchState = MutableStateFlow(CoverSearchState())
    val coverSearchState: StateFlow<CoverSearchState> = _coverSearchState.asStateFlow()

    // ReplayGain state
    private val _pendingReplayGainInfo = MutableStateFlow<ReplayGainInfo?>(null)
    val pendingReplayGainInfo: StateFlow<ReplayGainInfo?> = _pendingReplayGainInfo.asStateFlow()

    private val _isScanningReplayGain = MutableStateFlow(false)
    private var _originalMetadata: AudioMetadata? = null
    val isScanningReplayGain: StateFlow<Boolean> = _isScanningReplayGain.asStateFlow()

    private val _replayGainScanError = MutableStateFlow<ReplayGainScanError?>(null)
    val replayGainScanError: StateFlow<ReplayGainScanError?> = _replayGainScanError.asStateFlow()

    // Search job tracking - cancel previous search when new one starts
    private var _lyricsSearchJob: kotlinx.coroutines.Job? = null
    private var _coverSearchJob: kotlinx.coroutines.Job? = null

    // Combined edit state using combine() - reduces multiple StateFlow updates to single UI recomposition
    val editState: StateFlow<EditState> = combine(
        _hasUnsavedChanges,
        _modifiedFields,
        _saveResult
    ) { hasUnsavedChanges, modifiedFields, saveResult ->
        EditState(
            hasUnsavedChanges = hasUnsavedChanges,
            modifiedFields = modifiedFields,
            saveResult = saveResult
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditState()
    )

    init {
        loadAudioFile()
    }

    /**
     * Loads the audio file and its metadata.
     */
    private fun loadAudioFile() {
        viewModelScope.launch {
            _uiState.value = MetadataEditorUiState.Loading

            val audioFileResult = audioRepository.getAudioFile(filePath)

            audioFileResult.fold(
                onSuccess = { audioFile ->
                    _editedMetadata.value = audioFile.metadata
                    _originalMetadata = audioFile.metadata

                    // Load existing ReplayGain info if available
                    val replayGainResult = replayGainRepository.readReplayGain(filePath)
                    replayGainResult.getOrNull()?.let { replayGainInfo ->
                        _pendingReplayGainInfo.value = replayGainInfo
                    }
                    
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
        Logger.d(
            "Metadata field update file=$filePath field=$field valueLength=${value.length}",
            "MetadataEditor"
        )
        val nonBlankValue = value.takeIf { it.isNotBlank() }
        val updatedMetadata = when (field) {
            MetadataField.TITLE -> currentMetadata.copy(title = nonBlankValue)
            MetadataField.ARTIST -> currentMetadata.copy(artist = nonBlankValue)
            MetadataField.ALBUM -> currentMetadata.copy(album = nonBlankValue)
            MetadataField.ALBUM_ARTIST -> currentMetadata.copy(albumArtist = nonBlankValue)
            MetadataField.YEAR -> currentMetadata.copy(year = nonBlankValue)
            MetadataField.GENRE -> currentMetadata.copy(genre = nonBlankValue)
            MetadataField.COMPOSER -> currentMetadata.copy(composer = nonBlankValue)
            MetadataField.LYRICIST -> currentMetadata.copy(lyricist = nonBlankValue)
            MetadataField.CONDUCTOR -> currentMetadata.copy(conductor = nonBlankValue)
            MetadataField.COMMENT -> currentMetadata.copy(comment = nonBlankValue)
            MetadataField.LYRICS -> currentMetadata.copy(lyrics = value)
        }

        setEditedMetadata(updatedMetadata, modifiedField = field)
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

        setEditedMetadata(updatedMetadata)
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

        setEditedMetadata(updatedMetadata)
    }

    /**
     * Updates the album art.
     * @param albumArtBytes The new album art bytes
     */
    fun updateAlbumArt(albumArtBytes: ByteArray?) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(albumArt = albumArtBytes)

        setEditedMetadata(updatedMetadata)
    }

    private fun setEditedMetadata(updatedMetadata: AudioMetadata, modifiedField: MetadataField? = null) {
        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true
        if (modifiedField != null) {
            _modifiedFields.value = _modifiedFields.value + modifiedField
        }

        // 同步更新搜索种子，供 Online Search 屏幕使用编辑中的实时值
        searchSeedHolder.updateSeed(
            title = updatedMetadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension,
            artist = updatedMetadata.artist?.takeIf { it.isNotBlank() },
            album = updatedMetadata.album?.takeIf { it.isNotBlank() }
        )

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    /**
     * Updates the pending ReplayGain info.
     * This should be called when ReplayGain scanning completes.
     * @param replayGainInfo The new ReplayGain info to save
     */
    fun updateReplayGainInfo(replayGainInfo: ReplayGainInfo) {
        _pendingReplayGainInfo.value = replayGainInfo
        _hasUnsavedChanges.value = true
    }

    /**
     * Clears the pending ReplayGain info.
     */
    fun clearReplayGainInfo() {
        _pendingReplayGainInfo.value = null
        _hasUnsavedChanges.value = true
    }

    /**
     * Clears the ReplayGain scan error.
     */
    fun clearReplayGainScanError() {
        _replayGainScanError.value = null
    }

    /**
     * Scans the current file for ReplayGain.
     * Uses dynamic sample rate handling - high-resolution audio (>48kHz) 
     * will be automatically downsampled for optimal performance.
     * 
     * When scan mode is ALBUM_ONLY or TRACK_AND_ALBUM, this will:
     * 1. Find other files in the same album from MediaStore
     * 2. Scan all album files
     * 3. Calculate album gain from all tracks
     */
    fun scanReplayGain() {
        viewModelScope.launch {
            _isScanningReplayGain.value = true
            _replayGainScanError.value = null // Clear previous error
            
            // Using ACCURATE mode for best results - dynamic sampling handles high-res files
            val scanQuality = com.voxly.domain.repository.ScanQuality.ACCURATE
            
            try {
                val currentScanMode = _scanMode.value
                val filesToScan: List<String>
                
                // Determine which files to scan based on scan mode (foobar2000 compatible)
                if (currentScanMode == ScanMode.TRACK_ONLY) {
                    // Track Only: scan single file only, no album gain
                    filesToScan = listOf(filePath)
                } else {
                    // Single Album or Albums mode: find same album files from MediaStore
                    // - SINGLE_ALBUM: treat all files as one album
                    // - ALBUMS: will group by album tags (in batch mode), same as SINGLE_ALBUM for single file
                    val albumFiles = findAlbumFiles()
                    // Always scan all found album files (even if only one - foobar2000 behavior)
                    filesToScan = albumFiles.ifEmpty { listOf(filePath) }
                }
                
                Logger.i("ReplayGain scan started. mode=${currentScanMode.name} files=${filesToScan.size}", "MetadataEditor")

                // Get target loudness from settings
                val targetLoudness = settingsDataStore.replayGainTargetLoudness.first()

                // Scan all files
                replayGainRepository.scanReplayGain(
                    filesToScan,
                    scanQuality,
                    targetLoudness
                ).collect { progress ->
                    when (progress.status) {
                        com.voxly.domain.repository.ScanStatus.COMPLETED -> {
                            _replayGainScanError.value = null
                            // Read the scanned ReplayGain info for current file
                            val replayGainReadResult = replayGainRepository.readReplayGain(filePath)
                            replayGainReadResult.getOrNull()?.let { info ->
                                // For album modes (SINGLE_ALBUM, ALBUMS), always calculate album gain
                                val finalInfo = if (currentScanMode != ScanMode.TRACK_ONLY) {
                                    calculateAlbumGainFromScannedFiles(filesToScan)
                                } else {
                                    info
                                }

                                _pendingReplayGainInfo.value = finalInfo
                                _hasUnsavedChanges.value = true
                            }
                            _isScanningReplayGain.value = false
                            Logger.i("ReplayGain scan completed. mode=${currentScanMode.name}", "MetadataEditor")
                        }
                        com.voxly.domain.repository.ScanStatus.FAILED -> {
                            // Determine error type based on reason
                            val error: ReplayGainScanError = when {
                                progress.currentFilePath.contains("Permission") ||
                                progress.currentFilePath.contains("EACCES") ->
                                    ReplayGainScanError.PermissionDenied(progress.currentFilePath)
                                progress.currentFilePath.contains("audio track") ||
                                progress.currentFilePath.contains("no audio") ||
                                progress.currentFilePath.contains("NO_AUDIO_TRACK") ->
                                    ReplayGainScanError.NoAudioTrack(progress.currentFilePath)
                                progress.currentFilePath.contains("ALL_FALLBACKS_EXHAUSTED") ->
                                    ReplayGainScanError.AllFallbacksFailed(progress.currentFilePath)
                                progress.currentFilePath.contains("decode") ||
                                progress.currentFilePath.contains("codec") ||
                                progress.currentFilePath.contains("DECODER_INIT_FAILED") ||
                                progress.currentFilePath.contains("fallback") ->
                                    ReplayGainScanError.DecodeFailed("解码失败", progress.currentFilePath)
                                else ->
                                    ReplayGainScanError.Unknown(progress.currentFilePath)
                            }
                            _replayGainScanError.value = error
                            _isScanningReplayGain.value = false
                            Logger.e("ReplayGain scan failed.", null, "MetadataEditor")
                        }
                        else -> { /* scanning in progress */ }
                    }
                }
            } catch (e: Exception) {
                Logger.e("ReplayGain scan exception: ${e.message}", e, "MetadataEditor")
                _isScanningReplayGain.value = false
            }
        }
    }
    
    /**
     * Finds files in the same album using MediaStore.
     */
    private suspend fun findAlbumFiles(): List<String> = withContext(Dispatchers.IO) {
        val metadata = _editedMetadata.value ?: return@withContext emptyList()
        
        val album = metadata.album ?: return@withContext emptyList()
        val artist = metadata.artist
        
        if (album.isBlank()) return@withContext emptyList()
        
        val files = mutableListOf<String>()
        
        try {
            val selection = if (artist.isNullOrBlank()) {
                "${MediaStore.Audio.Media.ALBUM} = ?"
            } else {
                "${MediaStore.Audio.Media.ALBUM} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            }
            
            val selectionArgs = if (artist.isNullOrBlank()) {
                arrayOf(album)
            } else {
                arrayOf(album, artist)
            }
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null && File(path).exists()) {
                        files.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to find album files: ${e.message}", e, "MetadataEditor")
        }
        
        files
    }
    
    /**
     * Calculates album gain from multiple scanned files using energy average.
     * Matches foobar2000 ReplayGain album gain calculation:
     * album_rms = sqrt(mean(track_rms²))
     *
     * This prevents loud tracks from dominating the album gain calculation.
     */
    private suspend fun calculateAlbumGainFromScannedFiles(filePaths: List<String>): ReplayGainInfo? {
        if (filePaths.isEmpty()) return null

        val trackGains = mutableListOf<ReplayGainInfo>()

        for (path in filePaths) {
            val replayGainResult = replayGainRepository.readReplayGain(path)
            replayGainResult.getOrNull()?.let { trackGains.add(it) }
        }

        if (trackGains.isEmpty()) return null

        // Reference loudness: -14 dB = 10^(-14/20) ≈ 0.1995
        // This must match ReplayGainScanner.REFERENCE_LUFS
        val referenceLufs = Constants.REPLAYGAIN_REFERENCE_LOUDNESS_LUFS
        val rmsReference = Constants.REPLAYGAIN_RMS_REFERENCE

        // Convert track gains back to RMS values for energy average
        // track_gain = target - measured
        // measured = target - track_gain
        // measured_db = 20 * log10(rms / reference)
        // => rms = reference * 10^(measured_db / 20)
        // => rms = reference * 10^((target - track_gain) / 20)
        val trackRmsValues = trackGains.map { trackGain ->
            rmsReference * 10.0.pow((referenceLufs - trackGain.trackGain) / 20.0)
        }

        // Energy average: sqrt(mean(rms²))
        val energyMean = trackRmsValues.map { it * it }.average()
        val albumRmsLinear = sqrt(energyMean)

        // Convert back to dB gain: album_gain = target - 20 * log10(album_rms / reference)
        val albumGainDb = if (albumRmsLinear > 0) {
            (referenceLufs - 20 * log10(albumRmsLinear / rmsReference)).toFloat()
        } else {
            0f
        }

        val maxPeak = trackGains.maxOfOrNull { it.trackPeak } ?: 0f

        // Get current file's track gain, or use album gain if not found
        val currentFileResult = replayGainRepository.readReplayGain(filePath)
        val currentTrackGain = currentFileResult.getOrNull()?.trackGain ?: albumGainDb
        val currentTrackPeak = currentFileResult.getOrNull()?.trackPeak ?: maxPeak

        return ReplayGainInfo(
            trackGain = currentTrackGain,
            trackPeak = currentTrackPeak,
            albumGain = albumGainDb,
            albumPeak = maxPeak
        )
    }

    /**
     * Saves the edited metadata and ReplayGain to the file.
     */
    fun saveMetadata() {
        val metadataToSave = _editedMetadata.value ?: return
        val replayGainToSave = _pendingReplayGainInfo.value

        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Logger.i(
                "Save metadata started file=$filePath hasReplayGain=${replayGainToSave != null}",
                "MetadataEditor"
            )
            _uiState.value = MetadataEditorUiState.Saving

            // First save the metadata
            val metadataResult = audioRepository.updateMetadata(filePath, metadataToSave)
            
            metadataResult.fold(
                onSuccess = {
                    // If we have pending ReplayGain info, save it too
                    var replayGainSuccess = true
                    if (replayGainToSave != null) {
                        val replayGainResult = replayGainRepository.saveReplayGain(
                            filePath,
                            replayGainToSave
                        )
                        replayGainSuccess = replayGainResult.isSuccess
                        if (replayGainSuccess) {
                            _pendingReplayGainInfo.value = null // Clear after successful save
                        } else {
                            Logger.w(
                                "Save replaygain failed file=$filePath reason=${replayGainResult.exceptionOrNull()?.message ?: "unknown"}",
                                "MetadataEditor"
                            )
                        }
                    }
                    
                    _hasUnsavedChanges.value = false
                    _modifiedFields.value = emptySet()
                    _saveResult.value = SaveResult.Success

                    // Add to recent edits history
                    _originalMetadata?.let { original ->
                        recentEditsRepository.addRecentEdit(
                            filePath = filePath,
                            originalMetadata = original,
                            newMetadata = metadataToSave
                        )
                    }
                    val currentSuccessState = _uiState.value as? MetadataEditorUiState.Success
                    _uiState.value = currentSuccessState?.copy(
                        editedMetadata = metadataToSave,
                        audioFile = currentSuccessState.audioFile.copy(
                            metadata = metadataToSave,
                            replayGainInfo = replayGainToSave ?: currentSuccessState.audioFile.replayGainInfo
                        )
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
                            metadata = metadataToSave,
                            replayGainInfo = replayGainToSave
                        ),
                        editedMetadata = metadataToSave
                    )
                    Logger.i(
                        "Save metadata success file=$filePath replayGainSuccess=$replayGainSuccess elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        "MetadataEditor"
                    )

                    // Sync file to cache so FileBrowser gets updated data
                    unifiedScanManager.syncFile(filePath)
                },
                onFailure = { error ->
                    Logger.e(
                        "Save metadata failed file=$filePath reason=${error.message ?: "unknown"} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        error,
                        "MetadataEditor"
                    )
                    // Check if this is a permission-related error
                    val errorMessage = error.message ?: "Failed to save"
                    val requiresReauthorization = errorMessage.contains("SAF write permission") ||
                            errorMessage.contains("Permission denied") ||
                            errorMessage.contains("EACCES") ||
                            errorMessage.contains("write permission")

                    _saveResult.value = SaveResult.Error(
                        message = errorMessage,
                        requiresReauthorization = requiresReauthorization,
                        errorCode = if (requiresReauthorization) {
                            SaveErrorCode.PERMISSION_REQUIRED
                        } else {
                            SaveErrorCode.SAVE_FAILED
                        }
                    )
                    val currentState = _uiState.value
                    if (currentState is MetadataEditorUiState.Saving) {
                        _uiState.value = MetadataEditorUiState.Error(
                            errorMessage + if (requiresReauthorization) "\n\n请重新选择文件以恢复写入权限。" else ""
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
            val metadataReadResult = audioRepository.readMetadata(filePath)
            metadataReadResult.onSuccess { originalMetadata ->
                _editedMetadata.value = originalMetadata
                _originalMetadata = originalMetadata
                _hasUnsavedChanges.value = false
                _modifiedFields.value = emptySet()
                // 清除搜索种子（放弃修改后不再使用编辑中的值）
                searchSeedHolder.clearSeed()
                val currentState = _uiState.value
                if (currentState is MetadataEditorUiState.Success) {
                    _uiState.value = currentState.copy(editedMetadata = originalMetadata)
                }
            }
        }
    }

    /**
     * Converts specified metadata fields from Traditional Chinese to Simplified Chinese.
     * @param fields Set of fields to convert
     */
    fun convertToSimplified(fields: Set<ConvertibleField>) {
        viewModelScope.launch {
            convertFields(fields, toSimplified = true)
        }
    }

    /**
     * Converts specified metadata fields from Simplified Chinese to Traditional Chinese.
     * @param fields Set of fields to convert
     */
    fun convertToTraditional(fields: Set<ConvertibleField>) {
        viewModelScope.launch {
            convertFields(fields, toSimplified = false)
        }
    }

    /**
     * Internal method to convert fields between Chinese character sets.
     */
    private suspend fun convertFields(fields: Set<ConvertibleField>, toSimplified: Boolean) {
        val currentMetadata = _editedMetadata.value ?: return
        
        var updatedMetadata = currentMetadata
        
        for (field in fields) {
            val value = when (field) {
                ConvertibleField.TITLE -> currentMetadata.title
                ConvertibleField.ARTIST -> currentMetadata.artist
                ConvertibleField.ALBUM -> currentMetadata.album
                ConvertibleField.ALBUM_ARTIST -> currentMetadata.albumArtist
                ConvertibleField.GENRE -> currentMetadata.genre
                ConvertibleField.COMPOSER -> currentMetadata.composer
                ConvertibleField.LYRICIST -> currentMetadata.lyricist
                ConvertibleField.COMMENT -> currentMetadata.comment
                ConvertibleField.LYRICS -> currentMetadata.lyrics
            }
            
            if (value.isNullOrBlank()) continue
            
            val converted = withContext(Dispatchers.Default) {
                if (toSimplified) {
                    toSimplifiedChinese(value)
                } else {
                    toTraditionalChinese(value)
                }
            }
            
            if (converted != value) {
                updatedMetadata = when (field) {
                    ConvertibleField.TITLE -> updatedMetadata.copy(title = converted)
                    ConvertibleField.ARTIST -> updatedMetadata.copy(artist = converted)
                    ConvertibleField.ALBUM -> updatedMetadata.copy(album = converted)
                    ConvertibleField.ALBUM_ARTIST -> updatedMetadata.copy(albumArtist = converted)
                    ConvertibleField.GENRE -> updatedMetadata.copy(genre = converted)
                    ConvertibleField.COMPOSER -> updatedMetadata.copy(composer = converted)
                    ConvertibleField.LYRICIST -> updatedMetadata.copy(lyricist = converted)
                    ConvertibleField.COMMENT -> updatedMetadata.copy(comment = converted)
                    ConvertibleField.LYRICS -> updatedMetadata.copy(lyrics = converted)
                }
            }
        }
        
        setEditedMetadata(updatedMetadata)
    }

    private fun toSimplifiedChinese(text: String): String {
        val transliterator = android.icu.text.Transliterator.getInstance("Traditional-Simplified")
        return transliterator.transliterate(text)
    }

    private fun toTraditionalChinese(text: String): String {
        val transliterator = android.icu.text.Transliterator.getInstance("Simplified-Traditional")
        return transliterator.transliterate(text)
    }

    /**
     * Clears the save result after it has been handled.
     */
    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun reauthorizeAndRetrySave(uri: Uri, grantType: SafGrantType) {
        viewModelScope.launch {
            val permissionResult = safWriteAccessService.persistPermission(uri, grantType)
            permissionResult.fold(
                onSuccess = {
                    Logger.i("SAF permission updated for file=$filePath grantType=$grantType", TAG)
                    saveMetadata()
                },
                onFailure = { error ->
                    val message = error.message
                        ?: "Failed to persist SAF permission. Please retry selecting the file or directory."
                    Logger.e("SAF reauthorization failed file=$filePath reason=$message", error, TAG)
                    _saveResult.value = SaveResult.Error(
                        message = message,
                        requiresReauthorization = true,
                        errorCode = SaveErrorCode.PERMISSION_REAUTHORIZE_FAILED
                    )
                    _uiState.value = MetadataEditorUiState.Error(message)
                }
            )
        }
    }

    fun searchOnlineCoverCandidates() {
        val metadata = _editedMetadata.value ?: return
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
        val artist = metadata.artist?.takeIf { it.isNotBlank() }

        // Cancel previous search before starting new one (flatMapLatest pattern)
        _coverSearchJob?.cancel()

        _coverSearchJob = viewModelScope.launch {
            _coverSearchState.value = CoverSearchState(isSearching = true)
            _isOnlineCoverLoading.value = true
            _onlineCoverError.value = null

            _onlineCoverResults.value = emptyList()
            try {
                val coverSearchResult = aggregatedOnlineMetadataRepository.searchByTrackForCover(title, artist)
                coverSearchResult.fold(
                    onSuccess = { recordings ->
                        recordings.forEach { recording ->
                            val newResults = _coverSearchState.value.results + recording
                            _coverSearchState.update { it.copy(results = newResults) }
                            _onlineCoverResults.value = newResults
                        }
                        _coverSearchState.update { it.copy(isSearching = false) }
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Cover search failed"
                        _coverSearchState.update { state ->
                            state.copy(errorSources = state.errorSources + ("System" to message))
                        }
                        _onlineCoverError.value = message
                        _coverSearchState.update { it.copy(isSearching = false) }
                    }
                )
            } catch (e: Exception) {
                val message = e.message ?: "Cover search failed"
                _coverSearchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
                _onlineCoverError.value = message
                _coverSearchState.update { it.copy(isSearching = false) }
            } finally {
                _isOnlineCoverLoading.value = false
            }
        }
    }

    fun applyOnlineCover(recording: OnlineRecording) {
        // If coverArtUrl already exists in the recording, use it directly
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    val bytes = downloadImageBytes(
                        url = existingCoverUrl,
                        userAgent = "Mozilla/5.0"
                    )
                    if (bytes.isNotEmpty()) {
                        updateAlbumArt(bytes)
                        _coverFetchMessage.value = "Cover fetched successfully"
                    } else {
                        _coverFetchMessage.value = "Cover URL is invalid"
                    }
                } catch (e: Exception) {
                    _coverFetchMessage.value = "Failed to load cover: ${e.message}"
                }
            }
            return
        }

        val releaseId = recording.releaseId
        
        Logger.d("applyOnlineCover: releaseId=$releaseId, source=${recording.source}", TAG)
        
        // If no releaseId, show a message and return
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.value = "无法获取封面：该结果没有关联的专辑信息"
            return
        }

        viewModelScope.launch {
            _coverFetchMessage.value = null

            val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
            try {
                val targetSource = when (recording.source) {
                    OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                    OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                    OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                    OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                    else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
                }
                Logger.d("applyOnlineCover: setting preferredSource=$targetSource", TAG)
                aggregatedOnlineMetadataRepository.preferredSource = targetSource

                val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
                Logger.d("applyOnlineCover: coverResult isSuccess=${coverResult.isSuccess}, isFailure=${coverResult.isFailure}", TAG)
                coverResult.fold(
                    onSuccess = { cover ->
                        if (cover != null) {
                            updateAlbumArt(cover)
                            _coverFetchMessage.value = "Cover fetched successfully"
                        } else {
                            _coverFetchMessage.value = "No online cover found"
                        }
                    },
                    onFailure = {
                        Logger.e("applyOnlineCover failed: ${it.message}", it, TAG)
                        _coverFetchMessage.value = it.message ?: "Cover fetch failed"
                    }
                )
            } finally {
                aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
            }
        }
    }

    fun clearCoverFetchMessage() {
        _coverFetchMessage.value = null
    }

    fun clearOnlineCoverResults() {
        _onlineCoverResults.value = emptyList()
        _onlineCoverError.value = null
        _coverSearchState.value = CoverSearchState()
    }

    fun searchOnlineLyrics() {
        val metadata = _editedMetadata.value ?: return
        val track = metadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
        val artist = metadata.artist?.takeIf { it.isNotBlank() }
        val album = metadata.album?.takeIf { it.isNotBlank() }
        val flowLyricsRepository = lyricsRepository as? LyricsRepositoryImpl ?: return

        // Cancel previous search before starting new one (flatMapLatest pattern)
        _lyricsSearchJob?.cancel()

        _lyricsSearchJob = viewModelScope.launch {
            _lyricsSearchState.value = LyricsSearchState(isSearching = true)
            _isOnlineLyricsLoading.value = true
            _onlineLyricsError.value = null

            _onlineLyricsResults.value = emptyList()
            try {
                flowLyricsRepository.searchOnlineLyricsFlow(track, artist, album).collect { result ->
                    when (result) {
                        is LyricsSourceResult.Result -> {
                            val newResults = _lyricsSearchState.value.results + result.lyrics
                            _lyricsSearchState.update { it.copy(results = newResults) }
                            _onlineLyricsResults.value = newResults
                        }

                        is LyricsSourceResult.SourceCompleted -> {
                            _lyricsSearchState.update { state ->
                                state.copy(completedSources = state.completedSources + result.source)
                            }
                        }

                        is LyricsSourceResult.Error -> {
                            _lyricsSearchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message)
                                )
                            }
                            _onlineLyricsError.value = result.message
                        }
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Lyrics search failed"
                _onlineLyricsError.value = message
                _lyricsSearchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
            } finally {
                _lyricsSearchState.update { it.copy(isSearching = false) }
                _isOnlineLyricsLoading.value = false
            }
        }
    }

    fun applyOnlineLyrics(result: OnlineLyricsResult) {
        viewModelScope.launch {
            _isOnlineLyricsLoading.value = true
            try {
                val lyrics = withContext(Dispatchers.IO) {
                    lyricsRepository.getOnlineLyrics(result).getOrNull()
                }
                if (lyrics != null) {
                    val text = if (lyrics.isSynced) lyrics.toLrcFormat() else lyrics.text
                    updateMetadataField(MetadataField.LYRICS, text)
                } else {
                    _onlineLyricsError.value = "Failed to load lyrics content"
                }
            } catch (e: Exception) {
                _onlineLyricsError.value = "Failed to load lyrics: ${e.message}"
            } finally {
                _isOnlineLyricsLoading.value = false
            }
        }
    }

    fun clearOnlineLyricsResults() {
        _onlineLyricsResults.value = emptyList()
        _onlineLyricsError.value = null
        _lyricsSearchState.value = LyricsSearchState()
    }

    fun applyOnlineMetadata(metadata: AudioMetadata) {
        val currentMetadata = _editedMetadata.value ?: return
        val updatedMetadata = currentMetadata.copy(
            title = metadata.title ?: currentMetadata.title,
            artist = metadata.artist ?: currentMetadata.artist,
            album = metadata.album ?: currentMetadata.album,
            albumArtist = metadata.albumArtist ?: currentMetadata.albumArtist,
            year = metadata.year ?: currentMetadata.year,
            genre = metadata.genre ?: currentMetadata.genre,
            trackNumber = metadata.trackNumber ?: currentMetadata.trackNumber,
            totalTracks = metadata.totalTracks ?: currentMetadata.totalTracks,
            lyrics = metadata.lyrics ?: currentMetadata.lyrics,
            albumArt = metadata.albumArt ?: currentMetadata.albumArt
        )

        setEditedMetadata(updatedMetadata)
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: MetadataEditor): MetadataEditorViewModel
    }
}
