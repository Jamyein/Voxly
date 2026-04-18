package com.voxly.data.repository

import com.voxly.data.remote.itunes.ITunesRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import javax.inject.Inject

class ITunesSourceStrategy @Inject constructor(
    private val iTunesRepository: ITunesRepository
) : MetadataSourceStrategy {

    override val source: OnlineSource = OnlineSource.ITUNES

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        return iTunesRepository.searchByArtistAlbum(artist, album)
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        return iTunesRepository.searchByTrack(title, artist)
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return iTunesRepository.getReleaseDetails(releaseId)
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return iTunesRepository.getCoverArt(releaseId)
    }
}
