package com.voxly.domain.model

/**
 * Represents the state of music library refresh operations.
 */
sealed class LibraryRefreshState {
    /**
     * No refresh operation in progress.
     */
    object Idle : LibraryRefreshState()

    /**
     * Currently scanning for audio files.
     * @param progress Optional progress information
     */
    data class Scanning(val progress: ScanProgress? = null) : LibraryRefreshState()

    /**
     * Refresh completed successfully.
     * @param fileCount Number of files found
     */
    data class Success(val fileCount: Int) : LibraryRefreshState()

    /**
     * Refresh failed with an error.
     * @param message Error message
     */
    data class Error(val message: String) : LibraryRefreshState()
}

/**
 * Progress information for scanning operations.
 * @param current Current file index
 * @param total Total number of files to scan
 * @param currentFile Name of the current file being scanned
 */
data class ScanProgress(
    val current: Int,
    val total: Int,
    val currentFile: String? = null
)
