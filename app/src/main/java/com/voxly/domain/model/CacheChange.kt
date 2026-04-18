package com.voxly.domain.model

import androidx.compose.runtime.Immutable

sealed class CacheChange {
    abstract val timestamp: Long

    data class FileUpdated(
        val filePath: String,
        val albumKey: String?,
        val artistKey: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FileDeleted(
        val filePath: String,
        val albumKey: String?,
        val artistKey: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FilesBatchUpdated(
        val filePaths: List<String>,
        val albumKeys: Set<String>,
        val artistKeys: Set<String>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FullRefresh(
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class AlbumMetadataChanged(
        val albumKey: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class ArtistMetadataChanged(
        val artistKey: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()
}

@Immutable
object CacheChangeKeys {
    fun extractAlbumKey(file: AudioFile): String? {
        val effectiveAlbumId = file.mediaStoreAlbumId?.takeIf { it > 0 }
        val effectiveAlbumName = file.metadata.album?.takeIf { it.isNotBlank() }
        val effectiveAlbumArtist = file.metadata.albumArtist?.takeIf { it.isNotBlank() }
            ?: file.metadata.artist?.takeIf { it.isNotBlank() }

        return when {
            effectiveAlbumId != null && effectiveAlbumName != null -> "id:$effectiveAlbumId"
            effectiveAlbumName != null -> "str:$effectiveAlbumName|${effectiveAlbumArtist.orEmpty()}"
            else -> null
        }
    }

    fun extractArtistKey(file: AudioFile): String? {
        val effectiveArtistId = file.mediaStoreArtistId?.takeIf { it > 0 }
        val effectiveArtistName = file.metadata.artist?.takeIf { it.isNotBlank() }

        return when {
            effectiveArtistId != null && effectiveArtistName != null -> "id:$effectiveArtistId"
            effectiveArtistName != null -> effectiveArtistName
            else -> null
        }
    }

    fun extractArtistKeysWithSeparators(file: AudioFile, separators: Set<String>): List<String> {
        val artistKey = extractArtistKey(file) ?: return emptyList()
        if (artistKey.startsWith("id:")) {
            return listOf(artistKey)
        }

        if (separators.isEmpty()) {
            return listOf(artistKey)
        }

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        return artistKey.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}