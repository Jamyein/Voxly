package com.voxly.domain.model

import androidx.compose.runtime.Immutable

sealed class CacheChange {
    abstract val timestamp: Long

    data class FileUpdated(
        val filePath: String,
        val albumKey: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FileDeleted(
        val filePath: String,
        val albumKey: String?,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FilesBatchUpdated(
        val filePaths: List<String>,
        val albumKeys: Set<String>,
        override val timestamp: Long = System.currentTimeMillis()
    ) : CacheChange()

    data class FullRefresh(
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

    /**
     * Artist group keys for a file — mirrors the full-build split in
     * AlbumArtistAggregator so incremental single-file updates produce exactly
     * the same groups as a rebuild. Keys are always name-based: split by
     * [separators] when provided, otherwise the raw artist name. The MediaStore
     * artist id is never a group key (it only refines the display name).
     */
    fun extractArtistKeysWithSeparators(file: AudioFile, separators: Set<String>): List<String> {
        val artistName = file.metadata.artist?.takeIf { it.isNotBlank() } ?: return emptyList()

        if (separators.isEmpty()) {
            return listOf(artistName)
        }

        val regex = separators.sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

        return artistName.split(Regex(regex))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}