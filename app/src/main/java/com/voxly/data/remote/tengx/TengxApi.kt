package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchRequest
import com.voxly.data.remote.tengx.model.TengxSearchResponse
import com.voxly.data.remote.tengx.model.TengxSongDetail
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit API interface for TengX Music.
 * Provides endpoints for searching, retrieving song details, lyrics, and album information.
 * 
 * Base URLs:
 * - Search API: https://u.y.qq.com/
 * - Lyrics API: https://c.y.qq.com/
 */
interface TengxApi {

    companion object {
        /** Search API base URL */
        const val SEARCH_BASE_URL = "https://u.y.qq.com/"
        /** Lyrics API base URL */
        const val LYRIC_BASE_URL = "https://c.y.qq.com/"
        /** Default g_tk parameter for QQ Music API */
        const val G_TK = 5381
    }

    /**
     * Searches for songs using POST request with JSON body.
     *
     * @param request Search request body containing query parameters
     * @return Search response with song/album/artist results
     */
    @POST("cgi-bin/musicu.fcg")
    suspend fun search(
        @Body request: TengxSearchRequest
    ): Response<TengxSearchResponse>

    /**
     * Gets lyrics for a song using GET request with query parameters.
     * Uses different base URL for lyrics API.
     *
     * @param songmid Song middle ID (songmid from search results)
     * @param g_tk GTK parameter for authentication
     * @param loginUin Login user ID (0 for guest)
     * @param hostUin Host UIN
     * @param format Response format (json)
     * @param inCharset Input charset (utf8)
     * @param outCharset Output charset (utf-8)
     * @param platform Platform (yqq)
     * @param referer Referer header for request
     * @return Lyrics response with Base64 encoded lyrics content
     */
    @GET
    suspend fun getLyrics(
        @Url url: String = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
        @Query("songmid") songmid: String,
        @Query("g_tk") g_tk: Int = G_TK,
        @Query("loginUin") loginUin: Int = 0,
        @Query("hostUin") hostUin: Int = 0,
        @Query("format") format: String = "json",
        @Query("inCharset") inCharset: String = "utf8",
        @Query("outCharset") outCharset: String = "utf-8",
        @Query("platform") platform: String = "yqq",
        @Header("Referer") referer: String = "https://y.qq.com/portal/player.html"
    ): Response<TengxLyricsResponse>

    /**
     * Gets detailed information about songs.
     *
     * @param songIds Comma-separated song IDs
     * @param format Response format (default: json)
     * @return Song detail response
     */
    @GET("cgi-bin/musicu.fcg")
    suspend fun getSongDetail(
        @Query("songids") songIds: String,
        @Query("format") format: String = "json"
    ): Response<TengxSongDetail>

    /**
     * Gets album details and songs.
     *
     * @param albumId Album ID
     * @param format Response format (default: json)
     * @return Album detail response
     */
    @GET("cgi-bin/musicu.fcg")
    suspend fun getAlbumDetail(
        @Query("albumid") albumId: Long,
        @Query("format") format: String = "json"
    ): Response<TengxAlbumDetail>
}
