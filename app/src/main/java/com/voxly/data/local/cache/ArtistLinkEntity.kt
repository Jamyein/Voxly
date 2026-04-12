package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for artist links.
 * Stores split artist names for multi-artist track support.
 * When parsing "周杰伦 / 费玉清", stores two records:
 * - trackId | "周杰伦"
 * - trackId | "费玉清"
 *
 * Enables artist page aggregation of collaborative works.
 */
@Entity(
    tableName = "artist_links",
    indices = [
        Index(value = ["artistName"]),
        Index(value = ["trackId"])
    ]
)
data class ArtistLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val artistName: String
)