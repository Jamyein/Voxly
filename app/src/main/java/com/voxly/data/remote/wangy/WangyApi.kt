package com.voxly.data.remote.wangy

import com.voxly.data.remote.NetworkConstants
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for WangY Music.
 * Supports simple web API, WeAPI (encrypted), and LinuxAPI (encrypted) endpoints.
 *
 * Reference: https://note.ldper.com/netease-music-api-interface.html
 * WeAPI implementation参考: music-tag-web applications/utils/encrypt.py
 */
interface WangyApi {

    companion object {
        const val BASE_URL = "https://music.163.com/"
        const val WEAPI_BASE_URL = "https://music.163.com/weapi/"
        const val LINUX_API_URL = "https://music.163.com/api/linux/forward"
        const val EAPI_BASE_URL = "https://music.163.com/eapi/"
        const val REFERER = "https://music.163.com/"
    }

    /**
     * Searches for songs using LinuxAPI encryption.
     * Endpoint: /api/linux/forward
     *
     * This uses the Linux client API which only requires AES encryption.
     * Simpler than WeAPI but uses fixed key.
     * Reference: music-tag-web applications/utils/encrypt.py
     *
     * @param eparams Encrypted params (from linuxEncrypt)
     * @return Search response
     */
    @POST("api/linux/forward")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_LINUX}",
        "Referer: $REFERER",
        "Content-Type: application/x-www-form-urlencoded",
        "Accept: application/json"
    )
    suspend fun searchSongsLinuxApi(
        @Body body: RequestBody
    ): Response<WangySearchResponse>

    /**
     * Searches for songs using EAPI encryption.
     * Endpoint: /api/search/song/list/page
     *
     * Uses EAPI encryption (AES-ECB with MD5 signature).
     * Reference: any-listen-extension implementation
     *
     * @param body Encrypted request body with params key (from WangyCrypto.eapiEncrypt)
     * @return Search response
     */
    @POST("api/search/song/list/page")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Origin: https://music.163.com",
        "Content-Type: application/x-www-form-urlencoded",
        "Accept: application/json"
    )
    suspend fun searchSongsEapi(
        @Body body: RequestBody
    ): Response<WangySearchResponse>

    /**
     * Searches for songs using simple web search (no encryption).
     * Endpoint: /api/search/get/web
     *
     * This is the simplest endpoint that doesn't require encryption.
     * Returns search results in a simpler format.
     *
     * @param keyword Search keywords
     * @param type Search type: 1=song, 100=artist, 10=album, 1000=playlist
     * @param offset Result offset for pagination
     * @param limit Max results per page
     * @return Search response
     */
    @GET("api/search/get/web")
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://music.163.com/",
        "Origin: https://music.163.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8",
        "Accept-Encoding: gzip, deflate, br"
    )
    suspend fun searchSongsWeb(
        @Query("s") keyword: String,
        @Query("type") type: Int = 1,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("total") total: Boolean = true
    ): Response<WangySearchResponse>

    /**
     * Searches for songs using simple web search (no encryption).
     * Endpoint: /api/search/get
     *
     * This is the simplest endpoint that doesn't require encryption.
     * Uses the old API format with @Query("s") parameter.
     *
     * @param keyword Search keywords
     * @param type Search type: 1=song, 100=artist, 10=album, 1000=playlist
     * @param offset Result offset for pagination
     * @param limit Max results per page
     * @param total Whether to return total count
     * @return Search response
     */
    @GET("api/search/get")
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://music.163.com/",
        "Origin: https://music.163.com",
        "X-Requested-With: XMLHttpRequest",
        "Accept: */*",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8",
        "Accept-Encoding: gzip, deflate, br"
    )
    suspend fun searchSongsSimple(
        @Query("s") keyword: String,
        @Query("type") type: Int = 1,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("total") total: Boolean = true
    ): Response<WangySearchResponse>

    /**
     * Gets lyrics for a song using simple GET request.
     * Endpoint: /api/song/lyric
     *
     * @param songId Song ID
     * @param os OS parameter (pc, android, iphone)
     * @param lv Lyrics version (-1 for all)
     * @param tv Translation version (-1 for all)
     * @return Lyrics response
     */
    @GET("api/song/lyric")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Accept: application/json"
    )
    suspend fun getLyrics(
        @Query("id") songId: Long,
        @Query("os") os: String = "pc",
        @Query("lv") lv: Int = -1,
        @Query("tv") tv: Int = -1
    ): Response<WangyLyricsResponse>

    /**
     * Gets lyrics for a song using EAPI encryption.
     * Endpoint: /eapi/song/lyric/v1
     *
     * Returns enhanced lyrics including:
     * - lrc: standard lyrics
     * - tlyric: translated lyrics
     * - romalrc: romanized lyrics
     * - yrc: synced lyrics with word-level timing
     * - ytlrc: translated synced lyrics
     * - yromalrc: romanized synced lyrics
     *
     * Reference: any-listen-extension implementation
     *
     * @param body Encrypted request body with params key (from WangyCrypto.eapiEncrypt)
     *             Should contain: id, cp: false, tv: 0, lv: 0, rv: 0, kv: 0, yv: 0, ytv: 0, yrv: 0
     * @return Enhanced lyrics response
     */
    @POST("eapi/song/lyric/v1")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Content-Type: application/x-www-form-urlencoded",
        "Accept: application/json"
    )
    suspend fun getLyricsEapi(
        @Body body: RequestBody
    ): Response<WangyLyricsResponse>

    /**
     * Gets detailed information about songs.
     * Endpoint: /api/song/detail
     *
     * @param songIds Comma-separated song IDs in format: [id1,id2,...]
     * @return Song detail response
     */
    @GET("api/song/detail")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Accept: application/json"
    )
    suspend fun getSongDetail(
        @Query("ids") songIds: String
    ): Response<WangySongDetail>

    /**
     * Gets album details.
     * Endpoint: /api/album/detail
     *
     * @param albumId Album ID
     * @return Album detail response
     */
    @GET("api/album/detail")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Accept: application/json"
    )
    suspend fun getAlbumDetail(
        @Query("id") albumId: Long
    ): Response<WangyAlbumDetail>

    /**
     * Gets detailed information about songs using WeAPI encryption.
     * Endpoint: /weapi/v3/song/detail
     *
     * Uses WeAPI encryption (weapiEncrypt) which provides more complete data.
     * Reference: any-listen-extension implementation
     *
     * @param body Encrypted request body with params and encSecKey (from WangyCrypto.weapiEncrypt)
     * @return Song detail response with cover art in songs[].al.picUrl
     */
    @POST("weapi/v3/song/detail")
    @Headers(
        "User-Agent: ${NetworkConstants.USER_AGENT_PC}",
        "Referer: $REFERER",
        "Origin: https://music.163.com",
        "Content-Type: application/x-www-form-urlencoded",
        "Accept: application/json"
    )
    suspend fun getSongDetailWeapi(
        @Body body: RequestBody
    ): Response<WangySongDetail>
}
