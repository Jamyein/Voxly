package com.voxly.data.repository

import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource

interface MetadataSourceStrategy {
    val source: OnlineSource

    suspend fun searchByArtistAlbum(artist: String, album: String, limit: Int): Result<List<OnlineRelease>>
    suspend fun searchByTrack(title: String, artist: String?, limit: Int): Result<List<OnlineRecording>>
    suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails>
    suspend fun getCoverArt(releaseId: String): Result<ByteArray?>
}
