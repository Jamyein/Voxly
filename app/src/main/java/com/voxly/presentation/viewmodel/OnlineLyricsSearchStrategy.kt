package com.voxly.presentation.viewmodel

import com.voxly.domain.repository.LyricsSourceResult
import com.voxly.domain.repository.OnlineLyricsResult
import kotlinx.coroutines.flow.Flow

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
