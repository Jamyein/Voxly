package com.voxly.presentation.viewmodel

import com.voxly.domain.repository.LyricsRepository
import com.voxly.domain.repository.LyricsSourceResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
