package com.voxly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.SyncedLyricLine
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * ViewModel for lyrics editor screen.
 * Handles local lyrics editing and online lyrics fetching.
 */
@HiltViewModel
class LyricsEditorViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))
    private val trackName: String = decodeNavArg(savedStateHandle.get<String>("trackName"))
    private val artistName: String = decodeNavArg(savedStateHandle.get<String>("artistName"))

    private val _uiState = MutableStateFlow<LyricsEditorUiState>(LyricsEditorUiState.Loading)
    val uiState: StateFlow<LyricsEditorUiState> = _uiState.asStateFlow()

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private val _editedLyricsText = MutableStateFlow("")
    val editedLyricsText: StateFlow<String> = _editedLyricsText.asStateFlow()

    private val _isSynced = MutableStateFlow(false)
    val isSynced: StateFlow<Boolean> = _isSynced.asStateFlow()

    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<OnlineLyricsResult>>(emptyList())
    val onlineSearchResults: StateFlow<List<OnlineLyricsResult>> = _onlineSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _showOnlineSearch = MutableStateFlow(false)
    val showOnlineSearch: StateFlow<Boolean> = _showOnlineSearch.asStateFlow()

    init {
        loadLyrics()
    }

    /**
     * Loads lyrics from the audio file.
     */
    private fun loadLyrics() {
        viewModelScope.launch {
            _uiState.update { LyricsEditorUiState.Loading }

            val lyricsReadResult = lyricsRepository.readLyrics(filePath)

            lyricsReadResult.fold(
                onSuccess = { lyrics ->
                    _lyrics.update { lyrics }
                    _editedLyricsText.update { lyrics?.getPlainText() ?: "" }
                    _isSynced.update { lyrics?.isSynced ?: false }
                    _hasChanges.update { false }
                    _uiState.update { LyricsEditorUiState.Success(lyrics) }
                },
                onFailure = { error ->
                    _uiState.update { LyricsEditorUiState.Error(error.message ?: "Failed to load lyrics") }
                }
            )
        }
    }

    /**
     * Updates the edited lyrics text.
     */
    fun updateLyricsText(text: String) {
        _editedLyricsText.update { text }
        _hasChanges.update { true }
    }

    /**
     * Toggles synced lyrics mode.
     */
    fun toggleSyncedMode(isSynced: Boolean) {
        _isSynced.update { isSynced }
        _hasChanges.update { true }
    }

    /**
     * Saves the edited lyrics to the file.
     */
    fun saveLyrics() {
        viewModelScope.launch {
            _uiState.update { LyricsEditorUiState.Saving }

            val lyricsToSave = if (_isSynced.value) {
                runCatching { Lyrics.parseLrc(_editedLyricsText.value) }
                    .getOrElse { Lyrics.createUnsynced(_editedLyricsText.value) }
            } else {
                Lyrics.createUnsynced(_editedLyricsText.value)
            }

            val lyricsSaveResult = lyricsRepository.saveLyrics(filePath, lyricsToSave)

            lyricsSaveResult.fold(
                onSuccess = {
                    _lyrics.update { lyricsToSave }
                    _hasChanges.update { false }
                    _uiState.update { LyricsEditorUiState.Success(lyricsToSave) }
                },
                onFailure = { error ->
                    _uiState.update { LyricsEditorUiState.Error(error.message ?: "Failed to save lyrics") }
                }
            )
        }
    }

    /**
     * Removes lyrics from the file.
     */
    fun removeLyrics() {
        viewModelScope.launch {
            _uiState.update { LyricsEditorUiState.Saving }

            val lyricsRemoveResult = lyricsRepository.removeLyrics(filePath)

            lyricsRemoveResult.fold(
                onSuccess = {
                    _lyrics.update { null }
                    _editedLyricsText.update { "" }
                    _hasChanges.update { false }
                    _uiState.update { LyricsEditorUiState.Success(null) }
                },
                onFailure = { error ->
                    _uiState.update { LyricsEditorUiState.Error(error.message ?: "Failed to remove lyrics") }
                }
            )
        }
    }

    /**
     * Searches for lyrics online.
     */
    fun searchOnlineLyrics() {
        viewModelScope.launch {
            _isSearching.update { true }
            _showOnlineSearch.update { true }

            val lyricsSearchResult = lyricsRepository.searchOnlineLyrics(
                trackName = trackName,
                artistName = artistName
            )

            lyricsSearchResult.fold(
                onSuccess = { results ->
                    _onlineSearchResults.update { results }
                },
                onFailure = { error ->
                    _onlineSearchResults.update { emptyList() }
                }
            )

            _isSearching.update { false }
        }
    }

    /**
     * Fetches lyrics by online ID.
     */
    fun fetchOnlineLyrics(resultItem: OnlineLyricsResult) {
        viewModelScope.launch {
            _uiState.update { LyricsEditorUiState.Loading }

            val onlineLyricsResult = lyricsRepository.getOnlineLyrics(resultItem)

            onlineLyricsResult.fold(
                onSuccess = { lyrics ->
                    _lyrics.update { lyrics }
                    _editedLyricsText.update { lyrics.getPlainText() }
                    _isSynced.update { lyrics.isSynced }
                    _hasChanges.update { true }
                    _showOnlineSearch.update { false }
                    _uiState.update { LyricsEditorUiState.Success(lyrics) }
                },
                onFailure = { error ->
                    _uiState.update { LyricsEditorUiState.Error(error.message ?: "Failed to fetch lyrics") }
                }
            )
        }
    }

    /**
     * Closes the online search dialog.
     */
    fun closeOnlineSearch() {
        _showOnlineSearch.update { false }
        _onlineSearchResults.update { emptyList() }
    }

    /**
     * Formats the current lyrics as LRC (adds timestamps if needed).
     */
    fun formatAsLrc() {
        val lines = _editedLyricsText.value.lines()
        val lrcLines = lines.mapIndexed { index, line ->
            // Simple auto-format: add sequential timestamps
            val timeMs = index * 5000L // 5 seconds per line
            val timestamp = SyncedLyricLine.formatTimestamp(timeMs)
            "$timestamp$line"
        }
        _editedLyricsText.update { lrcLines.joinToString("\n") }
        _isSynced.update { true }
        _hasChanges.update { true }
    }

    /**
     * Clears unsaved changes and reloads original lyrics.
     */
    fun discardChanges() {
        val originalLyrics = _lyrics.value
        _editedLyricsText.update { originalLyrics?.getPlainText() ?: "" }
        _isSynced.update { originalLyrics?.isSynced ?: false }
        _hasChanges.update { false }
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        if (_uiState.value is LyricsEditorUiState.Error) {
            _uiState.update { LyricsEditorUiState.Success(_lyrics.value) }
        }
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%') && !raw.contains('+')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}

/**
 * Sealed class representing lyrics editor UI states.
 */
sealed class LyricsEditorUiState {
    data object Loading : LyricsEditorUiState()
    data object Saving : LyricsEditorUiState()
    data class Success(val lyrics: Lyrics?) : LyricsEditorUiState()
    data class Error(val message: String) : LyricsEditorUiState()
}
