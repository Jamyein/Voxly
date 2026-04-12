package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for cached album art thumbnails.
 * Stores cover key pointing to disk cache for instant display.
 * 
 * Design decision: Store cover key (MD5 of AlbumArtist + Album) instead of bytes.
 * - Album count is much smaller than track count (e.g., 100 albums vs 10,000 tracks)
 * - Disk cache (WebP 512x512) provides fast access without DB bloat
 * - L1: Coil memory cache, L2: Disk cache, L3: MediaStore/Folder fallback
 */
@Entity(
    tableName = "album_thumbnails",
    indices = [
        Index(value = ["albumId"], unique = true),
        Index(value = ["coverKey"])
    ]
)
data class AlbumThumbnailEntity(
    @PrimaryKey
    val albumId: Long,
    
    /**
     * Cache key: MD5(AlbumArtist + AlbumName)
     * Used to lookup thumbnail file in disk cache.
     */
    val coverKey: String,
    
    /**
     * Width of the original thumbnail in pixels.
     */
    val width: Int,
    
    /**
     * Height of the original thumbnail in pixels.
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
        if (coverKey != other.coverKey) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (sourceUri != other.sourceUri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = albumId.hashCode()
        result = 31 * result + coverKey.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + (sourceUri?.hashCode() ?: 0)
        return result
    }
}
