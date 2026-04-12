package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for AlbumSortOrderEntity.
 * Provides CRUD operations for album sort order caching.
 */
@Dao
interface AlbumSortOrderDao {

    /**
     * Get sort order for a specific sort option.
     */
    @Query("SELECT * FROM album_sort_order WHERE sortOption = :sortOption")
    suspend fun getSortOrder(sortOption: String): AlbumSortOrderEntity?

    /**
     * Insert or replace sort order.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sortOrder: AlbumSortOrderEntity)

    /**
     * Delete all sort orders.
     */
    @Query("DELETE FROM album_sort_order")
    suspend fun deleteAll()

    /**
     * Delete sort order for a specific option.
     */
    @Query("DELETE FROM album_sort_order WHERE sortOption = :sortOption")
    suspend fun delete(sortOption: String)
}
