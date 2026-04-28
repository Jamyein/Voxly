package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.OnlineSourceResult
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.AudioRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.OnlineMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.util.OnlineSearchSorter
import com.voxly.presentation.navigation.OnlineMetadata
import com.voxly.presentation.viewmodel.SearchSeedHolder
import com.voxly.presentation.ui.getCoverArtBytes
import com.voxly.presentation.ui.loadImageBytesFromUrl
import com.voxly.core.util.Constants
import com.voxly.presentation.ui.prefetchCoverArtBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.net.URLDecoder

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = OnlineMetadataViewModel.Factory::class)
class OnlineMetadataViewModel @AssistedInject constructor(
    @Assisted val navKey: OnlineMetadata,
    private val audioRepository: AudioRepository,
    private val onlineMetadataRepository: OnlineMetadataRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val searchSeedHolder: SearchSeedHolder,
    private val pendingMetadataHolder: PendingMetadataHolder
) : ViewModel() {

    private val filePath: String = navKey.filePath

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

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _syncedLyricsByReleaseId = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    private var selectedSyncedLyrics: Lyrics? = null
    private var activeSearchJob: Job? = null
    private var activeLyricsJob: Job? = null
    private var activeSelectReleaseJob: Job? = null
    private var activeSearchId: Long = 0L
    
    // 从设置中获取的元数据源优先级
    private var metadataSourcePriority: List<String> = emptyList()
    
    // 预下载的封面图
    private val _downloadedAlbumArt = MutableStateFlow<ByteArray?>(null)
    val downloadedAlbumArt: StateFlow<ByteArray?> = _downloadedAlbumArt.asStateFlow()

    // 标记封面下载是否超时（超时后允许应用没有封面的元数据）
    private val _isCoverArtTimeout = MutableStateFlow(false)
    val isCoverArtTimeout: StateFlow<Boolean> = _isCoverArtTimeout.asStateFlow()

    // 标记是否已为当前选择应用过元数据（防止 LaunchedEffect 多次触发）
    private val _hasAppliedForCurrentSelection = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // 监听元数据源优先级设置变化，实时更新
            // 使用 debounce 防止设置变化时触发频繁的搜索操作
            settingsDataStore.metadataSourcePriority
                .debounce(500)
                .collect { priority ->
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
            // 优先从 SearchSeedHolder 获取实时编辑值（未保存的修改）
            val seed = searchSeedHolder.peekSeed(filePath)

            val rawTitle: String?
            val rawArtist: String?
            val rawAlbum: String?

            if (seed != null) {
                // 使用编辑中的实时值
                rawTitle = seed.title.takeIf { it.isNotBlank() }
                rawArtist = seed.artist
                rawAlbum = seed.album
            } else {
                // 兜底：从文件读取
                val metadata = audioRepository.readMetadata(filePath).getOrNull()
                val fileName = File(filePath).nameWithoutExtension
                val parsed = parseFromFileName(fileName)
                rawTitle = metadata?.title?.takeIf { it.isNotBlank() }
                    ?: parsed.title
                    ?: fileName.takeIf { it.isNotBlank() }
                rawArtist = metadata?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist
                rawAlbum = metadata?.album?.takeIf { it.isNotBlank() } ?: parsed.album
            }

            val title = sanitizeSearchSeed(rawTitle) ?: rawTitle
            val artist = sanitizeSearchSeed(rawArtist) ?: rawArtist
            val album = sanitizeSearchSeed(rawAlbum) ?: rawAlbum

            _searchQuery.update {
                OnlineSearchQuery(
                    title = title.orEmpty(),
                    artist = artist,
                    album = album,
                    fromTags = seed != null || (!rawTitle.isNullOrBlank() && !rawArtist.isNullOrBlank() && !rawAlbum.isNullOrBlank())
                )
            }
            autoSearch()
        }
    }

    fun autoSearch() {
        val query = _searchQuery.value
        searchInternal(query, autoSearchFlow(query))
    }

    fun searchByArtistAlbum(artist: String, album: String) {
        val updated = _searchQuery.value.copy(artist = artist, album = album)
        _searchQuery.update { updated }
        searchInternal(updated, searchByArtistAlbumFlow(artist, album))
    }

    fun searchByTrack(title: String, artist: String? = null) {
        val updated = _searchQuery.value.copy(title = title, artist = artist)
        _searchQuery.update { updated }
        searchInternal(updated, searchByTrackFlow(title, artist))
    }

    private fun searchInternal(
        query: OnlineSearchQuery,
        searcher: Flow<OnlineSourceResult>
    ) {
        Timber.tag("Voxly").i("OnlineMetadata search started: title=${query.title}, artist=${query.artist}, album=${query.album}")
        activeSearchJob?.cancel()
        activeLyricsJob?.cancel()
        val searchId = nextSearchId()

        activeSearchJob = viewModelScope.launch {
            try {
                _syncedLyricsByReleaseId.update { emptyMap() }
                _searchState.update { SearchProgressState(isSearching = true) }
                publishLegacySearchState()

                searcher.collect { result ->
                    when (result) {
                        is OnlineSourceResult.ReleaseResult -> {
                            // Track that this source has started
                            _searchState.update { state ->
                                state.copy(startedSources = state.startedSources + result.source)
                            }

                            // Prefetch cover art bytes in background (fire-and-forget)
                            result.release.coverArtUrl?.let { prefetchCoverArtBytes(it) }

                            val normalized = result.release.copy(
                                albumTitle = result.release.albumTitle ?: result.release.title,
                                source = if (result.release.source == OnlineSource.UNKNOWN) result.source else result.release.source
                            )
                            _searchState.update { state ->
                                val merged = mergeRelease(state.results, normalized)
                                val sorted = OnlineSearchSorter.sortReleases(
                                    releases = merged,
                                    title = query.title,
                                    artist = query.artist,
                                    sourcePriority = metadataSourcePriority
                                )
                                state.copy(results = sorted, hasAnyResults = sorted.isNotEmpty())
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.RecordingResult -> {
                            // Track that this source has started
                            _searchState.update { state ->
                                state.copy(startedSources = state.startedSources + result.source)
                            }

                            // Prefetch cover art bytes in background (fire-and-forget)
                            result.recording.coverArtUrl?.let { prefetchCoverArtBytes(it) }

                            val release = result.recording.toOnlineRelease() ?: return@collect
                            _searchState.update { state ->
                                val merged = mergeRelease(state.results, release)
                                val sorted = OnlineSearchSorter.sortReleases(
                                    releases = merged,
                                    title = query.title,
                                    artist = query.artist,
                                    sourcePriority = metadataSourcePriority
                                )
                                state.copy(results = sorted, hasAnyResults = sorted.isNotEmpty())
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.SourceCompleted -> {
                            _searchState.update { state ->
                                state.copy(
                                    completedSources = state.completedSources + result.source,
                                    startedSources = state.startedSources + result.source
                                )
                            }
                            publishLegacySearchState()
                        }

                        is OnlineSourceResult.Error -> {
                            _searchState.update { state ->
                                state.copy(
                                    errorSources = state.errorSources + (result.source to result.message),
                                    startedSources = state.startedSources + result.source
                                )
                            }
                            publishLegacySearchState()
                        }
                    }
                }

                // 检查搜索是否已过时（用户触发了新搜索）
                if (isSearchOutdated(searchId)) {
                    // 标记搜索已完成，避免 UI 卡在搜索状态
                    _searchState.update { it.copy(isSearching = false, isLyricsSearching = false) }
                    publishLegacySearchState()
                    return@launch
                }

                _searchState.update { it.copy(isSearching = false, isLyricsSearching = true) }
                publishLegacySearchState()
                enrichReleasesWithSyncedLyricsIncremental(query, searchId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _searchState.update { state ->
                    state.copy(
                        isSearching = false,
                        isLyricsSearching = false,
                        errorSources = state.errorSources + (OnlineSource.UNKNOWN to (e.message ?: "Search failed"))
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
            source = if (old.source == OnlineSource.UNKNOWN) incoming.source else old.source,
            songTitle = old.songTitle ?: incoming.songTitle,
            albumTitle = old.albumTitle ?: incoming.albumTitle,
            discNumber = old.discNumber ?: incoming.discNumber,
            discCount = old.discCount ?: incoming.discCount
        )
    }

    private fun mergeRelease(results: List<OnlineRelease>, incoming: OnlineRelease): List<OnlineRelease> {
        val existingIndex = results.indexOfFirst { it.id == incoming.id }
        if (existingIndex < 0) return results + incoming

        val merged = mergeRelease(results[existingIndex], incoming)
        return results.toMutableList().also { it[existingIndex] = merged }
    }

    private fun publishLegacySearchState() {
        val state = _searchState.value
        _searchResults.update { state.results }
        _isLoading.update { state.isSearching || state.isLyricsSearching }

        _uiState.update {
            when {
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

                _syncedLyricsByReleaseId.update { emptyMap() }

                coroutineScope {
                    val deferred: List<kotlinx.coroutines.Deferred<Pair<String, Lyrics?>>> = limited.map { release ->
                        async {
                            release.id to fetchSyncedLyrics(release)
                        }
                    }

                    // Await all tasks in parallel, then batch update UI
                    val results: List<Pair<String, Lyrics?>> = deferred.awaitAll()
                    if (isSearchOutdated(searchId)) return@coroutineScope

                    // Batch process all results - filter out nulls
                    val updatedLyricsMap = mutableMapOf<String, Lyrics>()
                    results.forEach { (releaseId, lyrics) ->
                        if (lyrics != null) {
                            updatedLyricsMap[releaseId] = lyrics
                        }
                    }

                    // Single state update for all lyrics
                    if (updatedLyricsMap.isNotEmpty()) {
                        _syncedLyricsByReleaseId.update { current ->
                            current + updatedLyricsMap
                        }
                    }

                    // Single state update for UI
                    _searchState.update { state ->
                        val updatedResults = state.results.map { release ->
                            release.copy(hasSyncedLyrics = updatedLyricsMap.containsKey(release.id))
                        }
                        val sorted = OnlineSearchSorter.sortReleases(
                            releases = updatedResults,
                            title = query.title,
                            artist = query.artist,
                            sourcePriority = metadataSourcePriority
                        )
                        state.copy(
                            results = sorted,
                            hasAnyResults = updatedResults.isNotEmpty()
                        )
                    }
                    publishLegacySearchState()
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
                    emit(OnlineSourceResult.SourceCompleted(OnlineSource.UNKNOWN))
                }
                .onFailure { error ->
                    emit(OnlineSourceResult.Error(OnlineSource.UNKNOWN, error.message ?: "Failed"))
                    emit(OnlineSourceResult.SourceCompleted(OnlineSource.UNKNOWN))
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
                    emit(OnlineSourceResult.SourceCompleted(OnlineSource.UNKNOWN))
                }
                .onFailure { error ->
                    emit(OnlineSourceResult.Error(OnlineSource.UNKNOWN, error.message ?: "Failed"))
                    emit(OnlineSourceResult.SourceCompleted(OnlineSource.UNKNOWN))
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
                year = this.year,
                format = null,
                trackCount = null,
                coverArtUrl = coverArtUrl,
                source = source,
                songTitle = title,
                albumTitle = album,
                discNumber = discNumber,
                discCount = discCount,
                trackNumber = trackNumber,
                recordLabel = recordLabel,
                comment = comment,
                genre = genre,
                lyrics = lyrics
            )
        }
        return OnlineRelease(
            id = effectiveReleaseId,
            title = title,
            artist = artist,
            year = this.year,
            format = null,
            trackCount = null,
            coverArtUrl = coverArtUrl,
            source = source,
            songTitle = title,
            albumTitle = album,
            discNumber = discNumber,
            discCount = discCount,
            trackNumber = trackNumber,
            recordLabel = recordLabel,
            comment = comment,
            genre = genre,
            lyrics = lyrics
        )
    }

    private fun nextSearchId(): Long {
        activeSearchId += 1
        return activeSearchId
    }

    private fun isSearchOutdated(searchId: Long): Boolean = searchId != activeSearchId

    fun selectRelease(release: OnlineRelease) {
        Timber.d("selectRelease called: id=${release.id}, title=${release.title}, source=${release.source}")

        // 重置应用状态，允许新的选择触发自动回填
        _hasAppliedForCurrentSelection.update { false }

        // Cancel previous release selection coroutines before starting new ones
        activeSelectReleaseJob?.cancel()

        _selectedReleaseCandidate.update { release }
        _selectedRelease.update { null }
        selectedSyncedLyrics = _syncedLyricsByReleaseId.value[release.id]
        // 清除之前的封面图和超时标志
        _downloadedAlbumArt.update { null }
        _isCoverArtTimeout.update { false }

        Timber.d("selectRelease: candidate set, launching coroutines for details, cover and lyrics")

        // Create a parent job to track all three coroutines
        val parentJob = SupervisorJob()
        activeSelectReleaseJob = parentJob

        // Track if cover has been downloaded to avoid duplicate downloads
        var coverDownloaded = false

        // 并行启动三个协程：封面图、详情、歌词
        // 协程1：获取搜索结果中的封面图（优先缓存，带超时回退到下载）
        viewModelScope.launch(parentJob) {
            if (!release.coverArtUrl.isNullOrBlank()) {
                try {
                    // 优先从缓存获取，如果没有则下载，最多等待5秒
                    val cover = kotlinx.coroutines.withTimeoutOrNull(Constants.COVER_ART_TIMEOUT_MS) {
                        getCoverArtBytes(release.coverArtUrl)
                    }
                    if (cover != null) {
                        _downloadedAlbumArt.update { cover }
                        coverDownloaded = true
                        Timber.d("selectRelease: cover art loaded from cache, size=${cover.size}")
                    } else {
                        // 超时或下载失败
                        _isCoverArtTimeout.update { true }
                        Timber.w("selectRelease: cover art load timeout or failed for ${release.coverArtUrl}")
                    }
                } catch (e: Exception) {
                    _isCoverArtTimeout.update { true }
                    Timber.e(e, "selectRelease: cover art load error")
                }
            }
        }

        viewModelScope.launch(parentJob) {
            _isLoading.update { true }
            try {
                setRepositoryPreferredSource(release.source)
                Timber.d("selectRelease: calling getReleaseDetails for ${release.id}")
                val result = onlineMetadataRepository.getReleaseDetails(release.id)
                result.fold(
                    onSuccess = { details ->
                        Timber.d("selectRelease: got details, title=${details.title}, tracks=${details.tracks.size}")
                        _selectedRelease.update { details }
                        // 获取封面图（如果尚未下载）
                        val coverUrl = details.coverArtUrl ?: release.coverArtUrl
                        if (!coverUrl.isNullOrBlank() && !coverDownloaded) {
                            try {
                                val cover = kotlinx.coroutines.withTimeoutOrNull(Constants.COVER_ART_TIMEOUT_MS) {
                                    getCoverArtBytes(coverUrl)
                                }
                                if (cover != null) {
                                    _downloadedAlbumArt.update { cover }
                                    coverDownloaded = true
                                    Timber.d("selectRelease: cover art loaded, size=${_downloadedAlbumArt.value?.size}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "selectRelease: cover art load from details failed")
                            }
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get release details for ${release.id} from ${release.source}")
                        _errorMessage.emit("无法获取专辑详情，将应用基本信息 (来源: ${release.source})")
                        _selectedRelease.update { null }
                        // 即使获取详情失败，也尝试获取候选的封面图（如果尚未下载）
                        if (!release.coverArtUrl.isNullOrBlank() && !coverDownloaded) {
                            try {
                                val cover = kotlinx.coroutines.withTimeoutOrNull(Constants.COVER_ART_TIMEOUT_MS) {
                                    getCoverArtBytes(release.coverArtUrl)
                                }
                                _downloadedAlbumArt.update { cover }
                                coverDownloaded = true
                            } catch (e: Exception) {
                                Timber.e(e, "selectRelease: fallback cover art load failed")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception while getting release details for ${release.id}")
                _errorMessage.emit("获取专辑详情失败，将应用基本信息 (来源: ${release.source})")
                _selectedRelease.update { null }
            } finally {
                setRepositoryPreferredSource(OnlineSource.UNKNOWN)
                _isLoading.update { false }
                Timber.d("selectRelease: coroutine finished, isLoading=false")
            }
        }

        // 协程3：预加载歌词
        viewModelScope.launch(parentJob) {
            try {
                val lyrics = fetchSyncedLyrics(release)
                if (lyrics != null) {
                    _syncedLyricsByReleaseId.update {
                        _syncedLyricsByReleaseId.value.toMutableMap().apply {
                            put(release.id, lyrics)
                        }
                    }
                    // 如果是当前选中的候选，也更新 selectedSyncedLyrics
                    if (_selectedReleaseCandidate.value?.id == release.id) {
                        selectedSyncedLyrics = lyrics
                    }
                    Timber.d("selectRelease: lyrics preloaded for ${release.id}")
                }
            } catch (e: Exception) {
                Timber.w(e, "selectRelease: failed to preload lyrics for ${release.id}")
            }
        }
    }

    private fun setRepositoryPreferredSource(source: OnlineSource) {
        val repo = onlineMetadataRepository as? AggregatedOnlineMetadataRepository ?: return
        repo.preferredSource = when (source) {
            OnlineSource.MUSICBRAINZ -> AggregatedOnlineMetadataRepository.DataSource.MUSICBRAINZ
            OnlineSource.ITUNES -> AggregatedOnlineMetadataRepository.DataSource.ITUNES
            OnlineSource.NETEASE -> AggregatedOnlineMetadataRepository.DataSource.NETEASE
            OnlineSource.QQ_MUSIC -> AggregatedOnlineMetadataRepository.DataSource.QQ_MUSIC
            else -> AggregatedOnlineMetadataRepository.DataSource.BOTH
        }
    }

    /**
     * Get synced lyrics for the selected release.
     * Note: Lyrics should be preloaded in selectRelease, so this only reads from cache.
     */
    private fun getSyncedLyricsForSelected(): Lyrics? {
        val candidate = _selectedReleaseCandidate.value ?: return null

        // Get from cache (should be preloaded in selectRelease)
        return _syncedLyricsByReleaseId.value[candidate.id]
    }

    fun applyMetadata(): AudioMetadata? {
        if (_hasAppliedForCurrentSelection.value) {
            Timber.d("applyMetadata: already applied for current selection, skipping")
            return null
        }

        val details = _selectedRelease.value
        val candidate = _selectedReleaseCandidate.value
        
        val asyncLyrics = getSyncedLyricsForSelected()
        val albumArt = _downloadedAlbumArt.value

        val result = if (details != null) {
            AudioMetadata(
                title = details.tracks.find { it.number == 1 }?.title ?: details.title,
                artist = details.artist,
                album = details.title,
                albumArtist = details.artist,
                year = details.year?.toString(),
                genre = details.genre,
                trackNumber = details.tracks.firstOrNull()?.number,
                totalTracks = details.trackCount,
                discNumber = details.tracks.firstOrNull()?.discNumber ?: details.discNumber,
                totalDiscs = details.discCount,
                lyrics = asyncLyrics?.toLrcFormat(),
                albumArt = albumArt
            )
        } else if (candidate != null) {
            // 构建自定义字段
            val customFields = mutableMapOf<String, String>()
            candidate.recordLabel?.let { customFields["record_label"] = it }
            candidate.comment?.let { customFields["comment"] = it }

            // 优先使用同步获取的歌词（如果有），否则使用异步获取的歌词
            val lyricsText = candidate.lyrics ?: asyncLyrics?.toLrcFormat()

            AudioMetadata(
                title = candidate.songTitle ?: candidate.title,
                artist = candidate.artist,
                album = candidate.albumTitle ?: candidate.title,
                albumArtist = candidate.artist,
                year = candidate.year?.toString(),
                genre = candidate.genre,
                trackNumber = candidate.trackNumber,
                totalTracks = candidate.trackCount,
                discNumber = candidate.discNumber,
                totalDiscs = candidate.discCount,
                comment = candidate.comment,
                lyrics = lyricsText,
                albumArt = albumArt,
                customFields = customFields
            )
        } else {
            null
        }

        if (result != null) {
            _hasAppliedForCurrentSelection.update { true }
            pendingMetadataHolder.put(filePath, result)
            Timber.d("applyMetadata: marked current selection as applied and stored pending metadata")
        }

        return result
    }

    fun clearSelection() {
        _selectedRelease.update { null }
        _selectedReleaseCandidate.update { null }
        selectedSyncedLyrics = null
        _downloadedAlbumArt.update { null }

    }

    fun clearErrorMessage() {
        
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

    @AssistedFactory
    interface Factory {
        fun create(navKey: OnlineMetadata): OnlineMetadataViewModel
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
    val completedSources: Set<OnlineSource> = emptySet(),
    val errorSources: Map<OnlineSource, String> = emptyMap(),
    val startedSources: Set<OnlineSource> = emptySet(),  // 实际开始搜索的源
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
