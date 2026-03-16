package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for cached album art thumbnails.
 * Stores compressed thumbnail bytes for instant display.
 * 
 * Design decision: Store actual thumbnail bytes (compressed JPEG) for instant display.
 * - Album count is much smaller than track count (e.g., 100 albums vs 10,000 tracks)
 * - Thumbnails are small (typically 10-50KB each at 256x256)
 * - Instant display without needing to decode from files or network
 */
@Entity(
    tableName = "album_thumbnails",
    indices = [
        Index(value = ["albumId"], unique = true),
        Index(value = ["cachedAt"])
    ]
)
data class AlbumThumbnailEntity(
    @PrimaryKey
    val albumId: Long,
    
    /**
     * Compressed JPEG bytes of the thumbnail.
     * Typically 256x256 pixels, 80% quality JPEG (~10-50KB per album).
     */
    val thumbnailBytes: ByteArray,
    
    /**
     * Width of the thumbnail in pixels.
     */
    val width: Int,
    
    /**
     * Height of the thumbnail in pixels.
     */
    val height: Int,
    
    /**
     * Source URI or path where the thumbnail was extracted from.
     * Can be MediaStore URI or embedded file path.
     */
    val sourceUri: String?,
    
    /**
     * Timestamp when thumbnail was cached.
     */
    val cachedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumThumbnailEntity

        if (albumId != other.albumId) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (sourceUri != other.sourceUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = albumId.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + (sourceUri?.hashCode() ?: 0)
        return result
    }
}
