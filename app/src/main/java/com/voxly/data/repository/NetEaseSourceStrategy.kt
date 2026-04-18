package com.voxly.data.repository

import com.voxly.data.remote.netease.NetEaseMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import javax.inject.Inject

class NetEaseSourceStrategy @Inject constructor(
    private val netEaseMetadataRepository: NetEaseMetadataRepository
) : MetadataSourceStrategy {

    override val source: OnlineSource = OnlineSource.NETEASE

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        return netEaseMetadataRepository.searchNeteaseByArtistAlbum(artist, album, limit)
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        return netEaseMetadataRepository.searchByTrackBlocking(title, artist, limit)
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return netEaseMetadataRepository.getAlbumDetails(releaseId)
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return netEaseMetadataRepository.getCoverArtBytes(releaseId)
    }
}
