package com.voxly.data.local.scanner

import com.voxly.domain.model.AudioFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine for filtering audio files based on whitelist, blacklist, and duration settings.
 * Pure filtering logic with no I/O operations.
 */
@Singleton
class FilterEngine @Inject constructor() {

    /**
     * Data class to hold filter settings.
     * Prevents multiple I/O operations by collecting settings once.
     */
    data class FilterSettings(
        val whitelistEnabled: Boolean,
        val blacklistEnabled: Boolean,
        val minDurationEnabled: Boolean,
        val whitelistUris: List<String>,
        val blacklistUris: List<String>,
        val minDurationMs: Long
    )

    /**
     * Result of filtering with version for cache invalidation.
     */
    data class FilteredResult(
        val version: Long,
        val files: List<AudioFile>
    )

    /**
     * Applies all filters (whitelist, blacklist, min duration) to audio files.
     * @param files List of audio files to filter
     * @param settings Pre-collected filter settings (no I/O within this method)
     * @return Filtered list of audio files
     */
    fun applyFilters(files: List<AudioFile>, settings: FilterSettings): List<AudioFile> {
        // If no filters are enabled, return all files
        if (!settings.whitelistEnabled && !settings.blacklistEnabled && !settings.minDurationEnabled) {
            return files
        }

        // Pre-compute whitelist and blacklist paths once (avoid repeated computation)
        val whitelistPaths = if (settings.whitelistEnabled && settings.whitelistUris.isNotEmpty()) {
            settings.whitelistUris
        } else null

        val blacklistPaths = if (settings.blacklistEnabled && settings.blacklistUris.isNotEmpty()) {
            settings.blacklistUris
        } else null

        // Optimization: Pre-compute directory prefixes for faster matching
        val whitelistPrefixes = whitelistPaths?.map { it.trimEnd('/', '\\') }
        val blacklistPrefixes = blacklistPaths?.map { it.trimEnd('/', '\\') }

        return files.filter { file ->
            val path = file.path

            // Apply whitelist filter: file must be in one of the whitelist directories
            if (whitelistPrefixes != null) {
                val isInWhitelist = whitelistPrefixes.any { whitelistPath ->
                    path == whitelistPath ||
                    path.startsWith("$whitelistPath/") ||
                    path.startsWith("$whitelistPath\\")
                }
                if (!isInWhitelist) return@filter false
            }

            // Apply blacklist filter: file must NOT be in any blacklist directory
            if (blacklistPrefixes != null) {
                val isBlacklisted = blacklistPrefixes.any { blacklistPath ->
                    path == blacklistPath ||
                    path.startsWith("$blacklistPath/") ||
                    path.startsWith("$blacklistPath\\")
                }
                if (isBlacklisted) return@filter false
            }

            // Apply min duration filter: file duration must be >= minDurationMs
            if (settings.minDurationEnabled && file.duration > 0 && file.duration < settings.minDurationMs) {
                return@filter false
            }

            true
        }
    }

    /**
     * Compute a version hash for filter settings.
     * Used for cache invalidation when settings change.
     */
    fun computeFilterVersion(cacheVersion: Long, settings: FilterSettings): Long {
        var result = cacheVersion
        result = 31 * result + if (settings.whitelistEnabled) 1 else 0
        result = 31 * result + if (settings.blacklistEnabled) 1 else 0
        result = 31 * result + if (settings.minDurationEnabled) 1 else 0
        result = 31 * result + settings.whitelistUris.hashCode().toLong()
        result = 31 * result + settings.blacklistUris.hashCode().toLong()
        result = 31 * result + settings.minDurationMs
        return result
    }
}
