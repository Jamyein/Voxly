package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.repository.ChangeSource
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.repository.LibraryRepository
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.navigation.ArtistDetail
import com.voxly.presentation.ui.extractAndCacheCoverBytes
import com.voxly.presentation.ui.loadAlbumArtThumbnail
import com.voxly.core.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * ViewModel for ArtistDetailScreen.
 * Loads artist data directly from AudioFileScanner.artists (single source of truth).
 */
@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: ArtistDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val libraryDataHolder: LibraryDataHolder,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _artistName = MutableStateFlow("")
    val artistName: StateFlow<String> = _artistName.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files.asStateFlow()

    private val _albumCount = MutableStateFlow(0)
    val albumCount: StateFlow<Int> = _albumCount.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration: StateFlow<Long> = _totalDuration.asStateFlow()

    private val _coverPath = MutableStateFlow<String?>(null)
    val coverPath: StateFlow<String?> = _coverPath.asStateFlow()

    private val _coverAlbumId = MutableStateFlow<Long?>(null)
    val coverAlbumId: StateFlow<Long?> = _coverAlbumId.asStateFlow()

    private val _albumCovers = MutableStateFlow<Map<String, String?>>(emptyMap())
    val albumCovers: StateFlow<Map<String, String?>> = _albumCovers.asStateFlow()

    private val _albumYears = MutableStateFlow<Map<String, String?>>(emptyMap())
    val albumYears: StateFlow<Map<String, String?>> = _albumYears.asStateFlow()

    /**
     * Mirrors the global scan activity maintained by [LibraryDataHolder].
     * A VM created mid-scan picks up the current spinner state immediately
     * on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing

    private var preloadJob: Job? = null
    private var refreshJob: Job? = null
    private var albumYearJob: Job? = null
    private var autoScrollJob: Job? = null
    private val preloadMutex = kotlinx.coroutines.sync.Mutex()

    private val _autoScrollTarget = MutableStateFlow<Int?>(null)
    val autoScrollTarget: StateFlow<Int?> = _autoScrollTarget.asStateFlow()

    init {
        // Pre-populate from navKey so UI shows correct artist name from first frame
        _artistName.update { navKey.artistName }
        loadArtist(navKey.artistName)
    }

    /**
     * Load artist data by artist name.
     * Gets data directly from AudioFileScanner.artists (single source of truth).
     */
    fun loadArtist(artistName: String) {
        Timber.tag("Voxly").i("ArtistDetailViewModel loadArtist: artistId=$artistName")
        if (_artistName.value == artistName && _files.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            try {

                val scannerArtist = audioFileScanner.artists.first()
                    .find { it.name.equals(artistName, ignoreCase = true) }

                if (scannerArtist != null) {
                    _artistName.update { scannerArtist.name }
                    _files.update { scannerArtist.files }
                    _coverPath.update { scannerArtist.coverPath }
                    _coverAlbumId.update { scannerArtist.files.firstOrNull {
                        it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
                    }?.mediaStoreAlbumId }
                    calculateStats(scannerArtist.files)
                    loadAlbumYears(scannerArtist.files)
                } else {
                    _artistName.update { artistName }
                    _files.update { emptyList() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading artist: $artistName")
                _artistName.update { artistName }
            }
        }
    }

    /**
     * Update ViewModel state with artist data.
     * No manual caching needed - data comes from AudioFileScanner.artists.
     */
    fun cacheArtistData(artistName: String, files: List<AudioFile>, coverPath: String? = null) {
        _artistName.update { artistName }
        _files.update { files }
        _coverPath.update { coverPath }
        _coverAlbumId.update { files.firstOrNull {
            it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
        }?.mediaStoreAlbumId }
        calculateStats(files)
        precomputeAlbumCovers(files)
        loadAlbumYears(files)
    }

    private fun calculateStats(files: List<AudioFile>) {
        // Calculate album count (distinct albums)
        val albums = files.mapNotNull { it.metadata.album }.distinct()
        _albumCount.update { albums.size }

        // Calculate total duration
        _totalDuration.update { files.sumOf { it.duration } }
    }

    /**
     * Load album years from album_summary_view.
     */
    private fun loadAlbumYears(files: List<AudioFile>) {
        albumYearJob?.cancel()
        albumYearJob = viewModelScope.launch {
            try {
                val albumNames = files.mapNotNull { it.metadata.album }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (albumNames.isEmpty()) {
                    _albumYears.update { emptyMap() }
                    return@launch
                }

                val summaries = databaseProvider.getDatabase()
                        .albumSummaryDao()
                        .getAlbumSummariesByNames(albumNames)

                _albumYears.update { summaries.associate { it.albumTitle to it.year } }
            } catch (e: Exception) {
                Timber.e(e, "Error loading album years from view")
                _albumYears.update { emptyMap() }
            }
        }
    }

    /**
     * Precompute album cover paths for the carousel.
     * Deferred until carousel needs covers - not called on every loadArtist.
     * Uses extractAndCacheCoverBytes which has LRU byte caching.
     */
    private fun precomputeAlbumCovers(files: List<AudioFile>) {
        viewModelScope.launch {
            val covers = withContext(Dispatchers.IO) {
                val albumGroups = files.groupBy { it.metadata.album ?: "" }
                albumGroups.mapNotNull { (albumName, albumFiles) ->
                    if (albumName.isEmpty()) return@mapNotNull null

                    val fileWithArt = albumFiles.firstOrNull { file ->
                        try {
                            extractAndCacheCoverBytes(file.path) != null
                        } catch (e: Exception) {
                            false
                        }
                    }

                    albumName to fileWithArt?.path
                }.toMap()
            }
            _albumCovers.update { covers }

            preloadAdjacentAlbumCovers(0)
        }
    }

    /**
     * 预加载相邻专辑封面（currentPage ± 1）。
     * 并发保护：使用Mutex确保同时只有一个预加载任务执行。
     */
    fun preloadAdjacentAlbumCovers(currentPage: Int) {
        val albumList = _albumCovers.value.keys.toList()
        if (albumList.isEmpty()) return

        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            preloadMutex.lock()
            try {
                withContext(Dispatchers.IO) {
                    val indices = listOf(currentPage - 1, currentPage, currentPage + 1)
                        .filter { it in albumList.indices }

                    indices.forEach { index ->
                        val albumName = albumList[index]
                        val path = _albumCovers.value[albumName]
                        if (path != null) {
                            loadAlbumArtThumbnail(context, path)
                        }
                    }
                }
            } finally {
                preloadMutex.unlock()
            }
        }
    }

    /**
     * Get formatted total duration string.
     */
    fun getFormattedDuration(): String {
        val duration = _totalDuration.value
        val hours = duration / Constants.MS_PER_HOUR
        val minutes = (duration % Constants.MS_PER_HOUR) / Constants.MS_PER_MINUTE
        val seconds = (duration % Constants.MS_PER_MINUTE) / Constants.MS_PER_SECOND
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Schedule auto-scroll carousel to next album after 4s delay.
     * Cancels any pending auto-scroll before scheduling a new one.
     */
    fun scheduleAutoScroll(currentItem: Int, albumCount: Int) {
        autoScrollJob?.cancel()
        autoScrollJob = viewModelScope.launch {
            if (albumCount <= 1) return@launch
            delay(4000)
            _autoScrollTarget.update { (currentItem + 1) % albumCount }
        }
    }

    /**
     * Refresh artist data. Routes the scan request through [LibraryRepository]
     * (single fan-in) so that concurrent refreshes from other screens collapse
     * into one scan via the repository's conflated SharedFlow + the collector's
     * `collectLatest`. The local `loadArtist` re-read picks up the new
     * aggregator output as soon as the incremental rebuild finishes — typically
     * within one frame of the scan completing.
     */
    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // bypassVersionCache = true: user-initiated refresh should always
            // trigger a real scan attempt, not short-circuit on MediaStore
            // version equality.
            libraryRepository.refresh(
                forceRefresh = forceRefresh,
                bypassVersionCache = true,
                source = ChangeSource.PULL_TO_REFRESH
            )
            loadArtist(navKey.artistName)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: ArtistDetail): ArtistDetailViewModel
    }
}
