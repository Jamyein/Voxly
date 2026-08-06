package com.voxly.data.remote.wangy

import com.voxly.data.remote.wangy.model.WangySearchResponse
import com.voxly.data.remote.wangy.model.WangySong
import com.voxly.data.remote.wangy.model.WangyAlbum
import com.voxly.data.remote.wangy.model.WangyArtist
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * 验证网易云 simple search API 响应解析：专辑（album）字段必须被正确解析。
 *
 * 背景：元数据搜索页网易云源曾出现"歌手旁不显示专辑"的问题，根因之一是
 * 搜索解析若丢失 album 字段，会产出空串，进而堵住合并时的真实专辑名。
 */
class WangySearchAlbumParseTest {

    private lateinit var api: WangyApi
    private lateinit var repository: WangyRepository

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        repository = WangyRepositoryImpl(api)
    }

    private fun searchResponseJson(albumFieldName: String): String {
        // 网易云旧版 /api/search/get 歌曲对象带 "album" 字段；新版带 "al"
        val albumJson = if (albumFieldName == "album") {
            """"album": {"id": 2001, "name": "Test Album", "picUrl": "https://p2.music.126.net/x.jpg", "artist": {"id": 1, "name": "Test Artist"}}"""
        } else {
            """"al": {"id": 2001, "name": "Test Album", "picUrl": "https://p2.music.126.net/x.jpg", "artist": {"id": 1, "name": "Test Artist"}}"""
        }
        return """
            {
              "code": 200,
              "result": {
                "songs": [
                  {
                    "id": 123,
                    "name": "Test Song",
                    "artists": [{"id": 1, "name": "Test Artist"}],
                    "ar": [{"id": 1, "name": "Test Artist"}],
                    $albumJson,
                    "duration": 180000,
                    "dt": 180000
                  }
                ]
              }
            }
        """.trimIndent()
    }

    @Test
    fun `search parses album from 'album' field (legacy API format)`() = runBlocking {
        val body = mockk<ResponseBody>()
        every { body.string() } returns searchResponseJson("album")
        val response = mockk<Response<ResponseBody>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns body
        every { response.code() } returns 200
        coEvery { api.searchSongsSimple(keyword = any(), type = any(), offset = any(), limit = any(), total = any()) } returns response

        val result = repository.searchSongs("test")
        assertTrue(result.isSuccess)
        val song = result.getOrNull()?.result?.songs?.firstOrNull()
        assertEquals("Test Song", song?.name)
        assertEquals("Test Album", song?.album?.name)
    }

    @Test
    fun `search parses album from 'al' field (new API format)`() = runBlocking {
        val body = mockk<ResponseBody>()
        every { body.string() } returns searchResponseJson("al")
        val response = mockk<Response<ResponseBody>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns body
        every { response.code() } returns 200
        coEvery { api.searchSongsSimple(keyword = any(), type = any(), offset = any(), limit = any(), total = any()) } returns response

        val result = repository.searchSongs("test")
        assertTrue(result.isSuccess)
        val song = result.getOrNull()?.result?.songs?.firstOrNull()
        assertEquals("Test Song", song?.name)
        assertEquals("Test Album", song?.album?.name)
    }

    @Test
    fun `search tolerates missing album field`() = runBlocking {
        val body = mockk<ResponseBody>()
        every { body.string() } returns """
            {
              "code": 200,
              "result": {
                "songs": [
                  {"id": 123, "name": "Test Song", "artists": [{"id": 1, "name": "A"}], "duration": 180000}
                ]
              }
            }
        """.trimIndent()
        val response = mockk<Response<ResponseBody>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns body
        every { response.code() } returns 200
        coEvery { api.searchSongsSimple(keyword = any(), type = any(), offset = any(), limit = any(), total = any()) } returns response

        val result = repository.searchSongs("test")
        assertTrue(result.isSuccess)
        // album 缺失时 song 仍应解析成功（album 为 null），不抛异常
        assertEquals("Test Song", result.getOrNull()?.result?.songs?.firstOrNull()?.name)
    }
}
