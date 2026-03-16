package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache entity for album year data.
 * Stores year information for albums to enable instant display without reading file tags.
 */
@Entity(
    tableName = "album_year_cache",
    indices = [
        Index(value = ["albumName", "artist"], unique = true)
    ]
)
data class AlbumYearEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val albumName: String,
    val artist: String?,
    val year: String,
    val updatedAt: Long = System.currentTimeMillis()
)
