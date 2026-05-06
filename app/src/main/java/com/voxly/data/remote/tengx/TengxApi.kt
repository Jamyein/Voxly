package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSearchRequest
import com.voxly.data.remote.tengx.model.TengxSongDetail
import okhttp3.ResponseBody
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
 * Search API based on any-listen-extension-online-metadata:
 * https://github.com/any-listen/any-listen-extension-online-metadata
 * Reference: src/qq_music/index.ts
 *
 * Base URLs:
 * - API: https://u.y.qq.com/
 * - Lyrics API: https://c.y.qq.com/
 */
interface TengxApi {

    companion object {
        /** Unified API base URL (any-listen compatible) */
        const val BASE_URL = "https://u.y.qq.com/"
        /** Lyrics API base URL */
        const val LYRIC_BASE_URL = "https://c.y.qq.com/"
    }

    /**
     * Search API using POST request with zzcSign signature.
     * Endpoint: https://u.y.qq.com/cgi-bin/musics.fcg?sign={sign}
     *
     * Uses QQ Music mobile API with JSON body and signature.
     *
     * @param sign zzcSign signature for the request body
     * @param body Search request body (TengxSearchRequest serialized to JSON)
     * @return Search response as string
     */
    @POST("cgi-bin/musics.fcg")
    @Headers(
        "User-Agent: QQMusic 14090508(android 12)",
        "Referer: https://y.qq.com/",
        "Origin: https://y.qq.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8",
        "Content-Type: application/json"
    )
    suspend fun search(
        @Query("sign") sign: String,
        @Body body: com.voxly.data.remote.tengx.model.TengxSearchRequest
    ): Response<ResponseBody>

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
        "User-Agent: QQMusic 14090508(android 12)",
        "Referer: https://y.qq.com/portal/player.html",
        "Origin: https://y.qq.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun getLyrics(
        @Url url: String = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
        @Query("songmid") songmid: String,
        @Query("g_tk") g_tk: Int = 5381,
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
        "User-Agent: QQMusic 14090508(android 12)",
        "Referer: https://y.qq.com/",
        "Origin: https://y.qq.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
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
        "User-Agent: QQMusic 14090508(android 12)",
        "Referer: https://y.qq.com/",
        "Origin: https://y.qq.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun getAlbumDetail(
        @Query("albumid") albumId: Long,
        @Query("format") format: String = "json"
    ): Response<TengxAlbumDetail>
}
