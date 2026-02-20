package com.voxly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.data.repository.OnlineSourceResult
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineMetadataViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsDataStore: SettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))

    private val _uiState = MutableStateFlow<OnlineMetadataUiState>(OnlineMetadataUiState.Idle)
    val uiState: StateFlow<OnlineMetadataUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<OnlineRelease>>(emptyList())
    val searchResults: StateFlow<List<OnlineRelease>> = _searchResults.asStateFlow()

    private val _searchState = MutableStateFlow(SearchProgressState())
    val searchState: StateFlow<SearchProgressState> = _searchState.asStateFlow()

    private val _selectedRelease = MutableStateFlow<OnlineReleaseDetails?>(null)
    val selectedRelease: StateFlow<OnlineReleaseDetails?> = _selectedRelease.asStateFlow()

    private val _selectedReleaseCandidate = MutableStateFlow<OnlineRelease?>(null)
    val selectedReleaseCandidate: StateFlow<OnlineRelease?> = _selectedReleaseCandidate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow(OnlineSearchQuery())
    val searchQuery: StateFlow<OnlineSearchQuery> = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _syncedLyricsByReleaseId = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    private var selectedSyncedLyrics: Lyrics? = null
    private var activeSearchJob: Job? = null
    private var activeLyricsJob: Job? = null
    private var activeSearchId: Long = 0L
    
    // 从设置中获取的元数据源优先级
    private var metadataSourcePriority: List<String> = emptyList()

    init {
        viewModelScope.launch {
            // 监听元数据源优先级设置变化，实时更新
            settingsDataStore.metadataSourcePriority.collect { priority ->
                metadataSourcePriority = priority
            }
        }
        viewModelScope.launch {
            // 初始化时获取元数据源优先级设置
            metadataSourcePriority = settingsDataStore.metadataSourcePriority.first()
            // 获取后再执行自动搜索，确保优先级设置已加载
            prepareAutoSearch()
        }
    }

    private fun prepareAutoSearch() {
        viewModelScope.launch {
            val metadata = audioRepository.readMetadata(filePath).getOrNull()
            val fileName = File(filePath).nameWithoutExtension
            val parsed = parseFromFileName(fileName)

            val rawTitle = metadata?.title?.takeIf { it.isNotBlank() }
                ?: parsed.title
                ?: fileName.takeIf { it.isNotBlank() }
            val rawArtist = metadata?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
            val rawAlbum = metadata?.album?.takeIf { it.isNotBlank() } ?: parsed.album

            val title = sanitizeSearchSeed(rawTitle) ?: rawTitle
            val artist = sanitizeSearchSeed(rawArtist) ?: rawArtist
            val album = sanitizeSearchSeed(rawAlbum) ?: rawAlbum

            _searchQuery.value = OnlineSearchQuery(
                title = title.orEmpty(),
                artist = artist,
                album = album,
                fromTags = !metadata?.title.isNullOrBlank() ||
                    !metadata?.artist.isNullOrBlank() ||
                    !metadata?.album.isNullOrBlank()
            )
            autoSearch()
        }
    }

    fun autoSearch() {
        val query = _searchQuery.value
        searchInternal(query, autoSearchFlow(query))
    }

    fun searchByArtistAlbum(artist: String, album: String) {
        val updated = _searchQuery.value.copy(artist = artist, album = album)
        _searchQuery.value = updated
        searchInternal(updated, searchByArtistAlbumFlow(artist, album))
    }

    fun searchByTrack(title: String, artist: String? = null) {
        val updated = _searchQuery.value.copy(title = title, artist = artist)
        _searchQuery.value = updated
        searchInternal(updated, searchByTrackFlow(title, artist))
    }

    private fun searchInternal(
        query: OnlineSearchQuery,
        searcher: Flow<OnlineSourceResult>
    ) {
        activeSearchJob?.cancel()
        activeLyricsJob?.cancel()
        val searchId = nextSearchId()

        activeSearchJob = viewModelScope.launch {
            try {
                _syncedLyricsByReleaseId.value = emptyMap()
                _searchState.value = SearchProgressState(isSearching = true)
                publishLegacySearchState()

                searcher.collect { result ->
                    when (result) {
                        is OnlineSourceResult.ReleaseResult -> {
                            val normalized = result.release.copy(
                                albumTitle = result.release.albumTitle ?: result.release.title,
                                source = if (result.release.source == "Unknown") result.source else result.release.source
                            )
                            _searchState.update { state ->
                                val merged = mergeRelease(state.results, normalized)
                                val sorted = sortReleases(merged, query)
                                state.copy(results = sorted, hasAnyResults = sorted.isNotEmpty())
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.RecordingResult -> {
                            val release = result.recording.toOnlineRelease() ?: return@collect
                            _searchState.update { state ->
                                val merged = mergeRelease(state.results, release)
                                val sorted = sortReleases(merged, query)
                                state.copy(results = sorted, hasAnyResults = sorted.isNotEmpty())
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.SourceCompleted -> {
                            _searchState.update { state ->
                                state.copy(completedSources = state.completedSources + result.source)
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.Error -> {
                            _searchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message)
                                )
                            }
                            publishLegacySearchState()
                        }
                    }
                }

                if (isSearchOutdated(searchId)) return@launch

                _searchState.update { it.copy(isSearching = false, isLyricsSearching = true) }
                publishLegacySearchState()
                enrichReleasesWithSyncedLyricsIncremental(query, searchId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _searchState.update { state ->
                    state.copy(
                        isSearching = false,
                        isLyricsSearching = false,
                        errorSources = state.errorSources + ("System" to (e.message ?: "Search failed"))
                    )
                }
                publishLegacySearchState()
            }
        }
    }

    private fun autoSearchFlow(query: OnlineSearchQuery): Flow<OnlineSourceResult> = flow {
        if (query.title.isNotBlank()) {
            emitAll(searchByTrackFlow(query.title, query.artist))
        }
        if (!query.artist.isNullOrBlank() && !query.album.isNullOrBlank()) {
            emitAll(searchByArtistAlbumFlow(query.artist, query.album))
        }
    }

    private fun mergeRelease(old: OnlineRelease?, incoming: OnlineRelease): OnlineRelease {
        if (old == null) return incoming
        return old.copy(
            title = if (old.title.isBlank()) incoming.title else old.title,
            artist = if (old.artist.isBlank()) incoming.artist else old.artist,
            year = old.year ?: incoming.year,
            format = old.format ?: incoming.format,
            trackCount = old.trackCount ?: incoming.trackCount,
            coverArtUrl = old.coverArtUrl ?: incoming.coverArtUrl,
            source = if (old.source == "Unknown") incoming.source else old.source,
            songTitle = old.songTitle ?: incoming.songTitle,
            albumTitle = old.albumTitle ?: incoming.albumTitle
        )
    }

    private fun mergeRelease(results: List<OnlineRelease>, incoming: OnlineRelease): List<OnlineRelease> {
        val existingIndex = results.indexOfFirst { it.id == incoming.id }
        if (existingIndex < 0) return results + incoming

        val merged = mergeRelease(results[existingIndex], incoming)
        return results.toMutableList().also { it[existingIndex] = merged }
    }

    private fun sortReleases(releases: List<OnlineRelease>, query: OnlineSearchQuery): List<OnlineRelease> {
        val titleNeedle = when {
            query.title.isNotBlank() -> query.title
            !query.album.isNullOrBlank() -> query.album
            else -> ""
        }

        return releases.sortedWith(
            compareByDescending<OnlineRelease> { release ->
                sourcePriorityIndex(release.source, metadataSourcePriority)
            }.thenByDescending { release ->
                val candidateTitle = (release.songTitle ?: release.albumTitle ?: release.title).trim()
                when {
                    titleNeedle.isBlank() -> 1
                    candidateTitle.equals(titleNeedle, ignoreCase = true) -> 3
                    candidateTitle.contains(titleNeedle, ignoreCase = true) -> 2
                    else -> 1
                }
            }.thenByDescending { release ->
                val artistNeedle = query.artist.orEmpty()
                when {
                    artistNeedle.isBlank() -> 1
                    release.artist.equals(artistNeedle, ignoreCase = true) -> 3
                    release.artist.contains(artistNeedle, ignoreCase = true) -> 2
                    else -> 1
                }
            }
        )
    }

    /**
     * 根据设置中的元数据源优先级计算排序索引
     */
    private fun sourcePriorityIndex(source: String, priority: List<String>): Int {
        // 标准化 source 名称
        val normalizedSource = when (source.lowercase()) {
            "itunes" -> "itunes"
            "musicbrainz" -> "musicbrainz"
            "netease" -> "netease"
            "qq music", "qq_music" -> "qq_music"
            else -> source.lowercase()
        }
        
        // 在优先级列表中查找索引
        val index = priority.indexOfFirst { it.equals(normalizedSource, ignoreCase = true) }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private fun publishLegacySearchState() {
        val state = _searchState.value
        _searchResults.value = state.results
        _isLoading.value = state.isSearching || state.isLyricsSearching

        _uiState.value = when {
            state.isSearching && state.results.isEmpty() -> OnlineMetadataUiState.Searching
            (state.isSearching || state.isLyricsSearching) && state.results.isNotEmpty() -> {
                OnlineMetadataUiState.PartialResults(state.results)
            }
            state.results.isNotEmpty() -> OnlineMetadataUiState.Results(state.results)
            state.errorSources.isNotEmpty() -> {
                OnlineMetadataUiState.Error(
                    state.errorSources.values.firstOrNull() ?: "Search failed"
                )
            }
            !state.isSearching && !state.isLyricsSearching -> OnlineMetadataUiState.NoResults
            else -> OnlineMetadataUiState.Searching
        }
    }

    private fun enrichReleasesWithSyncedLyricsIncremental(
        query: OnlineSearchQuery,
        searchId: Long
    ) {
        activeLyricsJob?.cancel()
        activeLyricsJob = viewModelScope.launch {
            try {
                val limited = _searchState.value.results.take(30)
                if (limited.isEmpty()) {
                    if (!isSearchOutdated(searchId)) {
                        _searchState.update { it.copy(isLyricsSearching = false) }
                        publishLegacySearchState()
                    }
                    return@launch
                }

                _syncedLyricsByReleaseId.value = emptyMap()

                coroutineScope {
                    val deferred = limited.map { release ->
                        async {
                            release.id to fetchSyncedLyrics(release)
                        }
                    }

                    deferred.forEach { task ->
                        val (releaseId, lyrics) = task.await()
                        if (isSearchOutdated(searchId)) return@forEach

                        if (lyrics != null) {
                            _syncedLyricsByReleaseId.update { current ->
                                current + (releaseId to lyrics)
                            }
                        }

                        _searchState.update { state ->
                            val updatedResults = state.results.map { release ->
                                if (release.id == releaseId) {
                                    release.copy(hasSyncedLyrics = lyrics != null)
                                } else {
                                    release
                                }
                            }
                            state.copy(
                                results = sortReleases(updatedResults, query),
                                hasAnyResults = updatedResults.isNotEmpty()
                            )
                        }
                        publishLegacySearchState()
                    }
                }
            } finally {
                if (!isSearchOutdated(searchId)) {
                    _searchState.update { it.copy(isLyricsSearching = false) }
                    publishLegacySearchState()
                }
            }
        }
    }

    private suspend fun fetchSyncedLyrics(release: OnlineRelease): Lyrics? {
        val track = release.songTitle ?: release.title
        val artist = release.artist
        val album = release.albumTitle ?: release.title
        val match = lyricsRepository.searchOnlineLyrics(
            trackName = track,
            artistName = artist,
            albumName = album
        ).getOrElse { emptyList() }
            .firstOrNull { it.hasSyncedLyrics }

        if (match == null) return null
        val lyrics = lyricsRepository.getOnlineLyrics(match).getOrNull() ?: return null
        return lyrics.takeIf { it.isSynced }
    }

    private fun searchByArtistAlbumFlow(artist: String, album: String): Flow<OnlineSourceResult> {
        val aggregatedRepo = onlineMetadataRepository as? AggregatedOnlineMetadataRepository
        if (aggregatedRepo != null) {
            return aggregatedRepo.searchByArtistAlbumFlow(artist, album)
        }
        return flow {
            onlineMetadataRepository.searchByArtistAlbum(artist, album)
                .onSuccess { releases ->
                    releases.forEach { emit(OnlineSourceResult.ReleaseResult(it, it.source)) }
                    emit(OnlineSourceResult.SourceCompleted("Unknown"))
                }
                .onFailure { error ->
                    emit(OnlineSourceResult.Error("Unknown", error.message ?: "Failed"))
                    emit(OnlineSourceResult.SourceCompleted("Unknown"))
                }
        }
    }

    private fun searchByTrackFlow(title: String, artist: String?): Flow<OnlineSourceResult> {
        val aggregatedRepo = onlineMetadataRepository as? AggregatedOnlineMetadataRepository
        if (aggregatedRepo != null) {
            return aggregatedRepo.searchByTrackFlow(title, artist)
        }
        return flow {
            onlineMetadataRepository.searchByTrack(title, artist)
                .onSuccess { recordings ->
                    recordings.forEach { emit(OnlineSourceResult.RecordingResult(it, it.source)) }
                    emit(OnlineSourceResult.SourceCompleted("Unknown"))
                }
                .onFailure { error ->
                    emit(OnlineSourceResult.Error("Unknown", error.message ?: "Failed"))
                    emit(OnlineSourceResult.SourceCompleted("Unknown"))
                }
        }
    }

    private fun OnlineRecording.toOnlineRelease(): OnlineRelease? {
        // Use releaseId if available, otherwise use recording id as fallback
        val effectiveReleaseId = releaseId ?: id.takeIf { it.isNotBlank() }
        if (effectiveReleaseId.isNullOrBlank()) {
            // If no releaseId and no recording id, we can't create a meaningful OnlineRelease
            // Return a minimal release for display purposes
            return OnlineRelease(
                id = "unknown-${System.nanoTime()}",
                title = title,
                artist = artist,
                year = null,
                format = null,
                trackCount = null,
                coverArtUrl = coverArtUrl,
                source = source,
                songTitle = title
            )
        }
        return OnlineRelease(
            id = effectiveReleaseId,
            title = title,
            artist = artist,
            year = null,
            format = null,
            trackCount = null,
            coverArtUrl = coverArtUrl,
            source = source,
            songTitle = title
        )
    }

    private fun nextSearchId(): Long {
        activeSearchId += 1
        return activeSearchId
    }

    private fun isSearchOutdated(searchId: Long): Boolean = searchId != activeSearchId

    fun selectRelease(release: OnlineRelease) {
        Timber.d("selectRelease called: id=${release.id}, title=${release.title}, source=${release.source}")
        _selectedReleaseCandidate.value = release
        _selectedRelease.value = null
        selectedSyncedLyrics = _syncedLyricsByReleaseId.value[release.id]
        Timber.d("selectRelease: candidate set, launching coroutine for details")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                setRepositoryPreferredSource(release.source)
                Timber.d("selectRelease: calling getReleaseDetails for ${release.id}")
                val result = onlineMetadataRepository.getReleaseDetails(release.id)
                result.fold(
                    onSuccess = { details ->
                        Timber.d("selectRelease: got details, title=${details.title}, tracks=${details.tracks.size}")
                        _selectedRelease.value = details
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get release details for ${release.id} from ${release.source}")
                        _errorMessage.value = "无法获取专辑详情，将应用基本信息 (来源: ${release.source})"
                        _selectedRelease.value = null
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception while getting release details for ${release.id}")
                _errorMessage.value = "获取专辑详情失败，将应用基本信息 (来源: ${release.source})"
                _selectedRelease.value = null
            } finally {
                setRepositoryPreferredSource("Unknown")
                _isLoading.value = false
                Timber.d("selectRelease: coroutine finished, isLoading=false")
            }
        }
    }

    private fun setRepositoryPreferredSource(source: String) {
        val repo = onlineMetadataRepository as? AggregatedOnlineMetadataRepository ?: return
        repo.preferredSource = when (source) {
            "MusicBrainz" -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
            "iTunes" -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
            "NetEase" -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
            "QQ Music" -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
            else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
        }
    }

    fun applyMetadata(): AudioMetadata? {
        val details = _selectedRelease.value
        val candidate = _selectedReleaseCandidate.value
        val lyrics = selectedSyncedLyrics

        // 详情存在时使用详情，详情不存在时使用候选，二选一不混合
        return if (details != null) {
            AudioMetadata(
                title = details.tracks.find { it.number == 1 }?.title ?: details.title,
                artist = details.artist,
                album = details.title,
                albumArtist = details.artist,
                year = details.year?.toString(),
                genre = details.genre,
                trackNumber = details.tracks.firstOrNull()?.number ?: 1,
                totalTracks = details.trackCount,
                lyrics = lyrics?.toLrcFormat()
            )
        } else if (candidate != null) {
            AudioMetadata(
                title = candidate.songTitle ?: candidate.title,
                artist = candidate.artist,
                album = candidate.albumTitle ?: candidate.title,
                albumArtist = candidate.artist,
                year = candidate.year?.toString(),
                genre = null,
                trackNumber = 1,
                totalTracks = candidate.trackCount,
                lyrics = lyrics?.toLrcFormat()
            )
        } else {
            null
        }
    }

    fun clearSelection() {
        _selectedRelease.value = null
        _selectedReleaseCandidate.value = null
        selectedSyncedLyrics = null
        _errorMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%') && !raw.contains('+')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun parseFromFileName(name: String): ParsedFileName {
        val cleaned = sanitizeSearchSeed(name).orEmpty()
        val split = cleaned
            .split(Regex("\\s*[-\u2013\u2014]\\s*"), limit = 3)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return when (split.size) {
            2 -> ParsedFileName(artist = split[0], title = split[1], album = null)
            3 -> ParsedFileName(artist = split[0], album = split[1], title = split[2])
            else -> ParsedFileName(artist = null, title = cleaned.takeIf { it.isNotBlank() }, album = null)
        }
    }

    private fun sanitizeSearchSeed(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val cleaned = value
            .replace('_', ' ')
            .replace('.', ' ')
            .replace(Regex("^\\s*\\d{1,3}[\\s._-]+"), "")
            .replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.takeIf { it.length >= 2 }
    }
}

data class OnlineSearchQuery(
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val fromTags: Boolean = false
)

private data class ParsedFileName(
    val artist: String?,
    val title: String?,
    val album: String?
)

data class SearchProgressState(
    val results: List<OnlineRelease> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false,
    val isLyricsSearching: Boolean = false,
    val hasAnyResults: Boolean = false
)

sealed class OnlineMetadataUiState {
    data object Idle : OnlineMetadataUiState()
    data object Searching : OnlineMetadataUiState()
    data class PartialResults(val releases: List<OnlineRelease>) : OnlineMetadataUiState()
    data object NoResults : OnlineMetadataUiState()
    data class Results(val releases: List<OnlineRelease>) : OnlineMetadataUiState()
    data class Error(val message: String) : OnlineMetadataUiState()
}
