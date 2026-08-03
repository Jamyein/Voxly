package com.voxly.data.local.scanner

import com.voxly.domain.model.AudioFile

/**
 * Collects raw audio files in the current scan scope. Filtering
 * (whitelist/blacklist/min-duration) is NOT a scan concern anymore — it happens
 * once, at read stage, in [AlbumArtistAggregator.filteredAllAudios].
 */
interface ScanStrategy {
    suspend fun scan(): List<AudioFile>
}
