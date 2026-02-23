package com.voxly.data.remote.wangy

import com.voxly.data.remote.NetworkConstants
import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * Retrofit API interface for WangY Music.
 * Uses simple web API (no encryption required).
 */
interface WangyApi {

    companion object {
        const val BASE_URL = "https://music.163.com/"
        const val REFERER = "https://music.163.com/"
    }

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
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun searchSongsSimple(
        @Query("s") keyword: String,
        @Query("type") type: Int = 1,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("total") total: Boolean = true
    ): Response<ResponseBody>

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
}
