package com.voxly.data.repository

import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.local.cache.RecentEditEntity
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.RecentEdit
import com.voxly.domain.repository.RecentEditsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-based implementation of RecentEditsRepository.
 * Uses Room database for better query performance and scalability.
 */
@Singleton
class RoomRecentEditsRepository @Inject constructor(
    private val databaseProvider: MusicCacheDatabaseProvider
) : RecentEditsRepository {

    companion object {
        private const val TAG = "RoomRecentEditsRepo"
        private const val MAX_ENTRIES = 1000
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun getRecentEdits(limit: Int): Flow<List<RecentEdit>> {
        Timber.tag("Voxly").i("RoomRecentEditsRepository: operation=getRecentEdits")
        val safeLimit = if (limit <= 0) MAX_ENTRIES else limit
        Timber.d("$TAG: Fetching recent edits, limit=$safeLimit")
        return databaseProvider.getDatabase().recentEditDao()
            .getRecentEdits(safeLimit)
            .map { entities ->
                Timber.d("$TAG: Retrieved ${entities.size} recent edits")
                entities.map { it.toDomain() }
            }
    }

    override suspend fun addRecentEdit(
        filePath: String,
        originalMetadata: AudioMetadata,
        newMetadata: AudioMetadata
    ) {
        Timber.tag("Voxly").i("RoomRecentEditsRepository: operation=addRecentEdit")
        Timber.d("$TAG: Recording edit for: $filePath")
        val database = databaseProvider.getDatabase()
        val entity = RecentEditEntity(
            filePath = filePath,
            fileName = filePath.substringAfterLast('/').substringAfterLast('\\'),
            timestamp = System.currentTimeMillis(),
            originalMetadataJson = encodeMetadata(originalMetadata),
            newMetadataJson = encodeMetadata(newMetadata)
        )
        database.recentEditDao().insert(entity)
        database.recentEditDao().deleteOldEntries(MAX_ENTRIES)
        Timber.i("$TAG: Recorded edit: $filePath")
    }

    override suspend fun clearRecentEdits() {
        Timber.i("$TAG: Clearing all recent edits")
        databaseProvider.getDatabase().recentEditDao().deleteAll()
        Timber.i("$TAG: Cleared all recent edits")
    }

    private fun RecentEditEntity.toDomain(): RecentEdit {
        return RecentEdit(
            filePath = filePath,
            fileName = fileName,
            timestamp = timestamp,
            originalMetadata = decodeMetadata(originalMetadataJson),
            newMetadata = decodeMetadata(newMetadataJson)
        )
    }

    private fun encodeMetadata(metadata: AudioMetadata): String {
        // Don't serialize albumArt (large binary data)
        val metadataWithoutArt = metadata.copy(albumArt = null)
        return json.encodeToString(SerializableAudioMetadata.fromAudioMetadata(metadataWithoutArt))
    }

    private fun decodeMetadata(jsonString: String): AudioMetadata {
        return try {
            val serializable = json.decodeFromString<SerializableAudioMetadata>(jsonString)
            serializable.toAudioMetadata()
        } catch (e: Exception) {
            AudioMetadata()
        }
    }
}

/**
 * Serializable version of AudioMetadata for JSON storage.
 * Excludes non-serializable fields like ByteArray.
 */
@kotlinx.serialization.Serializable
private data class SerializableAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val totalTracks: Int? = null,
    val discNumber: Int? = null,
    val totalDiscs: Int? = null,
    val composer: String? = null,
    val lyricist: String? = null,
    val conductor: String? = null,
    val originalArtist: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val customFields: Map<String, String> = emptyMap()
) {
    fun toAudioMetadata(): AudioMetadata {
        return AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            year = year,
            genre = genre,
            trackNumber = trackNumber,
            totalTracks = totalTracks,
            discNumber = discNumber,
            totalDiscs = totalDiscs,
            composer = composer,
            lyricist = lyricist,
            conductor = conductor,
            originalArtist = originalArtist,
            comment = comment,
            lyrics = lyrics,
            albumArt = null,
            customFields = customFields
        )
    }

    companion object {
        fun fromAudioMetadata(metadata: AudioMetadata): SerializableAudioMetadata {
            return SerializableAudioMetadata(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                albumArtist = metadata.albumArtist,
                year = metadata.year,
                genre = metadata.genre,
                trackNumber = metadata.trackNumber,
                totalTracks = metadata.totalTracks,
                discNumber = metadata.discNumber,
                totalDiscs = metadata.totalDiscs,
                composer = metadata.composer,
                lyricist = metadata.lyricist,
                conductor = metadata.conductor,
                originalArtist = metadata.originalArtist,
                comment = metadata.comment,
                lyrics = metadata.lyrics,
                customFields = metadata.customFields
            )
        }
    }
}
