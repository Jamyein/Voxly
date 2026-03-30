package com.voxly.presentation.screens.album

import com.voxly.R
import com.voxly.core.util.SortUtil
import com.voxly.data.local.AlbumSortOption
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
 */
fun albumDisplayYearInt(album: AlbumGroup): Int? {
    return album.files
        .mapNotNull { audioFile -> extractYear(audioFile.metadata.year) }
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
 * Gets the label resource ID for an AlbumSortOption.
 */
fun AlbumSortOption.labelResId(): Int = when (this) {
    AlbumSortOption.NAME_ASC -> R.string.album_sort_name_asc
    AlbumSortOption.TRACK_COUNT_DESC -> R.string.album_sort_track_count_desc
    AlbumSortOption.YEAR_DESC -> R.string.album_sort_year_desc
}
