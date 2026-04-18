package com.voxly.presentation.viewmodel

import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverRepositorySearchStrategyTest {

    private val mockRepository = mockk<AggregatedOnlineMetadataRepository>()
    private val strategy = CoverRepositorySearchStrategy(mockRepository)

    @Test
    fun `searchByTrack returns recordings on success`() = runBlocking {
        val mockRecordings = listOf(
            OnlineRecording(
                id = "release-1",
                title = "Test Album",
                artist = "Test Artist",
                duration = 180,
                releaseId = "release-1",
                source = OnlineSource.MUSICBRAINZ
            )
        )

        coEvery {
            mockRepository.searchByTrackForCover("Test Song", "Test Artist")
        } returns Result.success(mockRecordings)

        val result = strategy.searchByTrack("Test Song", "Test Artist")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("release-1", result.getOrNull()?.first()?.releaseId)
    }

    @Test
    fun `searchByTrack returns failure on error`() = runBlocking {
        coEvery {
            mockRepository.searchByTrackForCover("Test Song", "Test Artist")
        } returns Result.failure(Exception("Search failed"))

        val result = strategy.searchByTrack("Test Song", "Test Artist")

        assertTrue(result.isFailure)
        assertEquals("Search failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getCoverArt returns bytes on success`() = runBlocking {
        val coverBytes = byteArrayOf(1, 2, 3, 4)

        coEvery {
            mockRepository.getCoverArt("release-123")
        } returns Result.success(coverBytes)

        val result = strategy.getCoverArt("release-123")

        assertTrue(result.isSuccess)
        assertEquals(coverBytes.contentToString(), result.getOrNull()?.contentToString())
    }

    @Test
    fun `getCoverArt returns null when no cover found`() = runBlocking {
        coEvery {
            mockRepository.getCoverArt("release-123")
        } returns Result.success(null)

        val result = strategy.getCoverArt("release-123")

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }
}
