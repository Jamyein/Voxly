package com.voxly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

/**
 * ViewModel for LyricsSelectorScreen.
 * Loads lyrics and album art from file path.
 */
@HiltViewModel
class LyricsSelectorViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val audioRepository: AudioRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))

    private val _lyricsText = MutableStateFlow("")
    val lyricsText: StateFlow<String> = _lyricsText.asStateFlow()

    private val _albumArtBytes = MutableStateFlow<ByteArray?>(null)
    val albumArtBytes: StateFlow<ByteArray?> = _albumArtBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Load lyrics
            val lyricsResult = lyricsRepository.readLyrics(filePath)
            lyricsResult.fold(
                onSuccess = { lyrics ->
                    _lyricsText.value = lyrics?.getPlainText() ?: ""
                },
                onFailure = { error ->
                    _error.value = error.message ?: "Failed to load lyrics"
                }
            )

            // Load album art
            val albumArtResult = audioRepository.extractAlbumArt(filePath)
            albumArtResult.fold(
                onSuccess = { bytes ->
                    _albumArtBytes.value = bytes
                },
                onFailure = { /* Ignore album art errors */ }
            )

            _isLoading.value = false
        }
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%') && !raw.contains('+')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}
