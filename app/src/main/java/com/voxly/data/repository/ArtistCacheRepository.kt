package com.voxly.data.repository

import com.voxly.domain.model.ArtistGroup
import com.voxly.domain.model.AudioFile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for artist data to avoid repeated database queries when navigating to ArtistDetailScreen.
 */
@Singleton
class ArtistCacheRepository @Inject constructor() {

    private val artistCache = ConcurrentHashMap<String, ArtistGroup>()

    /**
     * Cache an artist group.
     */
    fun cacheArtist(artist: ArtistGroup) {
        artistCache[artist.name] = artist
    }

    /**
     * Retrieve an artist from cache by name.
     * Returns null if not found.
     */
    fun getArtist(artistName: String): ArtistGroup? {
        return artistCache[artistName]
    }

    /**
     * Remove an artist from cache.
     */
    fun removeArtist(artistName: String) {
        artistCache.remove(artistName)
    }

    /**
     * Clear all cached artists.
     */
    fun clearCache() {
        artistCache.clear()
    }
}
