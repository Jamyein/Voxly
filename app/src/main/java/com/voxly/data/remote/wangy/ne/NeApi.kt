package com.voxly.data.remote.wangy.ne

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

/**
 * Retrofit API interface for Netease Cloud Music EAPI.
 * 
 * Uses the interface.music.163.com endpoint which supports
 * EAPI encryption with anonymous login.
 */
interface NeApi {

    /**
     * Generic EAPI request handler.
     * 
     * @param url Full URL including endpoint path
     * @param headers Request headers (User-Agent, Cookie, Referer, etc.)
     * @param body Encrypted request body (params=hex string)
     * @return Response body
     */
    @POST
    suspend fun request(
        @retrofit2.http.Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://interface.music.163.com/"
        
        // Common headers
        fun buildCommonHeaders(cookie: String): Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/${NeCrypto.APP_VER}",
            "Referer" to "https://music.163.com/",
            "Cookie" to cookie,
            "Accept" to "*/*",
            "Host" to "interface.music.163.com"
        )
        
        // Login headers (without authentication cookie)
        fun buildLoginHeaders(cookie: String): Map<String, String> = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/${NeCrypto.APP_VER}",
            "Referer" to "https://music.163.com/",
            "Cookie" to cookie,
            "Accept" to "*/*",
            "Host" to "interface.music.163.com"
        )
    }
}
