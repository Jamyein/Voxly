package com.voxly.data.local.cover

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverUriProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")

    companion object {
        private const val MAX_URI_EXISTS_CACHE = 200
        private const val MAX_FOLDER_COVER_CACHE = 200

        private val uriExistsCache = LinkedHashMap<Uri, Boolean>(MAX_URI_EXISTS_CACHE, 0.75f, true)
        private val folderCoverCache = LinkedHashMap<String, Uri?>(MAX_FOLDER_COVER_CACHE, 0.75f, true)

        fun clearCaches() {
            synchronized(uriExistsCache) {
                uriExistsCache.clear()
            }
            synchronized(folderCoverCache) {
                folderCoverCache.clear()
            }
        }
    }

    suspend fun getCoverUri(
        albumId: Long?,
        filePath: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        if (albumId != null && albumId > 0) {
            val uri = ContentUris.withAppendedId(albumArtUri, albumId)
            if (uriExistsCached(uri)) {
                return@withContext uri
            }
        }

        if (!filePath.isNullOrBlank()) {
            findFolderCoverCached(filePath)?.let { return@withContext it }
        }

        return@withContext null
    }

    private fun uriExistsCached(uri: Uri): Boolean = synchronized(uriExistsCache) {
        val exists = uriExistsCache.getOrPut(uri) {
            runCatching { contentResolver.openInputStream(uri)?.use { true } }.getOrNull() ?: false
        }
        trimUriExistsCache()
        exists
    }

    private fun findFolderCoverCached(filePath: String): Uri? {
        return synchronized(folderCoverCache) {
            val cover = folderCoverCache.getOrPut(filePath) {
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
            trimFolderCoverCache()
            cover
        }
    }

    private fun trimUriExistsCache() {
        while (uriExistsCache.size > MAX_URI_EXISTS_CACHE) {
            uriExistsCache.keys.firstOrNull()?.let { uriExistsCache.remove(it) } ?: break
        }
    }

    private fun trimFolderCoverCache() {
        while (folderCoverCache.size > MAX_FOLDER_COVER_CACHE) {
            folderCoverCache.keys.firstOrNull()?.let { folderCoverCache.remove(it) } ?: break
        }
    }

    fun getMediaStoreUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        val uri = ContentUris.withAppendedId(albumArtUri, albumId)
        return if (uriExistsCached(uri)) uri else null
    }
}
