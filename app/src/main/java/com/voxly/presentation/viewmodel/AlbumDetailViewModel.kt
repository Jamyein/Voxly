package com.voxly.presentation.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.local.cover.CoverUriProvider
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
import androidx.compose.ui.graphics.Color

@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: AlbumDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val coverUriProvider: CoverUriProvider,
    private val libraryDataHolder: LibraryDataHolder
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

    private val _coverUri = MutableStateFlow<Uri?>(null)
    val coverUri: StateFlow<Uri?> = _coverUri.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files.asStateFlow()

    /**
     * Mirrors the global scan activity maintained by [LibraryDataHolder].
     * A VM created mid-scan picks up the current spinner state immediately
     * on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    private var refreshJob: Job? = null
    private val tagLibReadCache = mutableMapOf<String, AudioMetadata>()

    init {
        // Pre-populate from navKey so UI shows correct name/artist from first frame
        _albumName.update { navKey.albumName }
        _albumArtist.update { navKey.albumArtist.takeIf { it.isNotEmpty() } }
        loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
    }

    /**
     * Load album data from AudioFileScanner by album name and artist.
     * Year and sample rate are loaded from album_summary_view for fast aggregation.
     * Bitrate is calculated from file metadata.
     * For files with missing discNumber, uses TagLib to read from file tags.
     * Also pre-resolves cover URI for seamless image loading during navigation.
     */
    fun loadAlbum(albumName: String, albumArtist: String?) {
        Timber.tag("Voxly").i("AlbumDetailViewModel loadAlbum: albumId=$albumName")
        if (_albumName.value == albumName && _albumArtist.value == albumArtist && _files.value.isNotEmpty()) {
            return
        }

        // Reset cover-related state immediately to prevent showing previous album's cover
        // during transition when ViewModel might be reused across different albums
        _coverUri.value = null
        _coverPath.value = null
        _files.value = emptyList()

        viewModelScope.launch {
            try {
                val albums = audioFileScanner.albums.first()
                val albumGroup = albums.find { album ->
                    album.name == albumName && (albumArtist == null || album.albumArtist == albumArtist)
                }

                if (albumGroup != null) {
                    _albumName.update { albumGroup.name }
                    _albumArtist.update { albumGroup.albumArtist }
                    _coverPath.update { albumGroup.coverPath }

                    val firstFile = albumGroup.files.firstOrNull()
                    val resolvedUri = withContext(Dispatchers.IO) {
                        coverUriProvider.getCoverUri(
                            albumId = firstFile?.mediaStoreAlbumId,
                            filePath = albumGroup.coverPath ?: firstFile?.path
                        )
                    }
                    _coverUri.update { resolvedUri }

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

                    val albumYear = albumGroup.files.mapNotNull {
                        it.metadata.year?.trim()?.takeIf { it.isNotBlank() }
                    }.maxOrNull()
                    val maxSampleRate = albumGroup.files.maxOfOrNull { it.sampleRate } ?: 0
                    val maxBitrate = albumGroup.files.maxOfOrNull { it.bitrate } ?: 0

                    _albumYear.update { albumYear }
                    _albumSampleRate.update { maxSampleRate }
                    _albumBitrate.update { maxBitrate }

                    withContext(Dispatchers.Default) {
                        extractDominantColor(albumGroup)
                    }
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
     * Refresh album data. Routes the scan request through [LibraryDataHolder]
     * (single fan-in) so concurrent refreshes from other screens collapse into
     * one scan via the holder's conflated SharedFlow + the collector's
     * `collectLatest`. The local `loadAlbum` re-read picks up the new
     * aggregator output as soon as the incremental rebuild finishes.
     */
    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // bypassVersionCache = true: user-initiated refresh should always
            // trigger a real scan attempt, not short-circuit on MediaStore
            // version equality.
            libraryDataHolder.requestRefresh(
                forceRefresh = forceRefresh,
                bypassVersionCache = true,
            )
            loadAlbum(navKey.albumName, navKey.albumArtist.takeIf { it.isNotEmpty() })
        }
    }

    private suspend fun extractDominantColor(albumGroup: com.voxly.domain.model.AlbumGroup) {
        val firstFile = albumGroup.files.firstOrNull() ?: return
        val mediaStoreUri = firstFile.getAlbumArtUri()
        val filePath = albumGroup.coverPath ?: firstFile.path
        val folder = java.io.File(filePath).parentFile
        val coverNames = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "album.jpg", "album.png")
        val fallbackUri = coverNames.firstNotNullOfOrNull { name ->
            java.io.File(folder, name).takeIf { it.exists() }?.let { android.net.Uri.fromFile(it) }
        }
        val uriToLoad = mediaStoreUri ?: fallbackUri ?: return

        try {
            val bitmap = context.contentResolver.openInputStream(uriToLoad)?.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val color = palette.dominantSwatch?.rgb?.let { Color(it) }
                    ?: palette.vibrantSwatch?.rgb?.let { Color(it) }
                    ?: palette.mutedSwatch?.rgb?.let { Color(it) }
                _dominantColor.update { color }
                bitmap.recycle()
            }
        } catch (_: Throwable) {
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: AlbumDetail): AlbumDetailViewModel
    }
}
