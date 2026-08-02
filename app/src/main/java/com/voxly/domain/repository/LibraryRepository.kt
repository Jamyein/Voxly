package com.voxly.domain.repository

import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.IncrementalList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized library repository — the single entry point for all
 * library refresh / sync related operations.
 *
 * Designed after BoomingMusic's Repository pattern (see
 * docs/scan-三方对比报告.md — recommendation B1).
 * Encapsulates LibraryDataHolder (event bus + refresh counter) and
 * AudioFileScanner (scan implementation) behind a single interface
 * so that ViewModels depend on one abstraction instead of N.
 */
interface LibraryRepository {

    // ─── Data flows (consumed by multiple screens) ─────────

    val allAudios: StateFlow<List<AudioFile>>
    val albums: StateFlow<List<AlbumGroup>>
    val artists: StateFlow<List<ArtistGroup>>
    val albumDiff: SharedFlow<IncrementalList<AlbumGroup>>
    val artistDiff: SharedFlow<IncrementalList<ArtistGroup>>

    // ─── Reactive scan state ───────────────────────────────

    /** True while any scan is in flight. */
    val isRefreshing: StateFlow<Boolean>

    /** Emitted on scan errors — UI collects and shows Snackbar. */
    val scanError: SharedFlow<String>

    // ─── Scan trigger (fire-and-forget) ────────────────────

    /**
     * Request a library refresh. Deduplicated via conflated SharedFlow
     * + collectLatest so concurrent requests collapse into one scan.
     *
     * @param forceRefresh Full rescan, ignores cache.
     * @param bypassVersionCache Skip the MediaStore version short-circuit.
     *   Pass `true` from user-initiated pull-to-refresh so the spinner
     *   always corresponds to a real scan attempt. System-driven refreshes
     *   (MediaStore observer, periodic worker, SAF walker) keep the default
     *   `false` to stay cheap when MediaStore has not changed.
     */
    fun refresh(
        forceRefresh: Boolean = false,
        bypassVersionCache: Boolean = false,
    )

    // ─── Single-file sync (metadata edit) ──────────────────

    /**
     * Syncs a single file to cache after metadata edit
     *
     * @param filePath Path to the file to sync
     * @return Result containing the updated AudioFile
     */
    suspend fun syncFile(filePath: String): Result<AudioFile>

    // ─── Settings-driven auto-refresh ──────────────────────

    /**
     * Starts watching settings changes for auto-refresh.
     * Should be called once at app startup.
     */
    fun startWatchingSettings()
}
