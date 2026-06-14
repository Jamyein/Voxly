package com.voxly.domain.repository

import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.repository.LibraryDataHolder
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.IncrementalList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized scan repository — the single entry point for all
 * library-scan related operations.
 *
 * Designed after BoomingMusic's Repository pattern (see
 * docs/scan-三方对比报告.md — recommendation B1).
 * Encapsulates LibraryDataHolder, AudioFileScanner, MusicLibraryCache,
 * MediaStoreVersionCache, and SettingsDataStore behind a single interface
 * so that ViewModels depend on one abstraction instead of N.
 */
interface ScanRepository {

    // ─── Scan trigger ──────────────────────────────────────

    /**
     * Request a library refresh. Deduplicated via conflated SharedFlow
     * + collectLatest so concurrent requests collapse into one scan.
     */
    fun requestRefresh(forceRefresh: Boolean = false)

    // ─── Reactive scan state ───────────────────────────────

    /** True while any scan is in flight. */
    val isRefreshing: StateFlow<Boolean>

    /** Emitted on scan errors — UI collects and shows Snackbar. */
    val scanError: SharedFlow<String>

    // ─── Data flows (consumed by multiple screens) ─────────

    val allAudios: StateFlow<List<AudioFile>>
    val albums: StateFlow<List<AlbumGroup>>
    val artists: StateFlow<List<ArtistGroup>>
    val albumDiff: SharedFlow<IncrementalList<AlbumGroup>>
    val artistDiff: SharedFlow<IncrementalList<ArtistGroup>>
}
