package com.voxly.data.repository

import com.voxly.domain.model.AlbumGroup
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache for album data to avoid JSON serialization overhead when navigating to AlbumDetailScreen.
 */
@Singleton
class AlbumCacheRepository @Inject constructor() {

    private val albumCache = ConcurrentHashMap<String, AlbumGroup>()

    /**
     * Cache an album group using a key composed of album name and artist.
     */
    fun cacheAlbum(album: AlbumGroup) {
        val key = createKey(album.name, album.albumArtist)
        albumCache[key] = album
    }

    /**
     * Retrieve an album from cache by name and artist.
     * Returns null if not found.
     */
    fun getAlbum(albumName: String, albumArtist: String?): AlbumGroup? {
        val key = createKey(albumName, albumArtist)
        return albumCache[key]
    }

    /**
     * Remove an album from cache.
     */
    fun removeAlbum(albumName: String, albumArtist: String?) {
        val key = createKey(albumName, albumArtist)
        albumCache.remove(key)
    }

    /**
     * Clear all cached albums.
     */
    fun clearCache() {
        albumCache.clear()
    }

    private fun createKey(albumName: String, albumArtist: String?): String {
        return "${albumName}_${albumArtist ?: ""}"
    }
}
