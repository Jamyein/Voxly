package com.voxly.data.local.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cached audio files.
 * Provides efficient queries for instant app startup.
 */
@Dao
interface CachedAudioFileDao {
    
    // ==================== Queries ====================
    
    /**
     * Gets all cached audio files, sorted by title.
     * Returns Flow for reactive UI updates.
     */
    @Query("SELECT * FROM cached_audio_files ORDER BY COALESCE(title, name) ASC")
    fun getAllAudioFiles(): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets all cached audio files as a one-shot query.
     * Use this when Flow is not needed.
     */
    @Query("SELECT * FROM cached_audio_files ORDER BY COALESCE(title, name) ASC")
    suspend fun getAllAudioFilesOnce(): List<CachedAudioFileEntity>
    
    /**
     * Gets audio files by directory path prefix.
     */
    @Query("SELECT * FROM cached_audio_files WHERE path LIKE :directoryPath || '%' ORDER BY path ASC")
    fun getAudioFilesByDirectory(directoryPath: String): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets a single audio file by path.
     */
    @Query("SELECT * FROM cached_audio_files WHERE path = :path LIMIT 1")
    suspend fun getAudioFileByPath(path: String): CachedAudioFileEntity?
    
    /**
     * Gets audio files by album ID.
     */
    @Query("SELECT * FROM cached_audio_files WHERE albumId = :albumId ORDER BY trackNumber ASC, COALESCE(title, name) ASC")
    fun getAudioFilesByAlbum(albumId: Long): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Searches audio files by title, artist, or album.
     */
    @Query("""
        SELECT * FROM cached_audio_files 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY COALESCE(title, name) ASC
    """)
    fun searchAudioFiles(query: String): Flow<List<CachedAudioFileEntity>>
    
    /**
     * Gets all distinct artists.
     */
    @Query("SELECT DISTINCT artist FROM cached_audio_files WHERE artist IS NOT NULL AND artist != '' ORDER BY artist ASC")
    suspend fun getAllArtists(): List<String>
    
    /**
     * Gets all distinct albums.
     */
    @Query("SELECT DISTINCT album, albumId FROM cached_audio_files WHERE album IS NOT NULL AND album != '' ORDER BY album ASC")
    suspend fun getAllAlbums(): List<AlbumInfo>
    
    /**
     * Data class for album info.
     */
    data class AlbumInfo(
        val album: String,
        val albumId: Long?
    )
    
    /**
     * Gets count of cached files.
     */
    @Query("SELECT COUNT(*) FROM cached_audio_files")
    suspend fun getCachedFileCount(): Int
    
    /**
     * Gets the last scan timestamp.
     */
    @Query("SELECT MAX(lastScannedAt) FROM cached_audio_files")
    suspend fun getLastScanTime(): Long?
    
    /**
     * Checks if cache has any data.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM cached_audio_files LIMIT 1)")
    suspend fun hasCache(): Boolean
    
    /**
     * Gets file paths and modification times for incremental scan.
     */
    @Query("SELECT path, fileLastModifiedAt FROM cached_audio_files")
    suspend fun getFilePathsWithModificationTimes(): List<FilePathWithModification>
    
    /**
     * Data class for file path with modification time.
     */
    data class FilePathWithModification(
        val path: String,
        val fileLastModifiedAt: Long
    )
    
    // ==================== Inserts/Updates ====================
    
    /**
     * Inserts a single audio file.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audioFile: CachedAudioFileEntity)
    
    /**
     * Inserts multiple audio files efficiently.
     * Uses transaction internally for batch insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(audioFiles: List<CachedAudioFileEntity>)
    
    /**
     * Inserts multiple audio files in chunks for very large libraries.
     * Prevents memory issues with 10,000+ files.
     */
    @Transaction
    suspend fun insertAllChunked(audioFiles: List<CachedAudioFileEntity>, chunkSize: Int = 500) {
        audioFiles.chunked(chunkSize).forEach { chunk ->
            insertAll(chunk)
        }
    }
    
    /**
     * Updates a single audio file.
     */
    @Update
    suspend fun update(audioFile: CachedAudioFileEntity)
    
    // ==================== Deletes ====================
    
    /**
     * Deletes a single audio file by path.
     */
    @Query("DELETE FROM cached_audio_files WHERE path = :path")
    suspend fun deleteByPath(path: String)
    
    /**
     * Deletes multiple audio files by paths.
     */
    @Transaction
    suspend fun deleteByPaths(paths: List<String>) {
        paths.forEach { path ->
            deleteByPath(path)
        }
    }
    
    /**
     * Deletes audio files not in the provided list of paths.
     * Used to clean up deleted files.
     */
    @Query("DELETE FROM cached_audio_files WHERE path NOT IN (:validPaths)")
    suspend fun deleteNotInPaths(validPaths: List<String>): Int
    
    /**
     * Clears all cached audio files.
     */
    @Query("DELETE FROM cached_audio_files")
    suspend fun deleteAll()

    // ==================== Statistics Queries ====================

    /**
     * Gets total count of audio files.
     */
    @Query("SELECT COUNT(*) FROM cached_audio_files")
    suspend fun getTotalFileCount(): Int

    /**
     * Gets total duration in milliseconds.
     */
    @Query("SELECT COALESCE(SUM(duration), 0) FROM cached_audio_files")
    suspend fun getTotalDuration(): Long

    /**
     * Gets total file size in bytes.
     */
    @Query("SELECT COALESCE(SUM(size), 0) FROM cached_audio_files")
    suspend fun getTotalSize(): Long

    /**
     * Gets format distribution - format and count.
     */
    @Query("SELECT format, COUNT(*) as count FROM cached_audio_files GROUP BY format ORDER BY count DESC")
    suspend fun getFormatDistribution(): List<FormatCount>

    /**
     * Gets top artists by file count.
     */
    @Query("""
        SELECT artist, COUNT(*) as count
        FROM cached_audio_files
        WHERE artist IS NOT NULL AND artist != ''
        GROUP BY artist
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getTopArtists(limit: Int): List<ArtistCount>

    /**
     * Gets top albums by file count.
     */
    @Query("""
        SELECT album, artist, COUNT(*) as count
        FROM cached_audio_files
        WHERE album IS NOT NULL AND album != ''
        GROUP BY album, artist
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getTopAlbums(limit: Int): List<AlbumCount>
}

/**
 * Data class for format count.
 */
data class FormatCount(
    val format: String,
    val count: Int
)

/**
 * Data class for artist count.
 */
data class ArtistCount(
    val artist: String,
    val count: Int
)

/**
 * Data class for album count.
 */
data class AlbumCount(
    val album: String,
    val artist: String?,
    val count: Int
)
