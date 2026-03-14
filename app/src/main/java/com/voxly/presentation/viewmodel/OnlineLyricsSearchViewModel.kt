package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.repository.LyricsRepositoryImpl
import com.voxly.data.repository.LyricsRepositoryImpl.LyricsSourceResult
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.presentation.navigation.OnlineLyricsSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for online lyrics search screen.
 */
@HiltViewModel(assistedFactory = OnlineLyricsSearchViewModel.Factory::class)
class OnlineLyricsSearchViewModel @AssistedInject constructor(
    @Assisted val navKey: OnlineLyricsSearch,
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    private val filePath: String = navKey.filePath

    // Search query info (exposed for UI)
    private val _searchTitle = MutableStateFlow("")
    val searchTitle: String get() = _searchTitle.value

    private val _searchArtist = MutableStateFlow<String?>(null)
    val searchArtist: String? get() = _searchArtist.value

    private val _searchAlbum = MutableStateFlow<String?>(null)
    val searchAlbum: String? get() = _searchAlbum.value

    private val _lyricsResults = MutableStateFlow<List<OnlineLyricsResult>>(emptyList())
    val lyricsResults: StateFlow<List<OnlineLyricsResult>> = _lyricsResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchState = MutableStateFlow(LyricsSearchState())
    val searchState: StateFlow<LyricsSearchState> = _searchState.asStateFlow()

    private val _selectedLyrics = MutableStateFlow<OnlineLyricsResult?>(null)
    val selectedLyrics: StateFlow<OnlineLyricsResult?> = _selectedLyrics.asStateFlow()

    /**
     * Search for lyrics using the audio file's metadata.
     */
    fun search(path: String) {
        val targetPath = path.ifBlank { filePath }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _lyricsResults.value = emptyList()
            _searchState.value = LyricsSearchState()

            // Load audio file metadata
            val result = audioRepository.getAudioFile(targetPath)
            result.fold(
                onSuccess = { audioFile ->
                    val metadata = audioFile.metadata
                    val track = metadata.title?.takeIf { it.isNotBlank() } ?: File(targetPath).nameWithoutExtension
                    val artist = metadata.artist?.takeIf { it.isNotBlank() }
                    val album = metadata.album?.takeIf { it.isNotBlank() }

                    _searchTitle.value = track
                    _searchArtist.value = artist
                    _searchAlbum.value = album

                    performLyricsSearch(track, artist, album)
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load audio file: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    private fun performLyricsSearch(track: String, artist: String?, album: String?) {
        val flowLyricsRepository = lyricsRepository as? LyricsRepositoryImpl ?: run {
            _errorMessage.value = "Lyrics repository not available"
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                _searchState.value = LyricsSearchState(isSearching = true)
                flowLyricsRepository.searchOnlineLyricsFlow(track, artist, album).collect { result ->
                    when (result) {
                        is LyricsSourceResult.Result -> {
                            val newResults = _searchState.value.results + result.lyrics
                            _searchState.update { it.copy(results = newResults) }
                            _lyricsResults.value = newResults
                            // Prefetch lyrics content in background (fire-and-forget)
                            prefetchLyricsContent(result.lyrics)
                        }

                        is LyricsSourceResult.SourceCompleted -> {
                            _searchState.update { state ->
                                state.copy(completedSources = state.completedSources + result.source)
                            }
                        }

                        is LyricsSourceResult.Error -> {
                            _searchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message)
                                )
                            }
                            _errorMessage.value = result.message
                        }
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Lyrics search failed"
                _errorMessage.value = message
                _searchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
            } finally {
                _searchState.update { it.copy(isSearching = false) }
                _isLoading.value = false
            }
        }
    }

    /**
     * Select lyrics and fetch the actual lyrics content.
     */
    fun selectLyrics(result: OnlineLyricsResult) {
        _selectedLyrics.value = result
    }

    /**
     * Prefetch lyrics content in background (fire-and-forget).
     * This downloads lyrics content when search results arrive,
     * so it's ready when user selects a result.
     */
    private fun prefetchLyricsContent(result: OnlineLyricsResult) {
        viewModelScope.launch {
            try {
                lyricsRepository.getOnlineLyrics(result)
                // Result is cached automatically in getOnlineLyrics()
            } catch (e: Exception) {
                // Silently fail - prefetch is best-effort
                Timber.d("Lyrics prefetch failed: ${e.message}")
            }
        }
    }

    /**
     * Get the lyrics content for the selected result.
     * Now uses cache-first approach (prefetched during search).
     */
    suspend fun getLyricsContent(result: OnlineLyricsResult): String? {
        return try {
            val lyrics = lyricsRepository.getOnlineLyrics(result).getOrNull()
            if (lyrics != null) {
                if (lyrics.isSynced) lyrics.toLrcFormat() else lyrics.text
            } else {
                null
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load lyrics: ${e.message}"
            null
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: OnlineLyricsSearch): OnlineLyricsSearchViewModel
    }
}
