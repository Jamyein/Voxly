package com.voxly.data.local.cover

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CoverUriProvider"

/**
 * Provides album art URIs for list and playback screens.
 * 
 * Loading priority:
 * 1. MediaStore album art URI (fast, cached by system)
 * 2. Folder cover files (cover.jpg, folder.jpg, etc.)
 * 
 * Note: Metadata editor uses original cover art directly from audio files,
 * not through this provider.
 */
@Singleton
class CoverUriProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver
    
    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")
    
    /**
     * Get cover URI for display in lists and playback screens.
     * 
     * @param albumId MediaStore album ID
     * @param filePath Audio file path (for folder cover lookup)
     * @return Cover URI or null
     */
    fun getCoverUri(
        albumId: Long?,
        filePath: String? = null
    ): Uri? {
        // 1. Try MediaStore first (fastest)
        if (albumId != null && albumId > 0) {
            val mediaStoreUri = ContentUris.withAppendedId(albumArtUri, albumId)
            if (uriExists(mediaStoreUri)) {
                return mediaStoreUri
            }
        }
        
        // 2. Try folder cover lookup
        if (!filePath.isNullOrBlank()) {
            findFolderCover(filePath)?.let { return it }
        }
        
        return null
    }
    
    /**
     * Check if URI exists without loading the image.
     */
    private fun uriExists(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Find cover file in the same folder as the audio file.
     */
    private fun findFolderCover(filePath: String): Uri? {
        val folder = File(filePath).parentFile ?: return null
        if (!folder.exists() || !folder.isDirectory) return null
        
        val coverNames = listOf(
            "cover.jpg", "folder.jpg", "cover.png", "folder.png",
            "album.jpg", "album.png", "cover.webp", "folder.webp"
        )
        
        for (name in coverNames) {
            val file = File(folder, name)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }
        
        return null
    }
    
    /**
     * Get MediaStore album art URI directly.
     */
    fun getMediaStoreUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        val uri = ContentUris.withAppendedId(albumArtUri, albumId)
        return if (uriExists(uri)) uri else null
    }
}
