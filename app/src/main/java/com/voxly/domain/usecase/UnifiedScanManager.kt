package com.voxly.domain.usecase

import com.voxly.domain.model.AudioFile
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
 * Unified scan manager interface
 *
 * Responsibilities:
 * - Centralized scan operations for all use cases
 * - Single source of truth for scan state
 * - Settings change watching and auto-refresh
 * - Single file sync for metadata updates
 */
interface UnifiedScanManager {

    /**
     * Current scan state flow
     */
    val scanState: StateFlow<ScanState>

    /**
     * Performs a unified scan operation
     *
     * @param target Scan target type (default: Global)
     * @param force Force refresh ignoring cache
     * @return ScanResult with scanned files or error
     */
    suspend fun scan(
        target: ScanTarget = ScanTarget.Global,
        force: Boolean = false
    ): ScanResult

    /**
     * Performs scan asynchronously (non-blocking)
     *
     * @param target Scan target type
     * @param force Force refresh ignoring cache
     * @param onComplete Optional callback when scan completes
     */
    fun scanAsync(
        target: ScanTarget = ScanTarget.Global,
        force: Boolean = false,
        onComplete: ((ScanResult) -> Unit)? = null
    )

    /**
     * Cancels any ongoing scan operation
     */
    fun cancel()

    /**
     * Syncs a single file to cache after metadata edit
     *
     * @param filePath Path to the file to sync
     * @return Result containing the updated AudioFile
     */
    suspend fun syncFile(filePath: String): Result<AudioFile>

    /**
     * Starts watching settings changes for auto-refresh
     * Should be called once at app startup
     */
    fun startWatchingSettings()

    /**
     * Resets state to Idle after UI has consumed it
     */
    fun resetState()
}
