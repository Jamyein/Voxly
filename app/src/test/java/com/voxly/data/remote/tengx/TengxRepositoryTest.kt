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
 */
class TengxRepositoryTest {

    @MockK
    private lateinit var api: TengxApi

    private lateinit var repository: TengxRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = TengxRepositoryImpl(api)
    }

    @Test
    fun searchSongs_returnsSuccess() = runBlocking {
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns TengxFixtures.SEARCH_SUCCESS_JSON
        val mockResponse = Response.success(mockResponseBody)
        coEvery { api.search(keywords = any(), pageNum = any(), pageSize = any()) } returns mockResponse

        val result = repository.searchSongs("test song")

        assertTrue(result.isSuccess)
    }

    @Test
    fun searchSongs_returnsEmpty() = runBlocking {
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns TengxFixtures.SEARCH_EMPTY_JSON
        val mockResponse = Response.success(mockResponseBody)
        coEvery { api.search(keywords = any(), pageNum = any(), pageSize = any()) } returns mockResponse

        val result = repository.searchSongs("nonexistent")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.data?.song?.totalnum)
    }

    @Test
    fun searchSongs_returnsFailure_onNetworkError() = runBlocking {
        coEvery { api.search(keywords = any(), pageNum = any(), pageSize = any()) } throws Exception("Network error")

        val result = repository.searchSongs("test")

        assertTrue(result.isFailure)
    }

    @Test
    fun getLyrics_returnsSuccess() = runBlocking {
        val mockResponse = Response.success(TengxFixtures.sampleLyricsResponse)
        coEvery { api.getLyrics(songmid = any()) } returns mockResponse

        val result = repository.getLyrics("001XXp5G2v8f7c")

        assertTrue(result.isSuccess)
    }

    @Test
    fun getLyrics_returnsFailure_onApiError() = runBlocking {
        val errorResponse = Response.error<TengxLyricsResponse>(500, mockk(relaxed = true))
        coEvery { api.getLyrics(songmid = any()) } returns errorResponse

        val result = repository.getLyrics("invalid")

        assertTrue(result.isFailure)
    }

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
