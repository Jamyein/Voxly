package com.voxly.data.local.cover

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverUriProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver
    
    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")
    
    private val uriExistsCache = mutableMapOf<Uri, Boolean>()
    private val folderCoverCache = mutableMapOf<String, Uri?>()

    fun getCoverUri(
        albumId: Long?,
        filePath: String? = null
    ): Uri? {
        if (albumId != null && albumId > 0) {
            val mediaStoreUri = ContentUris.withAppendedId(albumArtUri, albumId)
            if (uriExistsCached(mediaStoreUri)) {
                return mediaStoreUri
            }
        }
        
        if (!filePath.isNullOrBlank()) {
            findFolderCoverCached(filePath)?.let { return it }
        }
        
        return null
    }

    private fun uriExistsCached(uri: Uri): Boolean {
        return uriExistsCache.getOrPut(uri) {
            try {
                contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun findFolderCoverCached(filePath: String): Uri? {
        return folderCoverCache.getOrPut(filePath) {
            val folder = File(filePath).parentFile ?: return@getOrPut null
            if (!folder.exists() || !folder.isDirectory) return@getOrPut null
            
            val coverNames = listOf(
                "cover.jpg", "folder.jpg", "cover.png", "folder.png",
                "album.jpg", "album.png", "cover.webp", "folder.webp"
            )
            
            for (name in coverNames) {
                val file = File(folder, name)
                if (file.exists()) {
                    return@getOrPut Uri.fromFile(file)
                }
            }
            
            return@getOrPut null
        }
    }
    
    fun getMediaStoreUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        val uri = ContentUris.withAppendedId(albumArtUri, albumId)
        return if (uriExistsCached(uri)) uri else null
    }
}
