package com.voxly.presentation.viewmodel

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.core.util.Logger
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.data.repository.LyricsRepositoryImpl
import com.voxly.data.repository.LyricsRepositoryImpl.LyricsSourceResult
import com.voxly.data.repository.OnlineSourceResult
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.ReplayGainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

/**
 * ViewModel for the metadata editor screen.
 * Handles loading, editing, and saving audio file metadata.
 */
@HiltViewModel
class MetadataEditorViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val replayGainRepository: ReplayGainRepository,
    private val lyricsRepository: LyricsRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))

    private val _uiState = MutableStateFlow<MetadataEditorUiState>(MetadataEditorUiState.Loading)
    val uiState: StateFlow<MetadataEditorUiState> = _uiState.asStateFlow()

    private val _editedMetadata = MutableStateFlow<AudioMetadata?>(null)
    val editedMetadata: StateFlow<AudioMetadata?> = _editedMetadata.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

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
    val isScanningReplayGain: StateFlow<Boolean> = _isScanningReplayGain.asStateFlow()

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
            MetadataField.RECORD_LABEL -> currentMetadata.withCustomField("record_label", value)
            MetadataField.ENCODER -> currentMetadata.withCustomField("encoder", value)
            MetadataField.ISRC -> currentMetadata.withCustomField("isrc", value)
            MetadataField.COPYRIGHT -> currentMetadata.withCustomField("copyright", value)
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
     * Scans the current file for ReplayGain.
     */
    fun scanReplayGain() {
        viewModelScope.launch {
            _isScanningReplayGain.value = true
            
            // Scan the file for ReplayGain
            try {
                replayGainRepository.scanReplayGain(
                    listOf(filePath),
                    com.voxly.domain.repository.ScanQuality.NORMAL
                ).collect { progress ->
                    when (progress.status) {
                        com.voxly.domain.repository.ScanStatus.COMPLETED -> {
                            // Read the scanned ReplayGain info
                            val result = replayGainRepository.readReplayGain(filePath)
                            result.getOrNull()?.let { info ->
                                _pendingReplayGainInfo.value = info
                                _hasUnsavedChanges.value = true
                            }
                            _isScanningReplayGain.value = false
                        }
                        com.voxly.domain.repository.ScanStatus.FAILED -> {
                            _isScanningReplayGain.value = false
                        }
                        else -> { /* scanning in progress */ }
                    }
                }
            } catch (e: Exception) {
                _isScanningReplayGain.value = false
            }
        }
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
                    _saveResult.value = SaveResult.Success
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
                },
                onFailure = { error ->
                    Logger.e(
                        "Save metadata failed file=$filePath reason=${error.message ?: "unknown"} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        error,
                        "MetadataEditor"
                    )
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

    fun searchOnlineCoverCandidates() {
        val metadata = _editedMetadata.value ?: return
        val title = metadata.title?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
        val artist = metadata.artist?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _coverSearchState.value = CoverSearchState(isSearching = true)
            _isOnlineCoverLoading.value = true
            _onlineCoverError.value = null

            _onlineCoverResults.value = emptyList()
            try {
                aggregatedOnlineMetadataRepository.searchByTrackFlow(title, artist).collect { result ->
                    when (result) {
                        is OnlineSourceResult.RecordingResult -> {
                            if (result.recording.releaseId != null) {
                                val newResults = _coverSearchState.value.results + result.recording
                                _coverSearchState.update { it.copy(results = newResults) }
                                _onlineCoverResults.value =
                                    newResults.filter { !it.releaseId.isNullOrBlank() }
                            }
                        }

                        is OnlineSourceResult.SourceCompleted -> {
                            _coverSearchState.update { state ->
                                state.copy(completedSources = state.completedSources + result.source)
                            }
                        }

                        is OnlineSourceResult.Error -> {
                            _coverSearchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message)
                                )
                            }
                            _onlineCoverError.value = result.message
                        }

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Cover search failed"
                _coverSearchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
                _onlineCoverError.value = message
            } finally {
                _coverSearchState.update { it.copy(isSearching = false) }
                _isOnlineCoverLoading.value = false
            }
        }
    }

    fun applyOnlineCover(recording: OnlineRecording) {
        val releaseId = recording.releaseId ?: return

        viewModelScope.launch {
            _coverFetchMessage.value = null

            val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
            try {
                aggregatedOnlineMetadataRepository.preferredSource = when (recording.source) {
                    "MusicBrainz" -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                    "iTunes" -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                    "NetEase" -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                    "QQ Music" -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                    else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
                }

                val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
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

        viewModelScope.launch {
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
            val lyrics = lyricsRepository.getOnlineLyrics(result).getOrNull() ?: return@launch
            val text = if (lyrics.isSynced) lyrics.toLrcFormat() else lyrics.text
            updateMetadataField(MetadataField.LYRICS, text)
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
            lyrics = metadata.lyrics ?: currentMetadata.lyrics
        )

        _editedMetadata.value = updatedMetadata
        _hasUnsavedChanges.value = true

        val currentState = _uiState.value
        if (currentState is MetadataEditorUiState.Success) {
            _uiState.value = currentState.copy(editedMetadata = updatedMetadata)
        }
    }

    private fun metadataToStorageState(metadata: AudioMetadata): AudioMetadata {
        // Return a copy that represents the saved state
        return metadata.copy()
    }

    private fun AudioMetadata.withCustomField(key: String, value: String): AudioMetadata {
        val normalized = value.trim()
        val updated = customFields.toMutableMap()
        if (normalized.isBlank()) {
            updated.remove(key)
        } else {
            updated[key] = normalized
        }
        return copy(customFields = updated)
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%') && !raw.contains('+')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
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
    LYRICS,
    RECORD_LABEL,
    ENCODER,
    ISRC,
    COPYRIGHT
}

/**
 * Sealed class representing save operation results.
 */
sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(val message: String) : SaveResult()
}

data class LyricsSearchState(
    val results: List<OnlineLyricsResult> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)

data class CoverSearchState(
    val results: List<OnlineRecording> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)
