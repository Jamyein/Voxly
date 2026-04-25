package com.voxly.data.local.cache

import androidx.room.Dao
import androidx.room.Query

@Dao
interface AlbumSummaryDao {
    @Query("""
        SELECT 
            :albumTitle AS albumTitle,
            :albumArtist AS albumArtist,
            COUNT(*) AS songCount,
            MAX(NULLIF(year, '')) AS year,
            MAX(sampleRate) AS maxSampleRate,
            MAX(bitrate) AS maxBitrate
        FROM cached_audio_files
        WHERE album = :albumTitle AND (albumArtist = :albumArtist OR :albumArtist IS NULL)
    """)
    suspend fun getAlbumSummary(albumTitle: String, albumArtist: String?): AlbumSummary?

    @Query("""
        SELECT 
            album AS albumTitle,
            albumArtist AS albumArtist,
            COUNT(*) AS songCount,
            MAX(NULLIF(year, '')) AS year,
            MAX(sampleRate) AS maxSampleRate,
            MAX(bitrate) AS maxBitrate
        FROM cached_audio_files
        WHERE album IS NOT NULL AND album != ''
        GROUP BY albumArtist, album
    """)
    suspend fun getAllAlbumSummaries(): List<AlbumSummary>

    @Query("""
        SELECT 
            album AS albumTitle,
            albumArtist AS albumArtist,
            COUNT(*) AS songCount,
            MAX(NULLIF(year, '')) AS year,
            MAX(sampleRate) AS maxSampleRate,
            MAX(bitrate) AS maxBitrate
        FROM cached_audio_files
        WHERE album IN (:albumTitles) AND album IS NOT NULL AND album != ''
        GROUP BY albumArtist, album
    """)
    suspend fun getAlbumSummariesByNames(albumTitles: List<String>): List<AlbumSummary>
}

data class AlbumSummary(
    val albumTitle: String,
    val albumArtist: String?,
    val songCount: Int,
    val year: String?,
    val maxSampleRate: Int,
    val maxBitrate: Int
)
