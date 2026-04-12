package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for cached album sort orders.
 * Stores the ordered list of album IDs for each sort option to enable fast sorting without recalculation.
 */
@Entity(tableName = "album_sort_order")
data class AlbumSortOrderEntity(
    @PrimaryKey
    val sortOption: String,
    val albumIds: String,
    val contentHash: String,
    val lastUpdatedAt: Long
) {
    companion object {
        fun generateContentHash(albumIds: List<String>): String {
            return albumIds.hashCode().toString()
        }
    }
}
