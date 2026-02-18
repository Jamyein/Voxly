package com.voxly.data.local.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for album art thumbnails.
 * Provides efficient caching of compressed thumbnails for instant display.
 */
@Dao
interface AlbumThumbnailDao {
    
    // ==================== Queries ====================
    
    /**
     * Gets thumbnail by album ID.
     */
    @Query("SELECT * FROM album_thumbnails WHERE albumId = :albumId LIMIT 1")
    suspend fun getThumbnailByAlbumId(albumId: Long): AlbumThumbnailEntity?
    
    /**
     * Gets multiple thumbnails by album IDs.
     * Efficient batch query for list display.
     */
    @Query("SELECT * FROM album_thumbnails WHERE albumId IN (:albumIds)")
    suspend fun getThumbnailsByAlbumIds(albumIds: List<Long>): List<AlbumThumbnailEntity>
    
    /**
     * Gets all cached thumbnails.
     */
    @Query("SELECT * FROM album_thumbnails")
    suspend fun getAllThumbnails(): List<AlbumThumbnailEntity>
    
    /**
     * Gets count of cached thumbnails.
     */
    @Query("SELECT COUNT(*) FROM album_thumbnails")
    suspend fun getThumbnailCount(): Int
    
    /**
     * Checks if thumbnail exists for album.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM album_thumbnails WHERE albumId = :albumId LIMIT 1)")
    suspend fun hasThumbnail(albumId: Long): Boolean
    
    /**
     * Gets all album IDs that have cached thumbnails.
     */
    @Query("SELECT albumId FROM album_thumbnails")
    suspend fun getCachedAlbumIds(): List<Long>
    
    // ==================== Inserts/Updates ====================
    
    /**
     * Inserts or updates a thumbnail.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thumbnail: AlbumThumbnailEntity)
    
    /**
     * Inserts or updates multiple thumbnails.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(thumbnails: List<AlbumThumbnailEntity>)
    
    /**
     * Inserts thumbnails in chunks for large batches.
     */
    @Transaction
    suspend fun insertAllChunked(thumbnails: List<AlbumThumbnailEntity>, chunkSize: Int = 50) {
        thumbnails.chunked(chunkSize).forEach { chunk ->
            insertAll(chunk)
        }
    }
    
    // ==================== Deletes ====================
    
    /**
     * Deletes thumbnail by album ID.
     */
    @Query("DELETE FROM album_thumbnails WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)
    
    /**
     * Deletes thumbnails not in the provided list of album IDs.
     * Used to clean up thumbnails for deleted albums.
     */
    @Query("DELETE FROM album_thumbnails WHERE albumId NOT IN (:validAlbumIds)")
    suspend fun deleteNotInAlbumIds(validAlbumIds: List<Long>): Int
    
    /**
     * Clears all cached thumbnails.
     */
    @Query("DELETE FROM album_thumbnails")
    suspend fun deleteAll()
    
    /**
     * Deletes thumbnails older than specified timestamp.
     * Useful for periodic cache cleanup.
     */
    @Query("DELETE FROM album_thumbnails WHERE cachedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int
}
