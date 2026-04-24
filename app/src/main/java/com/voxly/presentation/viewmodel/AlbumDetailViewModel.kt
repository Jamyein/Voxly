package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.presentation.navigation.AlbumDetail
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
import timber.log.Timber

@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: AlbumDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val metadataProcessor: TagLibMetadataProcessor
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
    private val tagLibReadCache = mutableMapOf<String, AudioMetadata>()

    init {
        // Load album data from AudioFileScanner albums on init
        loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
    }

    /**
     * Load album data from AudioFileScanner by album name and artist.
     * Year and sample rate are loaded from album_summary_view for fast aggregation.
     * Bitrate is calculated from file metadata.
     * For files with missing discNumber, uses TagLib to read from file tags.
     */
    fun loadAlbum(albumName: String, albumArtist: String?) {
        if (_albumName.value == albumName && _albumArtist.value == albumArtist && _files.value.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                val albums = audioFileScanner.albums.first()
                val albumGroup = albums.find { album ->
                    album.name == albumName && (albumArtist == null || album.albumArtist == albumArtist)
                }

                if (albumGroup != null) {
                    _albumName.update { albumGroup.name }
                    _coverPath.update { albumGroup.coverPath }

                    val filesWithDiscNumber = withContext(Dispatchers.Default) {
                        albumGroup.files.map { file ->
                            if (file.metadata.discNumber == null) {
                                val cached = tagLibReadCache.getOrPut(file.path) {
                                    metadataProcessor.readMetadata(file.path) ?: file.metadata
                                }
                                if (cached.discNumber != null) {
                                    file.copy(metadata = file.metadata.copy(discNumber = cached.discNumber))
                                } else {
                                    file
                                }
                            } else {
                                file
                            }
                        }
                    }

                    _files.update { filesWithDiscNumber }

                    // Query year and sampleRate from album_summary_view
                    val albumSummary = databaseProvider.getDatabase()
                        .albumSummaryDao()
                        .getAlbumSummary(albumName, albumArtist)

                    _albumArtist.update { albumGroup.albumArtist ?: albumSummary?.albumArtist }
                    _albumYear.update { albumSummary?.year?.takeIf { it.isNotBlank() } }
                    _albumSampleRate.update { albumSummary?.maxSampleRate ?: 0 }
                    _albumBitrate.update { albumSummary?.maxBitrate ?: 0 }
                } else {
                    _albumName.update { albumName }
                    _albumArtist.update { albumArtist }
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
                _isRefreshing.update { true }
                audioFileScanner.loadAudioFiles(isIncremental = !forceRefresh)
                loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: AlbumDetail): AlbumDetailViewModel
    }
}
