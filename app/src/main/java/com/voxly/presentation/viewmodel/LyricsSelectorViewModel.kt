package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.presentation.navigation.LyricsSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for LyricsSelectorScreen.
 * Loads lyrics and album art from file path.
 */
@HiltViewModel(assistedFactory = LyricsSelectorViewModel.Factory::class)
class LyricsSelectorViewModel @AssistedInject constructor(
    @Assisted val navKey: LyricsSelector,
    private val lyricsRepository: LyricsRepository,
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val filePath: String = navKey.filePath

    private val _lyricsText = MutableStateFlow("")
    val lyricsText: StateFlow<String> = _lyricsText.asStateFlow()

    private val _albumArtBytes = MutableStateFlow<ByteArray?>(null)
    val albumArtBytes: StateFlow<ByteArray?> = _albumArtBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String>(replay = 0)
    val error: SharedFlow<String> = _error.asSharedFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.update { true }

            // Load lyrics
            val lyricsResult = lyricsRepository.readLyrics(filePath)
            lyricsResult.fold(
                onSuccess = { lyrics ->
                    _lyricsText.update { lyrics?.getPlainText() ?: "" }
                },
                onFailure = { error ->
                    _error.emit(error.message ?: "Failed to load lyrics")
                }
            )

            // Load album art
            val albumArtResult = audioRepository.extractAlbumArt(filePath)
            albumArtResult.fold(
                onSuccess = { bytes ->
                    _albumArtBytes.update { bytes }
                },
                onFailure = { /* Ignore album art errors */ }
            )

            _isLoading.update { false }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: LyricsSelector): LyricsSelectorViewModel
    }
}
