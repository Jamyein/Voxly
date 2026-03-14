package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.presentation.navigation.OnlineCoverSearch
import com.voxly.presentation.ui.getCoverArtBytes
import com.voxly.presentation.ui.prefetchCoverArtBytes
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
import java.io.File

/**
 * ViewModel for online cover search screen.
 */
@HiltViewModel(assistedFactory = OnlineCoverSearchViewModel.Factory::class)
class OnlineCoverSearchViewModel @AssistedInject constructor(
    @Assisted val navKey: OnlineCoverSearch,
    @ApplicationContext private val context: android.content.Context,
    private val audioRepository: AudioRepository,
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository
) : ViewModel() {

    private val filePath: String = navKey.filePath

    // Search query info (exposed for UI)
    private val _searchTitle = MutableStateFlow("")
    val searchTitle: String get() = _searchTitle.value

    private val _searchArtist = MutableStateFlow<String?>(null)
    val searchArtist: String? get() = _searchArtist.value

    private val _coverResults = MutableStateFlow<List<OnlineRecording>>(emptyList())
    val coverResults: StateFlow<List<OnlineRecording>> = _coverResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchState = MutableStateFlow(CoverSearchState())
    val searchState: StateFlow<CoverSearchState> = _searchState.asStateFlow()

    private val _coverFetchMessage = MutableStateFlow<String?>(null)
    val coverFetchMessage: StateFlow<String?> = _coverFetchMessage.asStateFlow()

    /**
     * Search for cover art using the audio file's metadata.
     */
    fun search(path: String) {
        val targetPath = path.ifBlank { filePath }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _coverResults.value = emptyList()
            _searchState.value = CoverSearchState()
            _coverFetchMessage.value = null

            // Load audio file metadata
            val result = audioRepository.getAudioFile(targetPath)
            result.fold(
                onSuccess = { audioFile ->
                    val metadata = audioFile.metadata
                    val title = metadata.title?.takeIf { it.isNotBlank() } ?: File(targetPath).nameWithoutExtension
                    val artist = metadata.artist?.takeIf { it.isNotBlank() }

                    _searchTitle.value = title
                    _searchArtist.value = artist

                    performCoverSearch(title, artist)
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load audio file: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    private fun performCoverSearch(title: String, artist: String?) {
        viewModelScope.launch {
            _searchState.value = CoverSearchState(isSearching = true)
            _isLoading.value = true

            try {
                val result = aggregatedOnlineMetadataRepository.searchByTrackForCover(title, artist)
                result.fold(
                    onSuccess = { recordings ->
                        // Prefetch cover art bytes in background (fire-and-forget) for all results
                        recordings.forEach { recording ->
                            recording.coverArtUrl?.let { prefetchCoverArtBytes(it) }
                        }
                        _searchState.update { it.copy(results = recordings, isSearching = false) }
                        _coverResults.value = recordings
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Cover search failed"
                        _searchState.update { state ->
                            state.copy(errorSources = state.errorSources + ("System" to message))
                        }
                        _errorMessage.value = message
                        _searchState.update { it.copy(isSearching = false) }
                    }
                )
            } catch (e: Exception) {
                val message = e.message ?: "Cover search failed"
                _searchState.update { state ->
                    state.copy(errorSources = state.errorSources + ("System" to message))
                }
                _errorMessage.value = message
            } finally {
                _searchState.update { it.copy(isSearching = false) }
                _isLoading.value = false
            }
        }
    }

    /**
     * Apply the selected cover art asynchronously.
     * Returns the cover art bytes or null if not available.
     */
    suspend fun applyCover(recording: OnlineRecording): ByteArray? {
        // First try to get from existing cover URL
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            return try {
                getCoverArtBytes(existingCoverUrl)
            } catch (e: Exception) {
                _coverFetchMessage.value = "Failed to load cover: ${e.message}"
                null
            }
        }

        val releaseId = recording.releaseId
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.value = "无法获取封面：该结果没有关联的专辑信息"
            return null
        }

        val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
        return try {
            val targetSource = when (recording.source) {
                OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
            }
            aggregatedOnlineMetadataRepository.preferredSource = targetSource

            val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
            coverResult.fold(
                onSuccess = { cover ->
                    if (cover != null) {
                        _coverFetchMessage.value = "Cover fetched successfully"
                    } else {
                        _coverFetchMessage.value = "No online cover found"
                    }
                    cover
                },
                onFailure = { error ->
                    _coverFetchMessage.value = error.message ?: "Cover fetch failed"
                    null
                }
            )
        } finally {
            aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
        }
    }

    /**
     * Get cover art bytes for a recording synchronously (for immediate use).
     */
    suspend fun getCoverBytes(recording: OnlineRecording): ByteArray? {
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            // Use cache-first approach
            return getCoverArtBytes(existingCoverUrl)
        }

        val releaseId = recording.releaseId
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.value = "无法获取封面：该结果没有关联的专辑信息"
            return null
        }

        var resultBytes: ByteArray? = null

        val oldPreferred = aggregatedOnlineMetadataRepository.preferredSource
        try {
            val targetSource = when (recording.source) {
                OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
                OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
                OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
                OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
                else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
            }
            aggregatedOnlineMetadataRepository.preferredSource = targetSource

            val coverResult = aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
            coverResult.fold(
                onSuccess = { cover ->
                    resultBytes = cover
                    if (cover != null) {
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

        return resultBytes
    }

    fun clearCoverFetchMessage() {
        _coverFetchMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: OnlineCoverSearch): OnlineCoverSearchViewModel
    }
}
