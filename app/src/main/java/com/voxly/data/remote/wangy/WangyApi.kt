package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.model.WangyAlbumDetail
import com.voxly.data.remote.wangy.model.WangyLyricsResponse
import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySongDetail
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for WangY Music.
 * Uses simplified web API endpoints (no encryption required).
 *
 * Reference: https://note.ldper.com/netease-music-api-interface.html
 */
interface WangyApi {

    companion object {
        const val BASE_URL = "https://music.163.com/"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val REFERER = "https://music.163.com/"
    }

    /**
     * Searches for songs using simple GET request (no encryption).
     * Endpoint: /api/search/get
     *
     * @param keyword Search keywords
     * @param type Search type: 1=song, 100=artist, 10=album, 1000=playlist
     * @param offset Result offset for pagination
     * @param limit Max results per page
     * @return Search response
     */
    @GET("api/search/get")
    @Headers(
        "User-Agent: $USER_AGENT",
        "Referer: $REFERER",
        "Accept: application/json",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun searchSongsLegacy(
        @Query("s") keyword: String,
        @Query("type") type: Int = 1,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("total") total: Boolean = true
    ): Response<WangySearchResponse>

    /**
     * Searches for songs using cloud search endpoint.
     * Endpoint: /api/cloudsearch/pc
     *
     * This endpoint is also used by NetEase web search and is more stable than legacy /api/search/get.
     */
    @FormUrlEncoded
    @POST("api/cloudsearch/pc")
    @Headers(
        "User-Agent: $USER_AGENT",
        "Referer: $REFERER",
        "Origin: https://music.163.com",
        "X-Requested-With: XMLHttpRequest",
        "Content-Type: application/x-www-form-urlencoded",
        "Accept: application/json",
        "Accept-Language: zh-CN,zh;q=0.9,en;q=0.8"
    )
    suspend fun searchSongsCloud(
        @Field("s") keyword: String,
        @Field("type") type: Int = 1,
        @Field("offset") offset: Int = 0,
        @Field("limit") limit: Int = 30,
        @Field("total") total: Boolean = true
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
        "User-Agent: $USER_AGENT",
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
        "User-Agent: $USER_AGENT",
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
        "User-Agent: $USER_AGENT",
        "Referer: $REFERER",
        "Accept: application/json"
    )
    suspend fun getAlbumDetail(
        @Query("id") albumId: Long
    ): Response<WangyAlbumDetail>
}
