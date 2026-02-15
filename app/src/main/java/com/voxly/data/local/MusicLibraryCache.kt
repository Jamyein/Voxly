package com.voxly.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Music library cache manager for optimized scanning.
 * Uses JSON file-based caching for persistence.
 */
@Singleton
class MusicLibraryCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MusicLibraryCache"
        private const val CACHE_FILE_NAME = "music_library_cache.json"
    }

    private val cacheFile: File by lazy {
        File(context.filesDir, CACHE_FILE_NAME)
    }

    private val gson: Gson = GsonBuilder().create()

    @Volatile
    private var cachedFiles: MutableMap<String, CachedAudioFile> = mutableMapOf()

    init {
        loadCacheFromDisk()
    }

    data class CachedAudioFile(
        val path: String,
        val name: String,
        val size: Long,
        val duration: Long,
        val format: String,
        val bitrate: Int,
        val sampleRate: Int,
        val channels: Int,
        val mediaStoreAlbumId: Long?,
        val metadata: CachedMetadata,
        val replayGainInfo: CachedReplayGainInfo?,
        val lastScannedAt: Long,
        val fileLastModifiedAt: Long
    )

    data class CachedMetadata(
        val title: String?, val artist: String?, val album: String?, val albumArtist: String?,
        val year: String?, val genre: String?, val trackNumber: Int?, val totalTracks: Int?,
        val discNumber: Int?, val totalDiscs: Int?, val composer: String?, val lyricist: String?,
        val conductor: String?, val originalArtist: String?, val comment: String?, val lyrics: String?,
        val customFields: Map<String, String>
    )

    data class CachedReplayGainInfo(
        val trackGain: Float, val trackPeak: Float, val albumGain: Float?, val albumPeak: Float?
    )

    fun getCachedAudioFiles(): Flow<List<AudioFile>> = flow {
        val audioFiles = cachedFiles.values.mapNotNull { cached ->
            try { cached.toAudioFile() } catch (e: Exception) { null }
        }
        emit(audioFiles.sortedBy { it.metadata.getDisplayTitle(it.name) })
    }.flowOn(Dispatchers.IO)

    fun getCachedAudioFilesByDirectory(directoryPath: String): Flow<List<AudioFile>> = flow {
        val audioFiles = cachedFiles.values.filter { it.path.startsWith(directoryPath) }
            .mapNotNull { cached -> try { cached.toAudioFile() } catch (e: Exception) { null } }
        emit(audioFiles.sortedBy { it.path })
    }.flowOn(Dispatchers.IO)

    suspend fun getCachedFileCount(): Int = withContext(Dispatchers.IO) { cachedFiles.size }
    suspend fun getLastScanTime(): Long? = withContext(Dispatchers.IO) {
        cachedFiles.values.maxOfOrNull { it.lastScannedAt }
    }
    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) { cachedFiles.isNotEmpty() }

    suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        cachedFiles.clear()
        saveCacheToDisk()
    }

    suspend fun removeFromCache(filePath: String): Unit = withContext(Dispatchers.IO) {
        cachedFiles.remove(filePath)
        saveCacheToDisk()
    }

    suspend fun updateCache(audioFiles: List<AudioFile>): Unit = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        audioFiles.forEach { audioFile ->
            val file = File(audioFile.path)
            cachedFiles[audioFile.path] = CachedAudioFile(
                path = audioFile.path, name = audioFile.name, size = audioFile.size,
                duration = audioFile.duration, format = audioFile.format, bitrate = audioFile.bitrate,
                sampleRate = audioFile.sampleRate, channels = audioFile.channels,
                mediaStoreAlbumId = audioFile.mediaStoreAlbumId,
                metadata = CachedMetadata(
                    audioFile.metadata.title, audioFile.metadata.artist, audioFile.metadata.album,
                    audioFile.metadata.albumArtist, audioFile.metadata.year, audioFile.metadata.genre,
                    audioFile.metadata.trackNumber, audioFile.metadata.totalTracks,
                    audioFile.metadata.discNumber, audioFile.metadata.totalDiscs,
                    audioFile.metadata.composer, audioFile.metadata.lyricist,
                    audioFile.metadata.conductor, audioFile.metadata.originalArtist,
                    audioFile.metadata.comment, audioFile.metadata.lyrics,
                    audioFile.metadata.customFields
                ),
                replayGainInfo = audioFile.replayGainInfo?.let {
                    CachedReplayGainInfo(it.trackGain, it.trackPeak, it.albumGain, it.albumPeak)
                },
                lastScannedAt = currentTime, fileLastModifiedAt = file.lastModified()
            )
        }
        saveCacheToDisk()
    }

    suspend fun getFilesNeedingRescan(currentFiles: List<Pair<String, Long>>): List<String> = withContext(Dispatchers.IO) {
        val currentPaths = currentFiles.map { it.first }
        val deletedPaths = cachedFiles.keys.filter { it !in currentPaths }
        deletedPaths.forEach { cachedFiles.remove(it) }
        currentFiles.filter { (path, lastModified) ->
            val cached = cachedFiles[path]
            cached == null || cached.fileLastModifiedAt != lastModified
        }.map { it.first }
    }

    suspend fun needsRescan(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) { cachedFiles.remove(filePath); return@withContext true }
        val lastModified = file.lastModified()
        val cached = cachedFiles[filePath]
        cached == null || cached.fileLastModifiedAt != lastModified
    }

    suspend fun getCachedFile(filePath: String): AudioFile? = withContext(Dispatchers.IO) {
        cachedFiles[filePath]?.toAudioFile()
    }

    suspend fun syncFileToCache(audioFile: AudioFile): Unit = withContext(Dispatchers.IO) {
        val file = File(audioFile.path)
        val currentTime = System.currentTimeMillis()
        cachedFiles[audioFile.path] = CachedAudioFile(
            path = audioFile.path, name = audioFile.name, size = audioFile.size,
            duration = audioFile.duration, format = audioFile.format, bitrate = audioFile.bitrate,
            sampleRate = audioFile.sampleRate, channels = audioFile.channels,
            mediaStoreAlbumId = audioFile.mediaStoreAlbumId,
            metadata = CachedMetadata(
                audioFile.metadata.title, audioFile.metadata.artist, audioFile.metadata.album,
                audioFile.metadata.albumArtist, audioFile.metadata.year, audioFile.metadata.genre,
                audioFile.metadata.trackNumber, audioFile.metadata.totalTracks,
                audioFile.metadata.discNumber, audioFile.metadata.totalDiscs,
                audioFile.metadata.composer, audioFile.metadata.lyricist,
                audioFile.metadata.conductor, audioFile.metadata.originalArtist,
                audioFile.metadata.comment, audioFile.metadata.lyrics,
                audioFile.metadata.customFields
            ),
            replayGainInfo = audioFile.replayGainInfo?.let {
                CachedReplayGainInfo(it.trackGain, it.trackPeak, it.albumGain, it.albumPeak)
            },
            lastScannedAt = currentTime, fileLastModifiedAt = file.lastModified()
        )
        saveCacheToDisk()
    }

    suspend fun cleanupDeletedFiles(currentPaths: List<String>): Int = withContext(Dispatchers.IO) {
        val deletedPaths = cachedFiles.keys.filter { it !in currentPaths }
        deletedPaths.forEach { cachedFiles.remove(it) }
        if (deletedPaths.isNotEmpty()) saveCacheToDisk()
        deletedPaths.size
    }

    private fun loadCacheFromDisk() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val type = object : TypeToken<MutableMap<String, CachedAudioFile>>() {}.type
                val loaded: MutableMap<String, CachedAudioFile> = gson.fromJson(json, type)
                cachedFiles = loaded
            }
        } catch (e: Exception) {
            cachedFiles = mutableMapOf()
        }
    }

    private fun saveCacheToDisk() {
        try {
            val json = gson.toJson(cachedFiles)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }

    private fun CachedAudioFile.toAudioFile(): AudioFile {
        return AudioFile(
            id = path.hashCode().toString(), path = path, name = name, size = size,
            duration = duration, format = format, bitrate = bitrate,
            sampleRate = sampleRate, channels = channels, mediaStoreAlbumId = mediaStoreAlbumId,
            metadata = AudioMetadata(
                metadata.title, metadata.artist, metadata.album, metadata.albumArtist,
                metadata.year, metadata.genre, metadata.trackNumber, metadata.totalTracks,
                metadata.discNumber, metadata.totalDiscs, metadata.composer, metadata.lyricist,
                metadata.conductor, metadata.originalArtist, metadata.comment, metadata.lyrics,
                null, metadata.customFields
            ),
            replayGainInfo = replayGainInfo?.let {
                ReplayGainInfo(it.trackGain, it.trackPeak, it.albumGain, it.albumPeak)
            }
        )
    }
}
