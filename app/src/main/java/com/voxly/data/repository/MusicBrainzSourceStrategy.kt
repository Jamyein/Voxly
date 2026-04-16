package com.voxly.data.repository

import com.voxly.data.remote.musicbrainz.MusicBrainzRepository
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineReleaseDetails
import com.voxly.domain.repository.OnlineSource
import javax.inject.Inject

class MusicBrainzSourceStrategy @Inject constructor(
    private val musicBrainzRepository: MusicBrainzRepository
) : MetadataSourceStrategy {

    override val source: OnlineSource = OnlineSource.MUSICBRAINZ

    override suspend fun searchByArtistAlbum(
        artist: String,
        album: String,
        limit: Int
    ): Result<List<OnlineRelease>> {
        return musicBrainzRepository.searchByArtistAlbum(artist, album)
    }

    override suspend fun searchByTrack(
        title: String,
        artist: String?,
        limit: Int
    ): Result<List<OnlineRecording>> {
        return musicBrainzRepository.searchByTrack(title, artist)
    }

    override suspend fun getReleaseDetails(releaseId: String): Result<OnlineReleaseDetails> {
        return musicBrainzRepository.getReleaseDetails(releaseId)
    }

    override suspend fun getCoverArt(releaseId: String): Result<ByteArray?> {
        return musicBrainzRepository.getCoverArt(releaseId)
    }
}
