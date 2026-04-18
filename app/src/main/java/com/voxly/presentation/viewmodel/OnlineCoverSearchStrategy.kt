package com.voxly.presentation.viewmodel

import com.voxly.domain.repository.OnlineRecording

interface OnlineCoverSearchStrategy {
    suspend fun searchByTrack(
        title: String,
        artist: String?
    ): Result<List<OnlineRecording>>

    suspend fun getCoverArt(releaseId: String): Result<ByteArray?>
}
