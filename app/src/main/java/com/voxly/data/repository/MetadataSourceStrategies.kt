package com.voxly.data.repository

import com.voxly.data.remote.itunes.ITunesRepository
import com.voxly.data.remote.musicbrainz.MusicBrainzRepository
import com.voxly.data.remote.netease.NetEaseMetadataRepository
import com.voxly.data.remote.qqmusic.QQMusicMetadataRepository
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
