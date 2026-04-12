package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ArtistLinkEntity.
 * Provides CRUD operations and queries for multi-artist support.
 */
@Dao
interface ArtistLinkDao {

    /**
     * Insert artist link.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArtistLinkEntity): Long

    /**
     * Insert multiple artist links.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ArtistLinkEntity>)

    /**
     * Delete all links for a track.
     */
    @Query("DELETE FROM artist_links WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    /**
     * Delete all links for tracks.
     */
    @Query("DELETE FROM artist_links WHERE trackId IN (:trackIds)")
    suspend fun deleteByTrackIds(trackIds: List<String>)

    /**
     * Get all artist names for a track.
     */
    @Query("SELECT artistName FROM artist_links WHERE trackId = :trackId")
    suspend fun getArtistNamesForTrack(trackId: String): List<String>

    /**
     * Get all tracks for an artist.
     */
    @Query("SELECT trackId FROM artist_links WHERE artistName = :artistName")
    suspend fun getTrackIdsForArtist(artistName: String): List<String>

    /**
     * Get all artist names (distinct).
     */
    @Query("SELECT DISTINCT artistName FROM artist_links ORDER BY artistName COLLATE NOCASE ASC")
    fun getAllArtistNames(): Flow<List<String>>

    /**
     * Get artist name to track count.
     */
    @Query("SELECT artistName, COUNT(DISTINCT trackId) as trackCount FROM artist_links GROUP BY artistName ORDER BY artistName COLLATE NOCASE ASC")
    fun getArtistCounts(): Flow<List<ArtistCount>>

    /**
     * Delete all artist links.
     */
    @Query("DELETE FROM artist_links")
    suspend fun deleteAll()

    /**
     * Get count of artist links.
     */
    @Query("SELECT COUNT(*) FROM artist_links")
    suspend fun getCount(): Int

    /**
     * Data class for artist count query.
     */
    data class ArtistCount(
        val artistName: String,
        val trackCount: Int
    )
}