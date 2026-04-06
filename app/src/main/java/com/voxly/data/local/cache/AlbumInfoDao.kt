package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AlbumInfoEntity.
 * Provides CRUD operations and queries for album information caching.
 */
@Dao
interface AlbumInfoDao {

    /**
     * Get album info by album name and artist.
     */
    @Query("SELECT * FROM album_info WHERE albumName = :albumName AND (albumArtist = :albumArtist OR (albumArtist IS NULL AND :albumArtist IS NULL))")
    suspend fun getAlbumInfo(albumName: String, albumArtist: String?): AlbumInfoEntity?

    /**
     * Get album info by its unique ID.
     */
    @Query("SELECT * FROM album_info WHERE id = :id")
    suspend fun getAlbumInfoById(id: String): AlbumInfoEntity?

    /**
     * Get all album info as a Flow for reactive updates.
     */
    @Query("SELECT * FROM album_info ORDER BY albumName COLLATE NOCASE ASC")
    fun getAllAlbumInfoFlow(): Flow<List<AlbumInfoEntity>>

    /**
     * Get all album info as a list (one-time query).
     */
    @Query("SELECT * FROM album_info ORDER BY albumName COLLATE NOCASE ASC")
    suspend fun getAllAlbumInfo(): List<AlbumInfoEntity>

    /**
     * Insert or update album info.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AlbumInfoEntity)

    /**
     * Insert multiple album info entries.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(entities: List<AlbumInfoEntity>)

    /**
     * Delete album info by album name and artist.
     */
    @Query("DELETE FROM album_info WHERE albumName = :albumName AND (albumArtist = :albumArtist OR (albumArtist IS NULL AND :albumArtist IS NULL))")
    suspend fun deleteByAlbum(albumName: String, albumArtist: String?)

    /**
     * Delete album info by ID.
     */
    @Query("DELETE FROM album_info WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete album info for albums that no longer have matching files.
     * This is useful for cleanup after file deletion.
     */
    @Query("""
        DELETE FROM album_info 
        WHERE id NOT IN (
            SELECT DISTINCT ai.id 
            FROM album_info ai 
            INNER JOIN cached_audio_files caf 
            ON caf.album = ai.albumName 
            AND (caf.albumArtist = ai.albumArtist OR (caf.albumArtist IS NULL AND ai.albumArtist IS NULL))
        )
    """)
    suspend fun deleteOrphanedAlbums()

    /**
     * Get years for a list of albums.
     * Returns map of "albumName|albumArtist" -> year
     */
    @Query("""
        SELECT albumName, albumArtist, year FROM album_info 
        WHERE albumName IN (:albumNames)
    """)
    suspend fun getAlbumYearsForNames(albumNames: List<String>): List<AlbumYearTuple>

    /**
     * Get album info by album name (may return multiple if different artists).
     */
    @Query("SELECT * FROM album_info WHERE albumName = :albumName")
    suspend fun getAlbumsByName(albumName: String): List<AlbumInfoEntity>

    /**
     * Delete all album info entries.
     */
    @Query("DELETE FROM album_info")
    suspend fun deleteAll()

    /**
     * Get count of album info entries.
     */
    @Query("SELECT COUNT(*) FROM album_info")
    suspend fun getCount(): Int

    /**
     * Check if album info exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM album_info WHERE albumName = :albumName AND (albumArtist = :albumArtist OR (albumArtist IS NULL AND :albumArtist IS NULL)))")
    suspend fun exists(albumName: String, albumArtist: String?): Boolean

    /**
     * Data class for year query results.
     */
    data class AlbumYearTuple(
        val albumName: String,
        val albumArtist: String?,
        val year: String?
    ) {
        /**
         * Creates a unique key for this album.
         */
        fun getKey(): String = "$albumName|${albumArtist ?: ""}"
    }

    /**
     * Transaction: Delete and insert in one transaction.
     * Useful for batch updates.
     */
    @Transaction
    suspend fun replaceAll(entities: List<AlbumInfoEntity>) {
        deleteAll()
        insertOrUpdateAll(entities)
    }

    /**
     * Get albums with years in a specific range.
     */
    @Query("SELECT * FROM album_info WHERE year BETWEEN :startYear AND :endYear ORDER BY year DESC")
    suspend fun getAlbumsInYearRange(startYear: String, endYear: String): List<AlbumInfoEntity>

    /**
     * Search albums by name pattern.
     */
    @Query("SELECT * FROM album_info WHERE albumName LIKE '%' || :query || '%' ORDER BY albumName COLLATE NOCASE ASC")
    suspend fun searchByName(query: String): List<AlbumInfoEntity>
}
