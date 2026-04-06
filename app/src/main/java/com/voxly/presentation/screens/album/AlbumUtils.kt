package com.voxly.presentation.screens.album

import com.voxly.R
import com.voxly.core.util.SortUtil
import com.voxly.data.local.AlbumSortOption
import com.voxly.data.local.cache.AlbumInfoEntity
import com.voxly.domain.model.AlbumGroup

/**
 * Data class representing a group of albums by year.
 */
data class YearGroup(
    val year: Int,
    val albums: List<AlbumGroup>
)

/**
 * Extracts a 4-digit year from the album metadata.
 * This is the legacy method that calculates from file metadata.
 * For better performance, use [getAlbumDisplayYear] with cached AlbumInfoEntity.
 */
fun albumDisplayYearInt(album: AlbumGroup): Int? {
    return album.files
        .mapNotNull { audioFile -> extractYear(audioFile.metadata.year) }
        .maxOrNull()
}

/**
 * Gets the display year for an album, preferring cached AlbumInfo data.
 * Falls back to calculating from file metadata if cache is unavailable.
 *
 * @param album The album group
 * @param cachedInfo Optional cached album info (from AlbumInfoManager)
 * @return The year as an integer, or null if not available
 */
fun getAlbumDisplayYear(album: AlbumGroup, cachedInfo: AlbumInfoEntity?): Int? {
    // Prefer cached year
    cachedInfo?.year?.let { year ->
        return extractYear(year)
    }
    // Fallback to calculating from files
    return albumDisplayYearInt(album)
}

/**
 * Gets the display year string for an album.
 *
 * @param album The album group
 * @param cachedInfo Optional cached album info
 * @return The year as a string, or null if not available
 */
fun getAlbumDisplayYearString(album: AlbumGroup, cachedInfo: AlbumInfoEntity?): String? {
    return cachedInfo?.year ?: album.files
        .mapNotNull { it.metadata.year }
        .maxOrNull()
}

/**
 * Creates a stable key for an album group.
 */
fun albumStableKey(album: AlbumGroup): String {
    val representativePath = album.files.firstOrNull()?.path.orEmpty()
    return "${album.name}|${album.artist.orEmpty()}|$representativePath"
}

/**
 * Extracts a 4-digit year from a string.
 */
fun extractYear(rawYear: String?): Int? {
    val normalized = rawYear?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return Regex("""\d{4}""").find(normalized)?.value?.toIntOrNull()
}

/**
 * Applies sorting to album groups.
 * For YEAR_DESC sort, uses file metadata calculation (cache integration would require suspend).
 */
fun applyAlbumSort(
    albums: List<AlbumGroup>,
    sortOption: AlbumSortOption
): List<AlbumGroup> {
    return when (sortOption) {
        AlbumSortOption.NAME_ASC -> albums.sortedWith(
            compareBy { SortUtil.toSortablePinyin(it.name) }
        )
        AlbumSortOption.TRACK_COUNT_DESC -> albums.sortedByDescending { it.files.size }
        AlbumSortOption.YEAR_DESC -> albums.sortedByDescending { album ->
            album.files.mapNotNull { audioFile ->
                audioFile.metadata.year
                    ?.let { Regex("""\d{4}""").find(it)?.value }
                    ?.toIntOrNull()
            }.maxOrNull() ?: Int.MIN_VALUE
        }
    }
}

/**
 * Applies sorting to album groups with cached info support.
 * This suspend version can use cached album info for better performance.
 *
 * @param albums List of album groups
 * @param sortOption Sort option
 * @param cachedInfoMap Map of album keys to cached info
 * @return Sorted list of albums
 */
fun applyAlbumSortWithCache(
    albums: List<AlbumGroup>,
    sortOption: AlbumSortOption,
    cachedInfoMap: Map<String, AlbumInfoEntity> = emptyMap()
): List<AlbumGroup> {
    return when (sortOption) {
        AlbumSortOption.NAME_ASC -> albums.sortedWith(
            compareBy { SortUtil.toSortablePinyin(it.name) }
        )
        AlbumSortOption.TRACK_COUNT_DESC -> albums.sortedByDescending { it.files.size }
        AlbumSortOption.YEAR_DESC -> albums.sortedByDescending { album ->
            // Try cache first
            val albumKey = AlbumInfoEntity.generateId(album.name, album.artist)
            val cachedYear = cachedInfoMap[albumKey]?.year?.let { extractYear(it) }
            // Fallback to file metadata
            cachedYear ?: album.files.mapNotNull { audioFile ->
                audioFile.metadata.year
                    ?.let { Regex("""\d{4}""").find(it)?.value }
                    ?.toIntOrNull()
            }.maxOrNull() ?: Int.MIN_VALUE
        }
    }
}

/**
 * Formats sample rate for display.
 * Examples: 44100 -> "44.1 kHz", 96000 -> "96 kHz"
 */
fun formatSampleRate(sampleRateHz: Int): String {
    return when {
        sampleRateHz >= 1000000 -> "${sampleRateHz / 1000000} MHz"
        sampleRateHz >= 1000 -> {
            val khz = sampleRateHz / 1000.0
            if (khz == khz.toInt().toDouble()) {
                "${khz.toInt()} kHz"
            } else {
                "%.1f kHz".format(khz)
            }
        }
        sampleRateHz > 0 -> "$sampleRateHz Hz"
        else -> ""
    }
}

/**
 * Formats bitrate for display.
 * Examples: 320 -> "320 kbps", 1411 -> "1,411 kbps"
 */
fun formatBitrate(bitrateKbps: Int): String {
    return when {
        bitrateKbps >= 1000 -> "%,d kbps".format(bitrateKbps)
        bitrateKbps > 0 -> "$bitrateKbps kbps"
        else -> ""
    }
}

/**
 * Gets the label resource ID for an AlbumSortOption.
 */
fun AlbumSortOption.labelResId(): Int = when (this) {
    AlbumSortOption.NAME_ASC -> R.string.album_sort_name_asc
    AlbumSortOption.TRACK_COUNT_DESC -> R.string.album_sort_track_count_desc
    AlbumSortOption.YEAR_DESC -> R.string.album_sort_year_desc
}
