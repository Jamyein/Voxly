package com.voxly.data.repository

import com.voxly.data.remote.qqmusic.QQMusicMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import javax.inject.Inject

class QQMusicSourceStrategy @Inject constructor(
    private val qqMusicMetadataRepository: QQMusicMetadataRepository
) : MetadataSourceStrategy {

    override val source: OnlineSource = OnlineSource.QQ_MUSIC

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        return qqMusicMetadataRepository.searchQQMusicByArtistAlbum(artist, album, limit)
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        return qqMusicMetadataRepository.searchByTrackBlocking(title, artist, limit)
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return qqMusicMetadataRepository.getAlbumDetails(releaseId)
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return qqMusicMetadataRepository.getCoverArtBytes(releaseId)
    }
}
