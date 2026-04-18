package com.voxly.presentation.viewmodel

import com.voxly.data.remote.downloadImageBytes
import com.voxly.data.repository.AggregatedOnlineMetadataRepository
import com.voxly.domain.repository.OnlineRecording
import javax.inject.Inject

class CoverRepositorySearchStrategy @Inject constructor(
    private val aggregatedOnlineMetadataRepository: AggregatedOnlineMetadataRepository
) : OnlineCoverSearchStrategy {

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
