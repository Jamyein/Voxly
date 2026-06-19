package com.voxly.data.repository

import com.voxly.data.local.AudioFileScanner
import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.IncrementalList
import com.voxly.domain.repository.ScanRepository
import com.voxly.domain.repository.LibraryDataHolder
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of [ScanRepository].
 *
 * Wires together [LibraryDataHolder] (coordination), [AudioFileScanner]
 * (data flows), and the version short-circuit behind a single interface.
 * ViewModels that inject this class depend on one abstraction instead
 * of multiple data-layer classes.
 */
@Singleton
class RealScanRepository @Inject constructor(
    private val libraryDataHolder: LibraryDataHolder,
    private val audioFileScanner: AudioFileScanner,
) : ScanRepository {

    override fun requestRefresh(
        forceRefresh: Boolean,
        bypassVersionCache: Boolean,
    ) {
        libraryDataHolder.requestRefresh(forceRefresh, bypassVersionCache)
    }

    override val isRefreshing: StateFlow<Boolean> = libraryDataHolder.isRefreshing
    override val scanError: SharedFlow<String> = libraryDataHolder.scanError

    // Data flows delegate to the scanner.
    // allAudios now reads the raw Room-backed StateFlow so callers see EVERY
    // cached audio file (including those without an album key). Album /
    // artist flows continue to come from the aggregator.
    override val allAudios: StateFlow<List<AudioFile>> = audioFileScanner.cachedAudioFilesStateFlow
    override val albums: StateFlow<List<AlbumGroup>> = audioFileScanner.albums
    override val artists: StateFlow<List<ArtistGroup>> = audioFileScanner.artists
    override val albumDiff: SharedFlow<IncrementalList<AlbumGroup>> = audioFileScanner.albumDiff
    override val artistDiff: SharedFlow<IncrementalList<ArtistGroup>> = audioFileScanner.artistDiff
}
