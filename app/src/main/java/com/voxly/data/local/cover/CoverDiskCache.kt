package com.voxly.data.local.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverDiskCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CoverDiskCache"
        private const val CACHE_DIR_NAME = "album_covers"
        private const val COVER_SIZE = 512
        private const val WEBP_QUALITY = 85
        private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100MB default max
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private val mutex = Mutex()
    private val bitmapOptions by lazy {
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    fun generateCacheKey(albumArtist: String?, albumName: String): String {
        val input = "${albumArtist ?: "_unknown_"}_$albumName"
        return md5(input)
    }

    suspend fun saveThumbnail(key: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(cacheDir, "$key.webp")
            FileOutputStream(file).use { fos ->
                val scaled = scaleBitmap(bitmap, COVER_SIZE)
                scaled.compress(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    },
                    WEBP_QUALITY,
                    fos
                )
                if (scaled != bitmap) {
                    scaled.recycle()
                }
            }
            file.absolutePath
        }
    }

    /**
     * Saves thumbnail from byte array.
     */
    suspend fun put(key: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions)
                ?: return@withContext false
            saveThumbnail(key, bitmap)
            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to put thumbnail for key: $key", e)
            false
        }
    }

    suspend fun getThumbnail(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "$key.webp")
        if (!file.exists()) return@withContext null

        try {
            file.readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read thumbnail for key: $key", e)
            null
        }
    }

    suspend fun hasThumbnail(key: String): Boolean = withContext(Dispatchers.IO) {
        File(cacheDir, "$key.webp").exists()
    }

    suspend fun deleteThumbnail(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(cacheDir, "$key.webp").delete()
        }
    }

    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    suspend fun evictIfNeeded(maxSizeBytes: Long = MAX_CACHE_SIZE_BYTES): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentSize = getCacheSizeBytes()
            if (currentSize <= maxSizeBytes) return@withContext 0

            val files = cacheDir.listFiles() ?: return@withContext 0
            val sortedByLru = files.sortedBy { it.lastModified() }
            var freedBytes = 0L
            var deletedCount = 0

            for (file in sortedByLru) {
                if (currentSize - freedBytes <= maxSizeBytes * 0.8) break
                freedBytes += file.length()
                if (file.delete()) {
                    deletedCount++
                }
            }

            Log.d(TAG, "Evicted $deletedCount files, freed ${freedBytes / 1024}KB")
            deletedCount
        }
    }

    suspend fun cleanupOrphaned(validKeys: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = cacheDir.listFiles() ?: return@withContext
            var deletedCount = 0
            for (file in files) {
                val key = file.nameWithoutExtension
                if (key !in validKeys) {
                    if (file.delete()) deletedCount++
                }
            }
            Log.d(TAG, "Cleaned up $deletedCount orphaned cover files")
            deletedCount
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = cacheDir.listFiles() ?: return@withContext
            var deletedCount = 0
            for (file in files) {
                if (file.delete()) deletedCount++
            }
            Log.d(TAG, "Cleared $deletedCount cover files")
            deletedCount
        }
    }

    private fun calculateCacheSizeBytes(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun scaleBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= targetSize && height <= targetSize) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (ratio > 1f) {
            newWidth = targetSize
            newHeight = (targetSize / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = targetSize
            newWidth = (targetSize * ratio).toInt().coerceAtLeast(1)
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
