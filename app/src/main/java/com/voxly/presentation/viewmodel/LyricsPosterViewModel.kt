package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.presentation.navigation.LyricsPoster
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for LyricsPosterScreen.
 * Loads album art from file path for poster generation.
 */
@HiltViewModel(assistedFactory = LyricsPosterViewModel.Factory::class)
class LyricsPosterViewModel @AssistedInject constructor(
    @Assisted val navKey: LyricsPoster,
    @ApplicationContext private val context: Context,
    private val audioRepository: AudioRepository
) : ViewModel() {

    private val filePath: String = navKey.filePath

    private val _albumArtBytes = MutableStateFlow<ByteArray?>(null)
    val albumArtBytes: StateFlow<ByteArray?> = _albumArtBytes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAudioFile()
    }

    private fun loadAudioFile() {
        viewModelScope.launch {
            Timber.tag("Voxly").i("LyricsPoster: generation started")
            _isLoading.update { true }

            try {
                // Load album art
                val albumArtResult = audioRepository.extractAlbumArt(filePath)
                albumArtResult.fold(
                    onSuccess = { bytes ->
                        _albumArtBytes.update { bytes }
                    },
                    onFailure = { /* Ignore album art errors */ }
                )
            } finally {
                _isLoading.update { false }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: LyricsPoster): LyricsPosterViewModel
    }
}
