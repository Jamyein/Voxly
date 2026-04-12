package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.repository.ArtistCacheRepository
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.navigation.ArtistDetail
import com.voxly.presentation.ui.extractAndCacheCoverBytes
import com.voxly.presentation.ui.loadAlbumArtThumbnail
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel for ArtistDetailScreen.
 * Loads artist data from memory cache or database.
 */
@HiltViewModel(assistedFactory = ArtistDetailViewModel.Factory::class)
class ArtistDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: ArtistDetail,
    @ApplicationContext private val context: Context,
    private val artistCacheRepository: ArtistCacheRepository,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var preloadJob: Job? = null
    private var refreshJob: Job? = null
    private var albumYearJob: Job? = null
    private var hasLoadedArtistData = false
    private val preloadMutex = kotlinx.coroutines.sync.Mutex()

    init {
        loadArtist(navKey.artistName)
    }

    /**
     * Load artist data by artist name.
     * First tries to get from cache, then falls back to database query.
     */
    fun loadArtist(artistName: String) {
        viewModelScope.launch {
            try {
                // Skip if already loaded for this artist
                if (hasLoadedArtistData && _artistName.value == artistName && _files.value.isNotEmpty()) {
                    return@launch
                }

                // First try to get from cache
                val cachedArtist = artistCacheRepository.getArtist(artistName)

                if (cachedArtist != null) {
                    _artistName.value = cachedArtist.name
                    _files.value = cachedArtist.files
                    _coverPath.value = cachedArtist.coverPath
                    _coverAlbumId.value = cachedArtist.files.firstOrNull { 
                        it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 
                    }?.mediaStoreAlbumId
                    calculateStats(cachedArtist.files)
                    if (!hasLoadedArtistData) {
                        precomputeAlbumCovers(cachedArtist.files)
                        loadAlbumYears(cachedArtist.files)
                    }
                    hasLoadedArtistData = true
                } else {
                    // Cache miss: look up from AudioFileScanner (source of truth)
                    val scannerArtist = audioFileScanner.artists.first()
                        .find { it.name.equals(artistName, ignoreCase = true) }

                    if (scannerArtist != null) {
                        // Populate cache and ViewModel state
                        cacheArtistData(scannerArtist.name, scannerArtist.files, scannerArtist.coverPath)
                    } else {
                        _artistName.value = artistName
                        _files.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _artistName.value = artistName
            }
        }
    }

    /**
     * Cache artist data for navigation.
     */
    fun cacheArtistData(artistName: String, files: List<AudioFile>, coverPath: String? = null) {
        val artistGroup = ArtistGroup(name = artistName, albums = emptyList(), files = files, coverPath = coverPath)
        artistCacheRepository.cacheArtist(artistGroup)

        // Also update ViewModel state
        _artistName.value = artistName
        _files.value = files
        _coverPath.value = coverPath
        _coverAlbumId.value = files.firstOrNull { 
            it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0 
        }?.mediaStoreAlbumId
        calculateStats(files)
        precomputeAlbumCovers(files)
        loadAlbumYears(files)
    }

    private fun calculateStats(files: List<AudioFile>) {
        // Calculate album count (distinct albums)
        val albums = files.mapNotNull { it.metadata.album }.distinct()
        _albumCount.value = albums.size

        // Calculate total duration
        _totalDuration.value = files.sumOf { it.duration }
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
                    _albumYears.value = emptyMap()
                    return@launch
                }

                val summaries = withContext(Dispatchers.IO) {
                    databaseProvider.getDatabase()
                        .albumSummaryDao()
                        .getAlbumSummariesByNames(albumNames)
                }

                _albumYears.value = summaries.associate { it.albumTitle to it.year }
            } catch (e: Exception) {
                Timber.e(e, "Error loading album years from view")
                _albumYears.value = emptyMap()
            }
        }
    }

    /**
     * 预计算专辑封面路径。
     * 修复：每封面仅调用一次MediaMetadataRetriever。
     * 使用extractAndCacheCoverBytes直接获取bytes，null表示无封面。
     */
    private fun precomputeAlbumCovers(files: List<AudioFile>) {
        viewModelScope.launch {
            val covers = withContext(Dispatchers.IO) {
                val albumGroups = files.groupBy { it.metadata.album ?: "" }
                albumGroups.mapNotNull { (albumName, albumFiles) ->
                    if (albumName.isEmpty()) return@mapNotNull null

                    // 找第一张有封面的文件（单次调用）
                    val fileWithArt = albumFiles.firstOrNull { file ->
                        try {
                            // extractAndCacheCoverBytes会写入Bytes Cache
                            extractAndCacheCoverBytes(file.path) != null
                        } catch (e: Exception) {
                            false
                        }
                    }

                    albumName to fileWithArt?.path
                }.toMap()
            }
            _albumCovers.value = covers

            // 封面计算完成后立即预加载第 0 页封面到 carouselCoverCache
            // 不依赖 UI recomposition，确保首次渲染时缓存已准备好
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
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Refresh artist data with optional full scan.
     */
    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
                loadArtist(navKey.artistName)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: ArtistDetail): ArtistDetailViewModel
    }
}
