package com.voxly.domain.repository

import com.voxly.domain.model.AlbumGroup
import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.IncrementalList
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified scan target types for the scanning system.
 */
sealed class ScanTarget {
    /**
     * Global scan - scans entire device for music files
     */
    object Global : ScanTarget()

    /**
     * Incremental scan - only scans new/modified files
     */
    object Incremental : ScanTarget()

    /**
     * Scan specific directories only
     */
    data class Directories(val paths: List<String>) : ScanTarget()

    /**
     * Single file update - used for metadata editing sync
     */
    data class SingleFile(val path: String) : ScanTarget()
}

/**
 * Unified scan result types
 */
sealed class ScanResult {
    /**
     * Scan completed successfully
     */
    data class Success(
        val files: List<AudioFile>,
        val scannedAt: Long = System.currentTimeMillis(),
        val scannedCount: Int = files.size
    ) : ScanResult()

    /**
     * Scan failed with an error
     */
    data class Error(val message: String, val cause: Throwable? = null) : ScanResult()

    /**
     * Scan was cancelled
     */
    object Cancelled : ScanResult()
}

/**
 * Unified scan state - represents the current state of scanning
 */
sealed class ScanState {
    /**
     * No scan in progress
     */
    object Idle : ScanState()

    /**
     * Currently scanning
     */
    data class Scanning(
        val target: ScanTarget?,
        val progress: Float = 0f,
        val currentFile: String? = null
    ) : ScanState()

    /**
     * Scan completed successfully
     */
    data class Success(
        val count: Int,
        val target: ScanTarget?
    ) : ScanState()

    /**
     * Scan failed with an error
     */
    data class Error(val message: String) : ScanState()

    /**
     * Scan was cancelled
     */
    object Cancelled : ScanState()
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
    val albumDiff: SharedFlow<IncrementalList<AlbumGroup>>
    val artistDiff: SharedFlow<IncrementalList<ArtistGroup>>

    // ─── Reactive scan state ───────────────────────────────

    /** True while any scan is in flight. */
    val isRefreshing: StateFlow<Boolean>

    /** Emitted on scan errors — UI collects and shows Snackbar. */
    val scanError: SharedFlow<String>

    /** Current unified scan state. */
    val scanState: StateFlow<ScanState>

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
     * @param source Origin of the refresh, used for merge-window tuning.
     */
    fun refresh(
        forceRefresh: Boolean = false,
        bypassVersionCache: Boolean = false,
        source: ChangeSource = ChangeSource.PULL_TO_REFRESH,
    )

    // ─── Single-file sync (metadata edit) ──────────────────

    /**
     * Syncs a single file to cache after metadata edit
     *
     * @param filePath Path to the file to sync
     * @return Result containing the updated AudioFile
     */
    suspend fun syncFile(filePath: String): Result<AudioFile>

    // ─── UnifiedScanManager surface ────────────────────────

    /**
     * Starts watching settings changes for auto-refresh.
     * Should be called once at app startup.
     */
    fun startWatchingSettings()

    /**
     * Resets scan state to Idle after UI has consumed it.
     */
    fun resetState()

    /**
     * Syncs selected directories and performs incremental scan.
     * Called when directory settings change.
     */
    fun syncDirectories()
}
