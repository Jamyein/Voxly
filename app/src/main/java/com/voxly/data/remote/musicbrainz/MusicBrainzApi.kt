package com.voxly.data.remote.musicbrainz

import com.voxly.data.remote.musicbrainz.model.MusicBrainzSearchResponse
import com.voxly.data.remote.musicbrainz.model.MusicBrainzReleaseResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface for MusicBrainz web service.
 * Provides endpoints for searching and retrieving music metadata.
 */
interface MusicBrainzApi {

    companion object {
        const val BASE_URL = "https://musicbrainz.org/ws/2/"
        const val USER_AGENT = "MP3TagAndroid/1.0 ( contact@example.com )"
    }

    /**
     * Searches for releases by artist and album title.
     * @param query Search query
     * @param limit Maximum number of results
     * @param offset Result offset for pagination
     * @return Search response with matching releases
     */
    @GET("release-group")
    suspend fun searchReleases(
        @Query("query") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<MusicBrainzSearchResponse>

    /**
     * Searches for recordings by track title and artist.
     * @param query Search query
     * @param limit Maximum number of results
     * @param offset Result offset for pagination
     * @return Search response with matching recordings
     */
    @GET("recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<MusicBrainzSearchResponse>

    /**
     * Gets detailed information about a specific release.
     * @param releaseId The MusicBrainz release ID
     * @param inc Include additional subqueries (e.g., "recordings", "artists")
     * @return Release details
     */
    @GET("release/{release-id}")
    suspend fun getReleaseDetails(
        @Path("release-id") releaseId: String,
        @Query("inc") inc: String = "recordings+artist-credits",
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<MusicBrainzReleaseResponse>

    /**
     * Gets a release group (album) by ID.
     * @param releaseGroupId The MusicBrainz release group ID
     * @param inc Include additional subqueries
     * @return Release group details
     */
    @GET("release-group/{release-group-id}")
    suspend fun getReleaseGroup(
        @Path("release-group-id") releaseGroupId: String,
        @Query("inc") inc: String = "releases",
        @Query("fmt") format: String = "json",
        @Header("User-Agent") userAgent: String = USER_AGENT
    ): Response<MusicBrainzReleaseResponse>
}
