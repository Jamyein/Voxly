package com.voxly.presentation.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.repository.LibraryRepository
import com.voxly.domain.repository.RefreshStrategy
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

data class AlbumDetailUiState(
    val albumName: String = "",
    val albumArtist: String? = null,
    val albumYear: String? = null,
    val albumBitrate: Int = 0,
    val albumSampleRate: Int = 0,
    val coverPath: String? = null,
    val coverUri: Uri? = null,
    val files: List<AudioFile> = emptyList(),
)

@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted val navKey: AlbumDetail,
    @ApplicationContext private val context: Context,
    private val audioFileScanner: AudioFileScanner,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val coverUriProvider: CoverUriProvider,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    // Split out so async palette extraction doesn't recompose the whole screen
    // (tracks list, header) — only consumers of the color recompose.
    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    /**
     * Mirrors the global scan activity maintained by [LibraryRepository].
     * A VM created mid-scan picks up the current spinner state immediately
     * on subscribe, with no missed-trigger edge case.
     */
    val isRefreshing: StateFlow<Boolean> = libraryRepository.isRefreshing

    private var refreshJob: Job? = null
    private val tagLibReadCache = mutableMapOf<String, AudioMetadata>()

    init {
        // Pre-populate from navKey so UI shows correct name/artist from first frame
        _uiState.update {
            it.copy(
                albumName = navKey.albumName,
                albumArtist = navKey.albumArtist.takeIf { a -> a.isNotEmpty() }
            )
        }
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
        val current = _uiState.value
        if (current.albumName == albumName && current.albumArtist == albumArtist && current.files.isNotEmpty()) {
            return
        }

        // Reset cover-related state immediately to prevent showing previous album's cover
        // during transition when ViewModel might be reused across different albums
        _uiState.update { it.copy(coverUri = null, coverPath = null, files = emptyList()) }

        viewModelScope.launch {
            try {
                val albums = audioFileScanner.albums.first()
                val albumGroup = albums.find { album ->
                    album.name == albumName && (albumArtist == null || album.albumArtist == albumArtist)
                }

                if (albumGroup != null) {
                    val firstFile = albumGroup.files.firstOrNull()
                    val resolvedUri = coverUriProvider.getCoverUri(
                            albumId = firstFile?.mediaStoreAlbumId,
                            filePath = albumGroup.coverPath ?: firstFile?.path
                        )

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

                    val albumYear = albumGroup.files.mapNotNull {
                        it.metadata.year?.trim()?.takeIf { it.isNotBlank() }
                    }.maxOrNull()
                    val maxSampleRate = albumGroup.files.maxOfOrNull { it.sampleRate } ?: 0
                    val maxBitrate = albumGroup.files.maxOfOrNull { it.bitrate } ?: 0

                    _uiState.update {
                        it.copy(
                            albumName = albumGroup.name,
                            albumArtist = albumGroup.albumArtist,
                            coverPath = albumGroup.coverPath,
                            coverUri = resolvedUri,
                            files = filesWithDiscNumber,
                            albumYear = albumYear,
                            albumSampleRate = maxSampleRate,
                            albumBitrate = maxBitrate
                        )
                    }

                    withContext(Dispatchers.Default) {
                        extractDominantColor(albumGroup)
                    }
                } else {
                    _uiState.update { it.copy(albumName = albumName, albumArtist = albumArtist) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading album: $albumName")
            }
        }
    }

    /**
     * Refresh album data. Routes the scan request through [LibraryRepository]
     * (single fan-in) so concurrent refreshes from other screens collapse into
     * one scan via the repository's conflated SharedFlow + the collector's
     * `collectLatest`. The local `loadAlbum` re-read picks up the new
     * aggregator output as soon as the incremental rebuild finishes.
     */
    fun refresh(forceRefresh: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // bypassVersionCache = true: user-initiated refresh should always
            // trigger a real scan attempt, not short-circuit on MediaStore
            // version equality.
            libraryRepository.refresh(
                if (forceRefresh) RefreshStrategy.FORCE else RefreshStrategy.INCREMENTAL
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
                _dominantColor.value = color
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
