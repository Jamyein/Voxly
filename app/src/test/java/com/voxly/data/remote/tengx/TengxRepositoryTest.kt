package com.voxly.data.remote.tengx

import com.voxly.data.remote.tengx.model.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Response

/**
 * Unit tests for TengxRepository (QQ Music API).
 *
 * Tests the musicu.fcg POST endpoint paths used by the Lyrico-aligned implementation.
 */
class TengxRepositoryTest {

    @MockK
    private lateinit var api: TengxApi

    private lateinit var repository: TengxRepository

    @Before
    fun setup() {
        api = mockk(relaxed = true)
        repository = TengxRepositoryImpl(api)
    }

    // -- search --

    @Test
    fun searchSongs_returnsSuccess() = runBlocking {
        val mockResponseBody: ResponseBody = mockk()
        every { mockResponseBody.string() } returns TengxFixtures.SEARCH_SUCCESS_JSON

        val mockResponse: Response<ResponseBody> = mockk()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns mockResponseBody
        every { mockResponse.code() } returns 200

        coEvery { api.postMusicu(body = any()) } returns mockResponse

        val result = repository.searchSongs("test song")

        assertTrue(result.isSuccess)
        val songs = result.getOrNull()?.data?.song?.list.orEmpty()
        assertTrue(songs.isNotEmpty())
        assertEquals("Test Song", songs.first().name)
    }

    @Test
    fun searchSongs_returnsEmpty() = runBlocking {
        val mockResponseBody: ResponseBody = mockk()
        every { mockResponseBody.string() } returns TengxFixtures.SEARCH_EMPTY_JSON

        val mockResponse: Response<ResponseBody> = mockk()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns mockResponseBody
        every { mockResponse.code() } returns 200

        coEvery { api.postMusicu(body = any()) } returns mockResponse

        val result = repository.searchSongs("nonexistent")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.data?.song?.totalnum)
    }

    @Test
    fun searchSongs_returnsFailure_onNetworkError() = runBlocking {
        coEvery { api.postMusicu(body = any()) } throws Exception("Network error")

        val result = repository.searchSongs("test")

        assertTrue(result.isFailure)
    }

    // -- lyrics --

    @Test
    fun getLyrics_returnsSuccess() = runBlocking {
        val mockResponseBody: ResponseBody = mockk()
        every { mockResponseBody.string() } returns TengxFixtures.LYRICS_SUCCESS_JSON

        val mockResponse: Response<ResponseBody> = mockk()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns mockResponseBody
        every { mockResponse.code() } returns 200

        coEvery { api.postMusicu(body = any()) } returns mockResponse

        val result = repository.getLyrics(songId = 123456789L)

        assertTrue("getLyrics failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val lyrics = result.getOrNull()!!
        assertTrue("lyrics is blank", lyrics.lyrics.isNotBlank())
        assertTrue("translatedLyrics is blank", lyrics.translatedLyrics.isNotBlank())
    }

    @Test
    fun getLyrics_returnsFailure_onApiError() = runBlocking {
        val errorResponse = Response.error<ResponseBody>(500, mockk(relaxed = true))
        coEvery { api.postMusicu(body = any()) } returns errorResponse

        val result = repository.getLyrics(songId = 0L)

        assertTrue(result.isFailure)
    }

    // -- song detail --

    @Test
    fun getSongDetail_returnsSuccess() = runBlocking {
        val mockDetail = TengxSongDetail(code = 0, data = TengxSongDetailData(track = emptyList()), message = "")
        val mockResponse = Response.success(mockDetail)
        coEvery { api.getSongDetail(songIds = any()) } returns mockResponse

        val result = repository.getSongDetail(listOf(123456789L))

        assertTrue(result.isSuccess)
    }

    @Test
    fun getSongDetail_returnsFailure_onNetworkError() = runBlocking {
        coEvery { api.getSongDetail(songIds = any()) } throws Exception("Network error")

        val result = repository.getSongDetail(listOf(123456789L))

        assertTrue(result.isFailure)
    }

    // -- album detail --

    @Test
    fun getAlbumDetail_returnsSuccess() = runBlocking {
        val mockDetail = TengxAlbumDetail(code = 0, data = TengxAlbumDetailData(album = null, list = emptyList()), message = "")
        val mockResponse = Response.success(mockDetail)
        coEvery { api.getAlbumDetail(albumId = any()) } returns mockResponse

        val result = repository.getAlbumDetail(2001L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun getAlbumDetail_returnsFailure_onHttpError() = runBlocking {
        val errorResponse = Response.error<TengxAlbumDetail>(404, mockk(relaxed = true))
        coEvery { api.getAlbumDetail(albumId = any()) } returns errorResponse

        val result = repository.getAlbumDetail(999999L)

        assertTrue(result.isFailure)
    }
}
