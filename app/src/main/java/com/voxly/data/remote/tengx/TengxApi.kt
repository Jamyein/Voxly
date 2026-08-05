package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.TengxAlbumDetail
import com.voxly.data.remote.tengx.model.TengxSongDetail
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for QQ Music.
 *
 * All search and lyrics requests go through the unified musicu.fcg endpoint
 * (POST with JSON body {comm, req_0}), matching the current QQ Music mobile
 * client protocol as documented by the Lyrico plugin reference.
 *
 * Song / album detail use GET musicu.fcg — same base, different convention.
 */
interface TengxApi {

    companion object {
        /** Unified API base URL */
        const val BASE_URL = "https://u.y.qq.com/"
    }

    /**
     * Unified musicu.fcg POST — used for search
     * (module music.search.SearchCgiService, method DoSearchForQQMusicLite)
     * and lyrics (module music.musichallSong.PlayLyricInfo, method GetPlayLyricInfo).
     */
    @POST("cgi-bin/musicu.fcg")
    @Headers(
        "User-Agent: Mozilla/5.0",
        "Content-Type: application/json; charset=utf-8"
    )
    suspend fun postMusicu(@Body body: RequestBody): Response<ResponseBody>

    /**
     * Gets detailed information about songs.
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
