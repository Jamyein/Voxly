package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxLyricsResponse
import com.voxly.data.remote.tengx.model.TengxSongDetail
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit API interface for TengX Music.
 * Provides endpoints for searching, retrieving song details, lyrics, and album information.
 *
 * Uses the unified any-listen compatible endpoint (u.y.qq.com/cgi-bin/musicu.fcg).
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
     * Search API using GET request.
     * Endpoint: https://c.y.qq.com/soso/fcgi-bin/client_search_cp
     *
     * Uses QQ Music web API with query parameters.
     *
     * @param url Search endpoint URL
     * @param ct Search type (24)
     * @param qqmusic_ver QQ Music version (1298)
     * @param new_json JSON response flag (1)
     * @param remoteplace Remote place identifier
     * @param searchid Search ID
     * @param t Search type (0=song)
     * @param aggr Aggregation flag (1)
     * @param cr Country code (1)
     * @param catZhida Zhida flag (1)
     * @param lossless Lossless flag (0)
     * @param flag_qc Flag qc (0)
     * @param pageNum Page number
     * @param pageSize Results per page
     * @param keywords Search keywords
     * @param g_tk GTK parameter
     * @param loginUin Login UIN
     * @param hostUin Host UIN
     * @param format Response format
     * @param inCharset Input charset
     * @param outCharset Output charset
     * @param notice Notice flag
     * @param platform Platform
     * @param needNewCode Need new code flag
     * @return Search response as string
     */
    @GET
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://y.qq.com/",
        "Origin: https://y.qq.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun search(
        @Url url: String = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp",
        @Query("ct") ct: Int = 24,
        @Query("qqmusic_ver") qqmusic_ver: Int = 1298,
        @Query("new_json") new_json: Int = 1,
        @Query("remoteplace") remoteplace: String = "txt.yqq.song",
        @Query("searchid") searchid: String = "",
        @Query("t") t: Int = 0,
        @Query("aggr") aggr: Int = 1,
        @Query("cr") cr: String = "1",
        @Query("catZhida") catZhida: Int = 1,
        @Query("lossless") lossless: Int = 0,
        @Query("flag_qc") flag_qc: Int = 0,
        @Query("p") pageNum: Int = 1,
        @Query("n") pageSize: Int = 20,
        @Query("w") keywords: String,
        @Query("g_tk") g_tk: Int = 5381,
        @Query("loginUin") loginUin: String = "0",
        @Query("hostUin") hostUin: Int = 0,
        @Query("format") format: String = "json",
        @Query("inCharset") inCharset: String = "utf8",
        @Query("outCharset") outCharset: String = "utf-8",
        @Query("notice") notice: Int = 0,
        @Query("platform") platform: String = "yqq",
        @Query("needNewCode") needNewCode: Int = 0
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
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://y.qq.com/",
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
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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
