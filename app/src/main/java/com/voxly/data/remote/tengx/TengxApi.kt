package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchResponse
import com.voxly.data.remote.tengx.model.TengxSongDetail
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit API interface for TengX Music.
 * Provides endpoints for searching, retrieving song details, lyrics, and album information.
 *
 * Uses simplified web API (no complex JSON body required).
 *
 * Base URLs:
 * - Search API: https://c.y.qq.com/
 * - Lyrics API: https://c.y.qq.com/
 */
interface TengxApi {

    companion object {
        /** Search API base URL */
        const val SEARCH_BASE_URL = "https://c.y.qq.com/"
        /** Lyrics API base URL */
        const val LYRIC_BASE_URL = "https://c.y.qq.com/"
        /** Default g_tk parameter for QQ Music API */
        const val G_TK = 5381
        /** User Agent for QQ Music API */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Searches for songs using simple GET request.
     * Endpoint: /soso/fcgi-bin/client_search_cp
     *
     * @param keyword Search keywords
     * @param page Page number (starts from 1)
     * @param perPage Results per page
     * @return Search response with song/album/artist results
     */
    @GET("soso/fcgi-bin/client_search_cp")
    @Headers(
        "User-Agent: $USER_AGENT",
        "Accept: application/json",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun search(
        @Query("w") keyword: String,
        @Query("p") page: Int = 1,
        @Query("n") perPage: Int = 20,
        @Query("ct") ct: Int = 24,
        @Query("qqmusic_ver") qqmusic_ver: Int = 1298,
        @Query("new_json") new_json: Int = 1,
        @Query("remoteplace") remoteplace: String = "txt.yqq.song",
        @Query("g_tk") g_tk: Int = G_TK,
        @Query("format") format: String = "json",
        @Header("Referer") referer: String = "https://y.qq.com/"
    ): Response<TengxSearchResponse>

    /**
     * QQ Music search API v2 (musicu endpoint).
     * Endpoint: https://u.y.qq.com/cgi-bin/musicu.fcg
     */
    @POST
    @Headers(
        "User-Agent: $USER_AGENT",
        "Referer: https://y.qq.com/",
        "Origin: https://y.qq.com",
        "Content-Type: application/json",
        "Accept: application/json",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun searchV2(
        @Url url: String = "https://u.y.qq.com/cgi-bin/musicu.fcg",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<JsonObject>

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
    @Headers(
        "User-Agent: $USER_AGENT",
        "Accept: application/json"
    )
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
    @Headers(
        "User-Agent: $USER_AGENT",
        "Referer: https://y.qq.com/",
        "Accept: application/json"
    )
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
    @Headers(
        "User-Agent: $USER_AGENT",
        "Referer: https://y.qq.com/",
        "Accept: application/json"
    )
    suspend fun getAlbumDetail(
        @Query("albumid") albumId: Long,
        @Query("format") format: String = "json"
    ): Response<TengxAlbumDetail>
}
