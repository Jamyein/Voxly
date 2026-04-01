package com.voxly.presentation.screens.filebrowser

import com.voxly.domain.model.AudioFile
import com.voxly.presentation.ui.loadAlbumArtThumbnail

/**
 * Loads album art from multiple sources:
 * 1. AlbumArtCacheManager thumbnail (file-level)
 * Returns null if no album art is found.
 */
suspend fun loadAlbumArt(
    context: android.content.Context,
    audioFile: AudioFile
): android.graphics.Bitmap? {
    return loadAlbumArtThumbnail(context, audioFile.path)
}
