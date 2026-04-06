package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.navigation.AlbumDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for AlbumDetailScreen.
 * Loads album data directly from AudioFileScanner's albums StateFlow.
 */
@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: AlbumDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider
) : ViewModel() {

    private val _albumName = MutableStateFlow("")
    val albumName: StateFlow<String> = _albumName.asStateFlow()

    private val _albumArtist = MutableStateFlow<String?>(null)
    val albumArtist: StateFlow<String?> = _albumArtist.asStateFlow()

    private val _albumYear = MutableStateFlow<String?>(null)
    val albumYear: StateFlow<String?> = _albumYear.asStateFlow()

    private val _albumBitrate = MutableStateFlow(0)
    val albumBitrate: StateFlow<Int> = _albumBitrate.asStateFlow()

    private val _albumSampleRate = MutableStateFlow(0)
    val albumSampleRate: StateFlow<Int> = _albumSampleRate.asStateFlow()

    private val _coverPath = MutableStateFlow<String?>(null)
    val coverPath: StateFlow<String?> = _coverPath.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Load album data from AudioFileScanner albums on init
        loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
    }

    /**
     * Load album data from AudioFileScanner by album name and artist.
     * Sample rate is loaded from cache (AlbumInfoEntity).
     * Year is loaded from cache for accurate and fast results.
     */
    fun loadAlbum(albumName: String, albumArtist: String?) {
        viewModelScope.launch {
            try {
                // Get albums from AudioFileScanner and find the matching one
                val albums = audioFileScanner.albums.first()
                val albumGroup = albums.find { album ->
                    album.name == albumName && album.artist == albumArtist
                }

                if (albumGroup != null) {
                    _albumName.value = albumGroup.name
                    _albumArtist.value = albumGroup.artist
                    _coverPath.value = albumGroup.coverPath
                    _files.value = albumGroup.files

                    // Try to load from AlbumInfo cache first
                    val albumInfo = databaseProvider.getDatabase()
                        .albumInfoDao()
                        .getAlbumInfo(albumName, albumArtist)

                    if (albumInfo != null) {
                        // Use cached album info
                        _albumYear.value = albumInfo.year
                        _albumSampleRate.value = albumInfo.sampleRate
                        _albumBitrate.value = albumInfo.bitrate
                        Timber.d("Loaded album info from cache: $albumName")
                    } else {
                        // Fallback: calculate from files
                        Timber.d("No cache found for album: $albumName, calculating from files")

                        val scannedAlbumYear = albumGroup.files
                            .mapNotNull { file -> file.metadata.year?.takeIf { it.isNotBlank() } }
                            .maxOrNull()

                        // Get bitrate and sample rate from files
                        val maxBitrate = albumGroup.files.maxOfOrNull { it.bitrate } ?: 0
                        val maxSampleRate = albumGroup.files.maxOfOrNull { it.sampleRate } ?: 0

                        _albumYear.value = scannedAlbumYear
                        _albumBitrate.value = maxBitrate
                        _albumSampleRate.value = maxSampleRate
                    }
                } else {
                    // Album not found - set basic info at least
                    _albumName.value = albumName
                    _albumArtist.value = albumArtist
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading album: $albumName")
            }
        }
    }

    /**
     * Refresh album data with optional full scan.
     */
    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _isRefreshing.value = true
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
                loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: AlbumDetail): AlbumDetailViewModel
    }
}
