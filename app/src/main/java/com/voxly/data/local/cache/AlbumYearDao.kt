package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for album year cache operations.
 */
@Dao
interface AlbumYearDao {

    /**
     * Get year for a specific album.
     */
    @Query("SELECT year FROM album_year_cache WHERE albumName = :albumName AND (artist = :artist OR (artist IS NULL AND :artist IS NULL)) LIMIT 1")
    suspend fun getYear(albumName: String, artist: String?): String?

    /**
     * Get years for multiple albums at once.
     * Returns a simple data class with just the needed fields.
     */
    @Query("SELECT albumName, artist, year FROM album_year_cache")
    suspend fun getAllYears(): List<AlbumYearTuple>

    /**
     * Save or update album year.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AlbumYearEntity)

    /**
     * Save or update multiple album years at once.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<AlbumYearEntity>)

    /**
     * Clear all cached album years.
     */
    @Query("DELETE FROM album_year_cache")
    suspend fun clearAll()
}

/**
 * Lightweight tuple for reading album year data.
 */
data class AlbumYearTuple(
    val albumName: String,
    val artist: String?,
    val year: String
)
