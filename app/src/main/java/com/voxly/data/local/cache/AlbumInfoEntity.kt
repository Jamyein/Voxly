package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for cached album information.
 * Stores aggregated album data including year, sample rate, and bitrate
 * to avoid expensive real-time calculations.
 *
 * The hash is based on album name + song count for change detection.
 */
@Entity(
    tableName = "album_info",
    indices = [
        Index(value = ["albumName", "albumArtist"], unique = true),
        Index(value = ["year"])
    ]
)
data class AlbumInfoEntity(
    @PrimaryKey
    val id: String,
    val albumName: String,
    val albumArtist: String?,

    /** Album year extracted from TagLib (most common year across all songs) */
    val year: String?,

    /**
     * Hash for detecting year changes.
     * Format: "year1,year2,year3" sorted and hashed.
     */
    val yearHash: String,

    /** Highest sample rate in Hz (e.g., 44100, 48000, 96000) */
    val sampleRate: Int,

    /** Highest bitrate in kbps (e.g., 320, 1411, 2304) */
    val bitrate: Int,

    /**
     * Hash for detecting album content changes.
     * Based on: albumName + songCount
     * This allows us to detect when songs are added/removed from an album.
     */
    val contentHash: String,

    /** Number of songs in the album when cached */
    val songCount: Int,

    /** Timestamp when this record was created/updated */
    val lastUpdatedAt: Long
) {
    companion object {
        /**
         * Generates a unique ID for an album.
         */
        fun generateId(albumName: String, albumArtist: String?): String {
            val artistPart = albumArtist ?: "_unknown_"
            return "${albumName.hashCode()}_${artistPart.hashCode()}"
        }

        /**
         * Generates content hash based on album name and song count.
         */
        fun generateContentHash(albumName: String, songCount: Int): String {
            return "${albumName.hashCode()}_$songCount"
        }

        /**
         * Generates year hash from a list of years.
         */
        fun generateYearHash(years: List<String>): String {
            return years.filter { it.isNotBlank() }
                .sorted()
                .joinToString(",")
                .hashCode()
                .toString()
        }

        /**
         * Formats sample rate for display (e.g., "44.1 kHz", "96 kHz")
         */
        fun formatSampleRate(sampleRateHz: Int): String {
            return when {
                sampleRateHz >= 1000 -> "${sampleRateHz / 1000} kHz"
                sampleRateHz > 0 -> "$sampleRateHz Hz"
                else -> ""
            }
        }

        /**
         * Formats bitrate for display (e.g., "320 kbps", "1,411 kbps")
         */
        fun formatBitrate(bitrateKbps: Int): String {
            return when {
                bitrateKbps >= 1000 -> "${bitrateKbps / 1000} Mbps ${bitrateKbps % 1000} kbps"
                bitrateKbps > 0 -> "$bitrateKbps kbps"
                else -> ""
            }
        }
    }
}
