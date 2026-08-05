package com.voxly.domain.repository

import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls how aggressively a library refresh request is honoured.
 *
 * [LAZY] — skip the scan entirely if the MediaStore version hasn't changed
 *   since the last successful scan. For resume callbacks and periodic workers
 *   where a no-op is the common case.
 *
 * [INCREMENTAL] — always run the incremental scan (mtime-based diff), even
 *   when the version hasn't changed. For MediaStore observer fires where real
 *   filesystem changes are suspected.
 *
 * [FORCE] — full rescan, bypassing every cache/short-circuit. For user-initiated
 *   pull-to-refresh.
 */
enum class RefreshStrategy {
    LAZY,
    INCREMENTAL,
    FORCE
}

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

    // ─── Reactive scan state ───────────────────────────────

    /** True while any scan is in flight. */
    val isRefreshing: StateFlow<Boolean>

    /** Emitted on scan errors — UI collects and shows Snackbar. */
    val scanError: SharedFlow<String>

    // ─── Scan trigger (fire-and-forget) ────────────────────

    /**
     * Request a library refresh. Deduplicated via conflated SharedFlow
     * + collectLatest so concurrent requests collapse into one scan.
     */
    fun refresh(strategy: RefreshStrategy = RefreshStrategy.LAZY)

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
