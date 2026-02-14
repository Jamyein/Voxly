package com.voxly.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineMetadataViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val lyricsRepository: LyricsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val filePath: String = decodeNavArg(savedStateHandle.get<String>("filePath"))

    private val _uiState = MutableStateFlow<OnlineMetadataUiState>(OnlineMetadataUiState.Idle)
    val uiState: StateFlow<OnlineMetadataUiState> = _uiState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<OnlineRelease>>(emptyList())
    val searchResults: StateFlow<List<OnlineRelease>> = _searchResults.asStateFlow()

    private val _selectedRelease = MutableStateFlow<OnlineReleaseDetails?>(null)
    val selectedRelease: StateFlow<OnlineReleaseDetails?> = _selectedRelease.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow(OnlineSearchQuery())
    val searchQuery: StateFlow<OnlineSearchQuery> = _searchQuery.asStateFlow()

    private val _syncedLyricsByReleaseId = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    private var selectedSyncedLyrics: Lyrics? = null

    init {
        prepareAutoSearch()
    }

    private fun prepareAutoSearch() {
        viewModelScope.launch {
            val metadata = audioRepository.readMetadata(filePath).getOrNull()
            val fileName = File(filePath).nameWithoutExtension
            val parsed = parseFromFileName(fileName)

            val title = metadata?.title?.takeIf { it.isNotBlank() }
                ?: parsed.title
                ?: fileName.takeIf { it.isNotBlank() }
            val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
            val album = metadata?.album?.takeIf { it.isNotBlank() } ?: parsed.album

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
        searchInternal(_searchQuery.value) { query -> searchReleases(query) }
    }

    fun searchByArtistAlbum(artist: String, album: String) {
        val updated = _searchQuery.value.copy(artist = artist, album = album)
        _searchQuery.value = updated
        searchInternal(updated) {
            onlineMetadataRepository.searchByArtistAlbum(artist, album).getOrElse { emptyList() }
        }
    }

    fun searchByTrack(title: String, artist: String? = null) {
        val updated = _searchQuery.value.copy(title = title, artist = artist)
        _searchQuery.value = updated
        searchInternal(updated) {
            onlineMetadataRepository.searchByTrack(title, artist)
                .getOrElse { emptyList() }
                .mapNotNull { recording ->
                    recording.releaseId?.let { releaseId ->
                        OnlineRelease(
                            id = releaseId,
                            title = recording.title,
                            artist = recording.artist,
                            year = null,
                            format = null,
                            trackCount = null,
                            coverArtUrl = null,
                            source = recording.source,
                            songTitle = recording.title
                        )
                    }
                }
        }
    }

    private fun searchInternal(
        query: OnlineSearchQuery,
        searcher: suspend (OnlineSearchQuery) -> List<OnlineRelease>
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _uiState.value = OnlineMetadataUiState.Searching

                val releases = searcher(query)
                val withLyrics = enrichReleasesWithSyncedLyrics(releases)

                _searchResults.value = withLyrics
                _uiState.value = if (withLyrics.isEmpty()) {
                    OnlineMetadataUiState.NoResults
                } else {
                    OnlineMetadataUiState.Results(withLyrics)
                }
            } catch (e: Exception) {
                _uiState.value = OnlineMetadataUiState.Error(e.message ?: "Search failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun searchReleases(query: OnlineSearchQuery): List<OnlineRelease> {
        val merged = LinkedHashMap<String, OnlineRelease>()

        if (query.title.isNotBlank()) {
            onlineMetadataRepository.searchByTrack(query.title, query.artist)
                .onSuccess { recordings ->
                    recordings.forEach { recording ->
                        val releaseId = recording.releaseId ?: return@forEach
                        val incoming = OnlineRelease(
                            id = releaseId,
                            title = recording.title,
                            artist = recording.artist,
                            year = null,
                            format = null,
                            trackCount = null,
                            coverArtUrl = null,
                            source = recording.source,
                            songTitle = recording.title
                        )
                        merged[releaseId] = mergeRelease(merged[releaseId], incoming)
                    }
                }
        }

        if (!query.artist.isNullOrBlank() && !query.album.isNullOrBlank()) {
            onlineMetadataRepository.searchByArtistAlbum(query.artist, query.album)
                .onSuccess { releases ->
                    releases.forEach { release ->
                        val normalized = release.copy(albumTitle = release.albumTitle ?: release.title)
                        merged[release.id] = mergeRelease(merged[release.id], normalized)
                    }
                }
        }

        return merged.values.toList()
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

    private suspend fun enrichReleasesWithSyncedLyrics(releases: List<OnlineRelease>): List<OnlineRelease> =
        coroutineScope {
            val limited = releases.take(30)
            val deferred = limited.map { release ->
                async {
                    val track = release.songTitle ?: release.title
                    val artist = release.artist
                    val album = release.albumTitle ?: release.title
                    val match = lyricsRepository.searchOnlineLyrics(
                        trackName = track,
                        artistName = artist,
                        albumName = album
                    ).getOrElse { emptyList() }
                        .firstOrNull { it.hasSyncedLyrics }
                    if (match != null) {
                        val lyrics = lyricsRepository.getOnlineLyrics(match).getOrNull()
                        if (lyrics?.isSynced == true) {
                            release.id to lyrics
                        } else {
                            release.id to null
                        }
                    } else {
                        release.id to null
                    }
                }
            }

            val lyricsMap = deferred.map { it.await() }
                .mapNotNull { (id, lyrics) -> lyrics?.let { id to it } }
                .toMap()
            _syncedLyricsByReleaseId.value = lyricsMap

            releases.map { release ->
                release.copy(hasSyncedLyrics = lyricsMap[release.id] != null)
            }
        }

    fun selectRelease(release: OnlineRelease) {
        selectedSyncedLyrics = _syncedLyricsByReleaseId.value[release.id]
        viewModelScope.launch {
            _isLoading.value = true
            try {
                setRepositoryPreferredSource(release.source)
                val result = onlineMetadataRepository.getReleaseDetails(release.id)
                result.fold(
                    onSuccess = { details ->
                        _selectedRelease.value = details
                    },
                    onFailure = { error ->
                        _uiState.value = OnlineMetadataUiState.Error(
                            error.message ?: "Failed to get release details"
                        )
                    }
                )
            } finally {
                setRepositoryPreferredSource("Unknown")
                _isLoading.value = false
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
        val details = _selectedRelease.value ?: return null
        val lyrics = selectedSyncedLyrics
        return AudioMetadata(
            title = details.tracks.find { it.number == 1 }?.title ?: details.title,
            artist = details.artist,
            album = details.title,
            albumArtist = details.artist,
            year = details.year?.toString(),
            genre = details.genre,
            trackNumber = 1,
            totalTracks = details.trackCount,
            lyrics = lyrics?.toLrcFormat()
        )
    }

    fun clearSelection() {
        _selectedRelease.value = null
        selectedSyncedLyrics = null
    }

    private fun decodeNavArg(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains("%")) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun parseFromFileName(name: String): ParsedFileName {
        val cleaned = name.replace('_', ' ').trim()
        val split = cleaned.split(" - ", limit = 3).map { it.trim() }.filter { it.isNotEmpty() }
        return when (split.size) {
            2 -> ParsedFileName(artist = split[0], title = split[1], album = null)
            3 -> ParsedFileName(artist = split[0], album = split[1], title = split[2])
            else -> ParsedFileName(artist = null, title = cleaned.takeIf { it.isNotBlank() }, album = null)
        }
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

sealed class OnlineMetadataUiState {
    data object Idle : OnlineMetadataUiState()
    data object Searching : OnlineMetadataUiState()
    data object NoResults : OnlineMetadataUiState()
    data class Results(val releases: List<OnlineRelease>) : OnlineMetadataUiState()
    data class Error(val message: String) : OnlineMetadataUiState()
}
