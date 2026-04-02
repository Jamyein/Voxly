package com.voxly.presentation.screens.filebrowser

import com.voxly.domain.model.AudioFile
import com.voxly.presentation.ui.loadAlbumArtThumbnail
import com.voxly.presentation.ui.loadMediaStoreAlbumArt
import java.io.File

/**
 * Loads album art from multiple sources:
 * 1. MediaStore album art (fastest, system cache)
 * 2. AlbumArtCacheManager thumbnail (file-level embedded art)
 * 3. Folder cover art (cover.jpg, folder.jpg, etc.)
 * Returns null if no album art is found.
 */
suspend fun loadAlbumArt(
    context: android.content.Context,
    audioFile: AudioFile
): android.graphics.Bitmap? {
    // 1. First try: MediaStore (fastest, system-level cache)
    if (audioFile.mediaStoreAlbumId != null && audioFile.mediaStoreAlbumId > 0) {
        loadMediaStoreAlbumArt(context, audioFile.mediaStoreAlbumId)?.let { return it }
    }

    // 2. Second try: AlbumArtCacheManager (file-level embedded art)
    loadAlbumArtThumbnail(context, audioFile.path)?.let { return it }

    // 3. Third try: folder cover art (cover.jpg, folder.jpg, etc.)
    loadFolderCoverArt(audioFile.path)?.let { return it }

    return null
}

/**
 * Loads folder cover art from the parent directory of the audio file.
 */
private fun loadFolderCoverArt(filePath: String): android.graphics.Bitmap? {
    val folder = File(filePath).parentFile ?: return null
    val coverFileNames = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "album.jpg", "album.png")

    for (fileName in coverFileNames) {
        val coverFile = File(folder, fileName)
        if (coverFile.exists()) {
            return try {
                decodeBitmapFromFile(coverFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        }
    }
    return null
}

/**
 * Decodes a bitmap from file without downsampling.
 */
private fun decodeBitmapFromFile(filePath: String): android.graphics.Bitmap? {
    return android.graphics.BitmapFactory.decodeFile(filePath)
}
