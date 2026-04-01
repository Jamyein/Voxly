package com.voxly.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for cached album art at file level.
 * Supports three-tier caching architecture:
 * - L1: Memory thumbnail cache (Bitmap LRU)
 * - L2: Room database cache (original + thumbnail bytes)
 * - L3: File system (on-demand read)
 * 
 * This entity stores both original art (optional, LRU managed) and compressed thumbnail
 * to reduce memory usage while maintaining fast access to frequently viewed covers.
 */
@Entity(
    tableName = "album_art_file_cache",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["accessCount", "lastAccessTime"])  // For LRU eviction
    ]
)
data class AlbumArtFileCacheEntity(
    @PrimaryKey
    val filePath: String,
    
    /**
     * Original full-resolution album art bytes.
     * Only stored for files < 5MB to control database size.
     * Null if original is too large or not cached.
     */
    val originalArtBytes: ByteArray?,
    
    /**
     * Compressed thumbnail bytes (256x256 JPEG, ~10-50KB).
     * Always stored for cached entries.
     */
    val thumbnailBytes: ByteArray?,
    
    /**
     * File modification time when cached.
     * Used to validate cache freshness.
     */
    val lastModified: Long,
    
    /**
     * Timestamp when first cached.
     */
    val cacheTime: Long = System.currentTimeMillis(),
    
    /**
     * Number of times this entry has been accessed.
     * Used for LRU eviction decisions.
     */
    val accessCount: Int = 0,
    
    /**
     * Timestamp of last access.
     * Used for LRU eviction decisions.
     */
    val lastAccessTime: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumArtFileCacheEntity

        if (filePath != other.filePath) return false
        if (originalArtBytes != null) {
            if (other.originalArtBytes == null) return false
            if (!originalArtBytes.contentEquals(other.originalArtBytes)) return false
        } else if (other.originalArtBytes != null) return false
        if (thumbnailBytes != null) {
            if (other.thumbnailBytes == null) return false
            if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        } else if (other.thumbnailBytes != null) return false
        if (lastModified != other.lastModified) return false
        if (cacheTime != other.cacheTime) return false
        if (accessCount != other.accessCount) return false
        if (lastAccessTime != other.lastAccessTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + (originalArtBytes?.contentHashCode() ?: 0)
        result = 31 * result + (thumbnailBytes?.contentHashCode() ?: 0)
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + cacheTime.hashCode()
        result = 31 * result + accessCount
        result = 31 * result + lastAccessTime.hashCode()
        return result
    }
}
