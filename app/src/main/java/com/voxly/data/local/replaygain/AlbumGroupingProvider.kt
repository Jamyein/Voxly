package com.voxly.data.local.replaygain

import com.voxly.data.local.cache.CachedAudioFileDao
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.data.local.scanner.AlbumArtistAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumGroupingProvider @Inject constructor(
    private val cachedAudioFileDao: CachedAudioFileDao,
    private val albumArtistAggregator: AlbumArtistAggregator,
    private val metadataProcessor: TagLibMetadataProcessor
) {
    /**
     * Groups [filePaths] into albums using the cheapest available source.
     *   1. Room cache (batch SELECT … IN, chunked to avoid SQLite variable limit)
     *   2. In-memory AlbumArtistAggregator
     *   3. Disk metadata read for stragglers
     */
    suspend fun groupByAlbum(filePaths: List<String>): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (filePaths.isEmpty()) return@withContext emptyMap()

        val remaining = filePaths.toMutableList()
        val filesByAlbum = mutableMapOf<String, MutableList<String>>()
        var singletonIndex = 0

        // 1. Room cache (primary)
        val chunkSize = 500
        val cachedInfo = remaining.chunked(chunkSize).flatMap { chunk ->
            cachedAudioFileDao.getAlbumInfoByPaths(chunk)
        }
        val cachedPaths = mutableSetOf<String>()
        cachedInfo.forEach { info ->
            cachedPaths.add(info.path)
            val album = info.album?.trim() ?: ""
            val artist = info.artist?.trim() ?: ""
            addToGroup(filesByAlbum, info.path, album, artist) { "singleton_${singletonIndex++}" }
        }
        remaining.removeAll(cachedPaths)

        // 2. In-memory aggregator fallback
        if (remaining.isNotEmpty()) {
            val albums = albumArtistAggregator.albums.value
            val remainingSet = remaining.toHashSet()
            albums.forEach { albumGroup ->
                val albumName = albumGroup.name.trim()
                val albumArtist = albumGroup.albumArtist?.trim() ?: ""
                albumGroup.files.forEach { file ->
                    if (remainingSet.remove(file.path)) {
                        addToGroup(filesByAlbum, file.path, albumName, albumArtist) { "singleton_${singletonIndex++}" }
                    }
                }
            }
        }

        // 3. Disk read fallback (stragglers / cache misses)
        remaining.forEach { path ->
            try {
                val metadata = metadataProcessor.readMetadata(path, includeAlbumArt = false)
                val album = metadata?.album?.trim() ?: ""
                val artist = metadata?.artist?.trim() ?: ""
                addToGroup(filesByAlbum, path, album, artist) { "singleton_${singletonIndex++}" }
            } catch (e: Exception) {
                Timber.w("AlbumGroupingProvider: failed to read metadata for $path")
                filesByAlbum.getOrPut("singleton_${singletonIndex++}") { mutableListOf() }.add(path)
            }
        }

        Timber.i("AlbumGroupingProvider: grouped ${filePaths.size} files into ${filesByAlbum.size} albums")
        filesByAlbum
    }

    private inline fun addToGroup(
        filesByAlbum: MutableMap<String, MutableList<String>>,
        path: String,
        album: String,
        artist: String,
        singletonKey: () -> String
    ) {
        if (album.isEmpty() && artist.isEmpty()) {
            filesByAlbum.getOrPut(singletonKey()) { mutableListOf() }.add(path)
        } else {
            val key = "${album}_$artist"
            filesByAlbum.getOrPut(key) { mutableListOf() }.add(path)
        }
    }
}
