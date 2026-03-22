package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
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

/**
 * ViewModel for AlbumDetailScreen.
 * Loads album data directly from AudioFileScanner's albums StateFlow.
 */
@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: AlbumDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner
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
     * Sample rate is loaded from file tags (MediaStore doesn't provide this).
     * Year is loaded from file tags for accurate results.
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

                    val scannedAlbumYear = albumGroup.files
                        .mapNotNull { file -> file.metadata.year?.takeIf { it.isNotBlank() } }
                        .maxOrNull()

                    // Get bitrate from first file (MediaStore provides this)
                    albumGroup.files.firstOrNull()?.let { firstFile ->
                        _albumBitrate.value = firstFile.bitrate

                        // Load audio properties for sample rate (MediaStore doesn't provide this)
                        val audioProperties = audioFileScanner.loadAudioProperties(firstFile.path)
                        _albumSampleRate.value = audioProperties?.sampleRate ?: 0

                        // Prefer the scanner year used by album sorting; only fall back to TagLib when absent.
                        _albumYear.value = scannedAlbumYear ?: audioFileScanner
                            .loadDetailedMetadata(firstFile.path)
                            ?.year
                    }
                } else {
                    // Album not found - set basic info at least
                    _albumName.value = albumName
                    _albumArtist.value = albumArtist
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
