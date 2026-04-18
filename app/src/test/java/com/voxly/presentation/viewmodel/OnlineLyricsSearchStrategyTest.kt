package com.voxly.presentation.viewmodel

import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.LyricsSourceResult
import com.voxly.domain.repository.OnlineLyricsResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLyricsSearchStrategyTest {

    private val mockLyricsRepository = mockk<LyricsRepository>()
    private val strategy = LyricsRepositorySearchStrategy(mockLyricsRepository)

    @Test
    fun `search returns lyrics results`() = runBlocking {
        val mockLyrics = OnlineLyricsResult(
            id = 1L,
            trackName = "Test Song",
            artistName = "Test Artist",
            albumName = "Test Album",
            duration = 180.0,
            hasSyncedLyrics = true,
            hasPlainLyrics = false,
            isInstrumental = false,
            source = "NetEase",
            preview = "Preview lyrics"
        )

        coEvery {
            mockLyricsRepository.searchOnlineLyricsFlow("Test Song", "Test Artist", "Test Album")
        } returns kotlinx.coroutines.flow.flowOf(
            LyricsSourceResult.Result(mockLyrics, "NetEase")
        )

        val results = strategy.search("Test Song", "Test Artist", "Test Album").first()

        assertTrue(results is LyricsSearchResult.Result)
    }

    @Test
    fun `search returns source completed`() = runBlocking {
        coEvery {
            mockLyricsRepository.searchOnlineLyricsFlow("Test Song", null, null)
        } returns kotlinx.coroutines.flow.flowOf(
            LyricsSourceResult.SourceCompleted("NetEase")
        )

        val results = strategy.search("Test Song", null, null).first()

        assertEquals(LyricsSearchResult.SourceCompleted::class, results::class)
        assertEquals("NetEase", (results as LyricsSearchResult.SourceCompleted).source)
    }

    @Test
    fun `search returns error on failure`() = runBlocking {
        coEvery {
            mockLyricsRepository.searchOnlineLyricsFlow("Test Song", null, null)
        } returns kotlinx.coroutines.flow.flowOf(
            LyricsSourceResult.Error("NetEase", "Network error")
        )

        val results = strategy.search("Test Song", null, null).first()

        assertEquals(LyricsSearchResult.Error::class, results::class)
        val errorResult = results as LyricsSearchResult.Error
        assertEquals("NetEase", errorResult.source)
        assertEquals("Network error", errorResult.message)
    }
}
