package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit API interface for WangY Music.
 * Provides endpoints for searching, retrieving song details, lyrics, and album information.
 * 
 * Uses EAPI and WeAPI encryption schemes based on the any-listen reference implementation.
 */
interface WangyApi {

    companion object {
        const val BASE_URL = "https://interface3.music.163.com/"
        const val WEB_URL = "https://music.163.com/"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"
    }

    /**
     * Searches for songs using EAPI encryption.
     * Endpoint: /eapi/search/song/list/page
     * 
     * @param url Full URL with path
     * @param encryptedParams EAPI encrypted parameters containing:
     *   - keyword: search keywords
     *   - offset: result offset
     *   - limit: max results
     *   - scene: "normal"
     *   - channel: "typing"
     * @return Search response
     */
    @POST
    @Headers("User-Agent: ${USER_AGENT}", "origin: https://music.163.com")
    suspend fun searchSongs(
        @Url url: String = "${BASE_URL}eapi/search/song/list/page",
        @Body encryptedParams: Map<String, String>
    ): Response<WangySearchResponse>

    /**
     * Gets lyrics for a song using EAPI encryption.
     * Endpoint: /eapi/song/lyric/v1
     * 
     * @param url Full URL with path
     * @param encryptedParams EAPI encrypted parameters containing:
     *   - id: song ID
     *   - cp: false
     *   - tv: 0, lv: 0, kv: 0, rv: 0, yv: 0, ytv: 0, rvk: 0, ytk: 0
     * @return Lyrics response
     */
    @POST
    @Headers("User-Agent: ${USER_AGENT}", "origin: https://music.163.com")
    suspend fun getLyrics(
        @Url url: String = "${BASE_URL}eapi/song/lyric/v1",
        @Body encryptedParams: Map<String, String>
    ): Response<WangyLyricsResponse>

    /**
     * Gets detailed information about songs using WeAPI encryption.
     * Endpoint: /weapi/v3/song/detail
     * 
     * @param url Full URL with path
     * @param encryptedParams WeAPI encrypted parameters containing:
     *   - c: JSON array of song objects
     *   - ids: JSON array of song IDs
     * @return Song detail response
     */
    @POST
    @Headers("User-Agent: ${USER_AGENT}", "origin: https://music.163.com")
    suspend fun getSongDetail(
        @Url url: String = "${WEB_URL}weapi/v3/song/detail",
        @Body encryptedParams: Map<String, String>
    ): Response<WangySongDetail>

    /**
     * Gets album details using WeAPI encryption.
     * Endpoint: /weapi/v1/album/detail
     * 
     * @param url Full URL with path
     * @param encryptedParams WeAPI encrypted parameters containing:
     *   - id: album ID
     * @return Album detail response
     */
    @POST
    @Headers("User-Agent: ${USER_AGENT}", "origin: https://music.163.com")
    suspend fun getAlbumDetail(
        @Url url: String = "${WEB_URL}weapi/v1/album/detail",
        @Body encryptedParams: Map<String, String>
    ): Response<WangyAlbumDetail>
}
