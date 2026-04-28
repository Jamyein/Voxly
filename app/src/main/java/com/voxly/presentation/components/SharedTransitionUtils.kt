package com.voxly.presentation.components

/**
 * Helper function to create unique keys for audio file shared elements.
 *
 * @param filePath The audio file path
 * @return Unique key string
 */
fun createAudioFileSharedElementKey(filePath: String): String = "audio-file-$filePath"

/**
 * Helper function to create unique keys for album shared elements.
 *
 * @param albumName The album name
 * @param albumArtist The album artist (optional, for disambiguation)
 * @return Unique key string
 */
fun createAlbumSharedElementKey(albumName: String, albumArtist: String? = null): String {
    return if (albumArtist != null) {
        "album-$albumName-$albumArtist"
    } else {
        "album-$albumName"
    }
}

/**
 * Helper function to create unique keys for artist shared elements.
 *
 * @param artistName The artist name
 * @return Unique key string
 */
fun createArtistSharedElementKey(artistName: String): String = "artist-$artistName"

/**
 * Helper function to create unique keys for album art shared elements.
 * Used for Container Transform transitions between list items and detail pages.
 *
 * @param filePath The audio file path
 * @return Unique key string for the album art shared element
 */
fun createAlbumArtSharedElementKey(filePath: String): String = "album-art-$filePath"

/**
 * Helper function to create unique keys for album cover shared elements.
 * Used for Container Transform transitions from AlbumScreen to AlbumDetailScreen.
 *
 * @param albumName The album name
 * @param albumArtist The album artist (optional, for disambiguation)
 * @return Unique key string for the album cover shared element
 */
fun createAlbumCoverSharedElementKey(albumName: String, albumArtist: String?): String {
    val normalizedArtist = albumArtist?.takeIf { it.isNotBlank() }
    return "album-cover-$albumName-${normalizedArtist ?: "unknown"}"
}

/**
 * Helper function to create unique keys for artist avatar shared elements.
 * Used for Container Transform transitions from ArtistScreen to ArtistDetailScreen.
 *
 * @param artistName The artist name
 * @return Unique key string for the artist avatar shared element
 */
fun createArtistAvatarSharedElementKey(artistName: String): String = "artist-avatar-$artistName"


