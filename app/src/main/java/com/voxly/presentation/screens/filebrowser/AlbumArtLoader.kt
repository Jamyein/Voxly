package com.voxly.presentation.screens.filebrowser

import android.net.Uri
import android.util.LruCache
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.ui.decodeBitmapFromBytes
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.ui.loadMediaStoreAlbumArt

/**
 * LRU cache for embedded album art to avoid repeated file reads.
 * Max 50 entries to balance memory usage with performance.
 */
private val embeddedArtCache = LruCache<String, android.graphics.Bitmap>(50)

/**
 * Loads album art from multiple sources:
 * 1. LRU cache for embedded art
 * 2. Embedded album art from the audio file
 * 3. MediaStore album art
 * Returns null if no album art is found.
 */
fun loadAlbumArt(
    context: android.content.Context,
    audioFile: AudioFile
): android.graphics.Bitmap? {
    // 1. First check global cache (embedded + folder cover)
    loadLocalAlbumArt(audioFile.path)?.let { return it }

    // 2. Try embedded album art from the file (with LRU cache)
    val cached = embeddedArtCache.get(audioFile.path)
    if (cached != null) return cached

    val embeddedArt = loadEmbeddedAlbumArt(context, audioFile.path)
    if (embeddedArt != null) {
        embeddedArtCache.put(audioFile.path, embeddedArt)
        return embeddedArt
    }

    // 3. Try MediaStore album art (with global caching)
    if (audioFile.mediaStoreAlbumId != null && audioFile.mediaStoreAlbumId > 0L) {
        val mediaStoreArt = loadMediaStoreAlbumArt(context, audioFile.mediaStoreAlbumId)
        if (mediaStoreArt != null) {
            return mediaStoreArt
        }
    }

    return null
}

/**
 * Loads embedded album art directly from the audio file using MediaMetadataRetriever.
 */
private fun loadEmbeddedAlbumArt(context: android.content.Context, filePath: String): android.graphics.Bitmap? {
    return runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                decodeThumbnailBitmap(artBytes)
            } else {
                null
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}

private fun loadMediaStoreAlbumBitmap(
    context: android.content.Context,
    albumId: Long?
): android.graphics.Bitmap? {
    if (albumId == null || albumId <= 0L) return null
    val uri = Uri.withAppendedPath(
        Uri.parse("content://media/external/audio/albumart"),
        albumId.toString()
    )
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            decodeThumbnailBitmap(bytes)
        }
    }.getOrNull()
}

private fun decodeThumbnailBitmap(
    bytes: ByteArray,
    targetSizePx: Int = 96
): android.graphics.Bitmap? {
    return decodeBitmapFromBytes(bytes, targetSizePx)
}
