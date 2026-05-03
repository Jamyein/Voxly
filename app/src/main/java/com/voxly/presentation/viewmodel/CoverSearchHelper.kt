package com.voxly.presentation.viewmodel

import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import javax.inject.Inject

/**
 * Helper for cover art search functionality in MetadataEditor.
 * Handles online cover search, results management, and cover application.
 */
class CoverSearchHelper @Inject constructor(
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository,
    private val coverSearchStrategy: CoverSearchStrategy
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _coverFetchMessage = MutableSharedFlow<String>()
    val coverFetchMessage: SharedFlow<String> = _coverFetchMessage.asSharedFlow()

    private val _onlineCoverResults = MutableStateFlow<List<OnlineRecording>>(emptyList())
    val onlineCoverResults: StateFlow<List<OnlineRecording>> = _onlineCoverResults.asStateFlow()

    private val _isOnlineCoverLoading = MutableStateFlow(false)
    val isOnlineCoverLoading: StateFlow<Boolean> = _isOnlineCoverLoading.asStateFlow()

    private val _onlineCoverError = MutableStateFlow<String?>(null)
    val onlineCoverError: StateFlow<String?> = _onlineCoverError.asStateFlow()

    private val _coverSearchState = MutableStateFlow(CoverSearchState())
    val coverSearchState: StateFlow<CoverSearchState> = _coverSearchState.asStateFlow()

    private var _coverSearchJob: Job? = null

    /**
     * Searches for online cover art using track title and artist.
     * @param title Track title
     * @param artist Artist name (optional)
     */
    fun searchOnlineCoverCandidates(title: String, artist: String?) {
        _coverSearchJob?.cancel()

        _coverSearchJob = scope.launch {
            _coverSearchState.update { CoverSearchState(isSearching = true) }
            _isOnlineCoverLoading.update { true }
            _onlineCoverError.update { null }
            _onlineCoverResults.update { emptyList() }

            try {
                val coverSearchResult = coverSearchStrategy.searchByTrack(title, artist)
                coverSearchResult.fold(
                    onSuccess = { recordings ->
                        recordings.forEach { recording ->
                            val newResults = _coverSearchState.value.results + recording
                            _coverSearchState.update { it.copy(results = newResults.toImmutableList()) }
                            _onlineCoverResults.update { newResults }
                        }
                        _coverSearchState.update { it.copy(isSearching = false) }
                    },
                    onFailure = { error ->
                        val message = error.message ?: "Cover search failed"
                        _coverSearchState.update { state ->
                            state.copy(errorSources = (state.errorSources + ("System" to message)).toPersistentMap())
                        }
                        _onlineCoverError.update { message }
                        _coverSearchState.update { it.copy(isSearching = false) }
                    }
                )
            } catch (e: Exception) {
                val message = e.message ?: "Cover search failed"
                _coverSearchState.update { state ->
                    state.copy(errorSources = (state.errorSources + ("System" to message)).toPersistentMap())
                }
                _onlineCoverError.update { message }
                _coverSearchState.update { it.copy(isSearching = false) }
            } finally {
                _isOnlineCoverLoading.update { false }
            }
        }
    }

    /**
     * Applies cover art from an online recording.
     * Downloads cover bytes from the recording's cover URL or release ID.
     * @param recording The online recording with cover art info
     * @return Cover art bytes or null if not available
     */
    suspend fun applyOnlineCover(recording: OnlineRecording): ByteArray? {
        // If coverArtUrl already exists in the recording, use it directly
        val existingCoverUrl = recording.coverArtUrl
        if (!existingCoverUrl.isNullOrBlank()) {
            return try {
                val bytes = downloadImageBytes(
                    url = existingCoverUrl,
                    userAgent = "Mozilla/5.0"
                )
                if (bytes.isNotEmpty()) {
                    _coverFetchMessage.emit("Cover fetched successfully")
                    bytes
                } else {
                    _coverFetchMessage.emit("Cover URL is invalid")
                    null
                }
            } catch (e: Exception) {
                _coverFetchMessage.emit("Failed to load cover: ${e.message}")
                null
            }
        }

        val releaseId = recording.releaseId
        
        // If no releaseId, show a message and return
        if (releaseId.isNullOrBlank()) {
            _coverFetchMessage.emit("无法获取封面：该结果没有关联的专辑信息")
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
                        _coverFetchMessage.emit("Cover fetched successfully")
                    } else {
                        _coverFetchMessage.emit("No online cover found")
                    }
                    cover
                },
                onFailure = {
                    _coverFetchMessage.emit(it.message ?: "Cover fetch failed")
                    null
                }
            )
        } finally {
            aggregatedOnlineMetadataRepository.preferredSource = oldPreferred
        }
    }

    /**
     * Clears cover fetch message.
     */
    fun clearCoverFetchMessage() {
        // Message is emitted via SharedFlow, no state to clear
    }

    /**
     * Clears all cover search results and errors.
     */
    fun clearOnlineCoverResults() {
        _onlineCoverResults.update { emptyList() }
        _onlineCoverError.update { null }
        _coverSearchState.update { CoverSearchState() }
    }

    /**
     * Cancels any ongoing cover search.
     */
    fun cancelSearch() {
        _coverSearchJob?.cancel()
        _coverSearchJob = null
    }
}