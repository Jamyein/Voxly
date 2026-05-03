package com.voxly.presentation.viewmodel

import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.LyricsSourceResult
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineLyricsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface OnlineCoverSearchStrategy {
    suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>>

    suspend fun getCoverArt(releaseId: String): Result<ByteArray?>
}

/**
 * Interface for cover search strategy.
 * This is the type bound in Hilt for CoverSearchHelper.
 */
interface CoverSearchStrategy {
    suspend fun searchByTrack(title: String, artist: String?): Result<List<OnlineRecording>>
}

interface OnlineLyricsSearchStrategy {
    suspend fun search(
        track: String,
        artist: String?,
        album: String?
    ): Flow<LyricsSearchResult>

    fun getSourceName(): String
}

sealed class LyricsSearchResult {
    data class Result(val lyrics: OnlineLyricsResult) : LyricsSearchResult()
    data class SourceCompleted(val source: String) : LyricsSearchResult()
    data class Error(val source: String, val message: String) : LyricsSearchResult()
}

class CoverRepositorySearchStrategy @Inject constructor(
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository
) : OnlineCoverSearchStrategy, CoverSearchStrategy {

    override suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>> {
        return aggregatedOnlineMetadataRepository.searchByTrackForCover(title, artist)
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return aggregatedOnlineMetadataRepository.getCoverArt(releaseId)
    }
}

class LyricsRepositorySearchStrategy @Inject constructor(
    private val lyricsRepository: LyricsRepository
) : OnlineLyricsSearchStrategy {

    override suspend fun search(
        track: String,
        artist: String?,
        album: String?
    ): Flow<LyricsSearchResult> {
        return lyricsRepository.searchOnlineLyricsFlow(track, artist, album).map { result ->
            when (result) {
                is LyricsSourceResult.Result -> LyricsSearchResult.Result(result.lyrics)
                is LyricsSourceResult.SourceCompleted -> LyricsSearchResult.SourceCompleted(result.source)
                is LyricsSourceResult.Error -> LyricsSearchResult.Error(result.source, result.message)
            }
        }
    }

    override fun getSourceName(): String = "LyricsRepository"
}
