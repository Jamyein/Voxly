package com.voxly.data.local.cache

import androidx.room.*

/**
 * DAO for file-level album art cache.
 * Supports three-tier caching with LRU eviction.
 */
@Dao
interface AlbumArtFileCacheDao {

    // ==================== Queries ====================

    /**
     * Gets complete cache entry by file path.
     */
    @Query("SELECT * FROM album_art_file_cache WHERE filePath = :path LIMIT 1")
    suspend fun getCacheEntry(path: String): AlbumArtFileCacheEntity?

    /**
     * Gets only thumbnail bytes for quick display.
     */
    @Query("SELECT thumbnailBytes FROM album_art_file_cache WHERE filePath = :path LIMIT 1")
    suspend fun getThumbnailBytes(path: String): ByteArray?

    /**
     * Gets only original art bytes for full display.
     */
    @Query("SELECT originalArtBytes FROM album_art_file_cache WHERE filePath = :path LIMIT 1")
    suspend fun getOriginalArtBytes(path: String): ByteArray?

    /**
     * Gets total count of cached entries.
     */
    @Query("SELECT COUNT(*) FROM album_art_file_cache")
    suspend fun getCacheCount(): Int

    /**
     * Gets total size of cached data in bytes (approximate).
     */
    @Query("SELECT SUM(LENGTH(COALESCE(originalArtBytes, X'')) + LENGTH(COALESCE(thumbnailBytes, X''))) FROM album_art_file_cache")
    suspend fun getTotalCacheSizeBytes(): Long?

    /**
     * Gets entries ordered by LRU (least recently used first).
     */
    @Query("SELECT * FROM album_art_file_cache ORDER BY accessCount ASC, lastAccessTime ASC LIMIT :limit")
    suspend fun getLeastRecentlyUsed(limit: Int): List<AlbumArtFileCacheEntity>

    /**
     * Gets entries that have original art cached (for size monitoring).
     */
    @Query("SELECT * FROM album_art_file_cache WHERE originalArtBytes IS NOT NULL")
    suspend fun getEntriesWithOriginalArt(): List<AlbumArtFileCacheEntity>

    // ==================== Inserts/Updates ====================

    /**
     * Inserts or replaces a cache entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AlbumArtFileCacheEntity)

    /**
     * Updates access statistics for an entry.
     */
    @Query("""
        UPDATE album_art_file_cache 
        SET accessCount = accessCount + 1, lastAccessTime = :timestamp 
        WHERE filePath = :path
    """)
    suspend fun updateAccessStats(path: String, timestamp: Long = System.currentTimeMillis())

    // ==================== Deletes ====================

    /**
     * Deletes a specific cache entry.
     */
    @Query("DELETE FROM album_art_file_cache WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    /**
     * Deletes multiple entries by paths.
     */
    @Query("DELETE FROM album_art_file_cache WHERE filePath IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    /**
     * Deletes entries older than specified timestamp.
     */
    @Query("DELETE FROM album_art_file_cache WHERE cacheTime < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /**
     * Clears all cache entries.
     */
    @Query("DELETE FROM album_art_file_cache")
    suspend fun deleteAll()

    /**
     * Deletes original art bytes only (keeps thumbnails).
     * Used when cache size needs reduction.
     */
    @Query("UPDATE album_art_file_cache SET originalArtBytes = NULL WHERE filePath = :path")
    suspend fun clearOriginalArt(path: String)

    /**
     * Deletes original art for least recently used entries.
     */
    @Query("""
        UPDATE album_art_file_cache 
        SET originalArtBytes = NULL 
        WHERE filePath IN (
            SELECT filePath FROM album_art_file_cache 
            WHERE originalArtBytes IS NOT NULL 
            ORDER BY accessCount ASC, lastAccessTime ASC 
            LIMIT :count
        )
    """)
    suspend fun clearOriginalArtForLruEntries(count: Int): Int
}