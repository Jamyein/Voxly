package com.voxly.presentation.viewmodel

import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineLyricsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject

/**
 * Helper for lyrics search functionality in MetadataEditor.
 * Handles online lyrics search, results management, and lyrics application.
 *
 * Usage:
 * ```kotlin
 * // In MetadataEditorViewModel
 * private val lyricsSearchHelper = LyricsSearchHelper(lyricsRepository, onlineLyricsSearchStrategy)
 *
 * // Expose state from helper
 * val onlineLyricsResults = lyricsSearchHelper.onlineLyricsResults
 * val isOnlineLyricsLoading = lyricsSearchHelper.isOnlineLyricsLoading
 * val lyricsSearchState = lyricsSearchHelper.lyricsSearchState
 * ```
 */
class LyricsSearchHelper @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val onlineLyricsSearchStrategy: OnlineLyricsSearchStrategy
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _onlineLyricsResults = MutableStateFlow<List<OnlineLyricsResult>>(emptyList())
    val onlineLyricsResults: StateFlow<List<OnlineLyricsResult>> = _onlineLyricsResults.asStateFlow()

    private val _isOnlineLyricsLoading = MutableStateFlow(false)
    val isOnlineLyricsLoading: StateFlow<Boolean> = _isOnlineLyricsLoading.asStateFlow()

    private val _onlineLyricsError = MutableStateFlow<String?>(null)
    val onlineLyricsError: StateFlow<String?> = _onlineLyricsError.asStateFlow()

    private val _lyricsSearchState = MutableStateFlow(LyricsSearchState())
    val lyricsSearchState: StateFlow<LyricsSearchState> = _lyricsSearchState.asStateFlow()

    private var _lyricsSearchJob: Job? = null

    /**
     * Searches for online lyrics using track, artist, and album info.
     * @param track Track title
     * @param artist Artist name (optional)
     * @param album Album name (optional)
     */
    fun searchOnlineLyrics(track: String, artist: String?, album: String?) {
        _lyricsSearchJob?.cancel()

        _lyricsSearchJob = scope.launch {
            _lyricsSearchState.update { LyricsSearchState(isSearching = true) }
            _isOnlineLyricsLoading.update { true }
            _onlineLyricsError.update { null }
            _onlineLyricsResults.update { emptyList() }

            try {
                onlineLyricsSearchStrategy.search(track, artist, album).collect { result ->
                    when (result) {
                        is LyricsSearchResult.Result -> {
                            val newResults = _lyricsSearchState.value.results + result.lyrics
                            _lyricsSearchState.update { it.copy(results = newResults.toImmutableList()) }
                            _onlineLyricsResults.update { newResults }
                        }

                        is LyricsSearchResult.SourceCompleted -> {
                            _lyricsSearchState.update { state ->
                                state.copy(completedSources = (state.completedSources + result.source).toPersistentSet())
                            }
                        }

                        is LyricsSearchResult.Error -> {
                            _lyricsSearchState.update { state ->
                                state.copy(
                                    errorSources = (state.errorSources + (result.source to result.message)).toPersistentMap()
                                )
                            }
                            _onlineLyricsError.update { result.message }
                        }
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Lyrics search failed"
                _onlineLyricsError.update { message }
                _lyricsSearchState.update { state ->
                    state.copy(errorSources = (state.errorSources + ("System" to message)).toPersistentMap())
                }
            } finally {
                _lyricsSearchState.update { it.copy(isSearching = false) }
                _isOnlineLyricsLoading.update { false }
            }
        }
    }

    /**
     * Gets lyrics content for the given result.
     * @return Lyrics text (LRC format for synced lyrics, plain text otherwise)
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
            _onlineLyricsError.update { "Failed to load lyrics: ${e.message}" }
            null
        }
    }

    /**
     * Clears all lyrics search results and errors.
     */
    fun clearOnlineLyricsResults() {
        _onlineLyricsResults.update { emptyList() }
        _onlineLyricsError.update { null }
        _lyricsSearchState.update { LyricsSearchState() }
    }

    /**
     * Cancels any ongoing lyrics search.
     */
    fun cancelSearch() {
        _lyricsSearchJob?.cancel()
        _lyricsSearchJob = null
    }
}