package com.voxly.data.remote.lrclib

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for LRCLIB (Lyrics Library).
 * A free and open lyrics API that provides synchronized lyrics.
 * 
 * Base URL: https://lrclib.net/api
 */
interface LRCLibApi {

    companion object {
        const val BASE_URL = "https://lrclib.net/api/"
    }

    /**
     * Searches for lyrics by track name and artist name.
     * 
     * @param track_name Track/song title
     * @param artist_name Artist name
     * @param album_name Album name (optional)
     * @return Search response with matching lyrics
     */
    @GET("search")
    suspend fun searchLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null
    ): Response<LRCLibSearchResponse>

    /**
     * Gets lyrics by ID.
     * 
     * @param id The lyrics ID
     * @return Lyrics details
     */
    @GET("get")
    suspend fun getLyricsById(
        @Query("id") id: Long
    ): Response<LRCLibLyrics>

    /**
     * Gets lyrics by track signature.
     * 
     * @param track_name Track title
     * @param artist_name Artist name
     * @param album_name Album name
     * @param duration Track duration in seconds
     * @return Lyrics details
     */
    @GET("get")
    suspend fun getLyricsBySignature(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") duration: Int? = null
    ): Response<LRCLibLyrics>
}

/**
 * Response model for LRCLIB search.
 */
data class LRCLibSearchResponse(
    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("trackName")
    val trackName: String,

    @SerializedName("artistName")
    val artistName: String,

    @SerializedName("albumName")
    val albumName: String? = null,

    @SerializedName("duration")
    val duration: Double? = null,

    @SerializedName("instrumental")
    val instrumental: Boolean = false,

    @SerializedName("plainLyrics")
    val plainLyrics: String? = null,

    @SerializedName("syncedLyrics")
    val syncedLyrics: String? = null
)

/**
 * Lyrics model from LRCLIB.
 */
data class LRCLibLyrics(
    @SerializedName("id")
    val id: Long,

    @SerializedName("trackName")
    val trackName: String,

    @SerializedName("artistName")
    val artistName: String,

    @SerializedName("albumName")
    val albumName: String? = null,

    @SerializedName("duration")
    val duration: Double? = null,

    @SerializedName("instrumental")
    val instrumental: Boolean = false,

    @SerializedName("plainLyrics")
    val plainLyrics: String? = null,

    @SerializedName("syncedLyrics")
    val syncedLyrics: String? = null
) {
    /**
     * Checks if this entry has lyrics (either synced or plain).
     */
    fun hasLyrics(): Boolean {
        return !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
    }

    /**
     * Gets the best available lyrics (synced preferred over plain).
     */
    fun getBestLyrics(): String? {
        return syncedLyrics ?: plainLyrics
    }
}
