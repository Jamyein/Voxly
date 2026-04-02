package com.voxly.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voxly.data.remote.NetworkConstants
import com.voxly.di.AlbumArtCacheEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import timber.log.Timber

// Session-scoped LRU cache for search result album covers (ImageBitmap)
private val searchResultCache = mutableMapOf<String, ImageBitmap>()
private val cacheLock = ReentrantLock()

// Session-scoped LRU cache for cover art bytes (ByteArray)
private val coverArtByteCache = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
private val byteCacheLock = ReentrantLock()
private const val MAX_BYTE_CACHE_SIZE = 30

// LRU cache for local album art (Bitmap)
private val localAlbumArtCache = LinkedHashMap<String, Bitmap>(200, 0.75f, true)
private val localCacheLock = ReentrantLock()
private const val MAX_LOCAL_CACHE_SIZE = 200

// Carousel专用封面缓存（15 entries, 384px）
private val carouselCoverCache = LinkedHashMap<String, Bitmap>(15, 0.75f, true)
private val carouselCacheLock = ReentrantLock()
private const val MAX_CAROUSEL_CACHE_SIZE = 15
private const val CAROUSEL_TARGET_SIZE = 384

// MediaStore album art cache (Bitmap)
private val mediaStoreAlbumCache = LinkedHashMap<String, Bitmap>(50, 0.75f, true)
private const val MAX_MEDIASTORE_CACHE_SIZE = 50

// Cache tier thresholds
private const val CORE_CACHE_SIZE = 50   // Core cache: detail page covers
private const val ESSENTIAL_CACHE_SIZE = 20  // Minimal cache: currently visible items

private const val TAG = "ImageLoader"

/**
 * Generates a size-aware cache key for album art
 */
private fun getAlbumArtCacheKey(filePath: String, albumId: Long?, sizePx: Int): String {
    return "${filePath}_${albumId}_$sizePx"
}

/**
 * Calculates actual pixels from dp size
 */
fun calculateTargetPixels(sizeDp: Int, density: Float): Int {
    return (sizeDp * density).toInt()
}

/**
 * Loads an image from URL and returns as ImageBitmap.
 * Uses session-scoped cache for search results - cleared after selection.
 * Also checks prefetch cache (coverArtByteCache) for pre-downloaded images.
 */
suspend fun loadImageBitmapFromUrl(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null

    // Check session cache first
    cacheLock.lock()
    val cached = searchResultCache[url]
    cacheLock.unlock()
    if (cached != null) {
        return cached
    }

    // Check prefetch cache (coverArtByteCache) - this is populated by prefetchCoverArtBytes
    byteCacheLock.lock()
    val prefetchedBytes = coverArtByteCache[url]
    byteCacheLock.unlock()
    if (prefetchedBytes != null) {
        val bitmap = decodeBitmapFromBytes(prefetchedBytes)?.asImageBitmap() ?: return null
        // Store in session cache for faster subsequent access
        cacheLock.lock()
        searchResultCache[url] = bitmap
        cacheLock.unlock()
        return bitmap
    }

    // Load from network
    val bytes = loadImageBytesFromUrl(url) ?: return null
    val bitmap = decodeBitmapFromBytes(bytes)?.asImageBitmap() ?: return null

    // Store in session cache
    cacheLock.lock()
    searchResultCache[url] = bitmap
    cacheLock.unlock()

    return bitmap
}

/**
 * Clears the search result image cache.
 * Call this when user selects a result.
 */
fun clearSearchResultImageCache() {
    cacheLock.lock()
    searchResultCache.clear()
    cacheLock.unlock()

    // Also clear the byte array cache
    clearCoverArtByteCache()
}

/**
 * Clears the cover art byte array cache.
 */
fun clearCoverArtByteCache() {
    byteCacheLock.lock()
    coverArtByteCache.clear()
    byteCacheLock.unlock()
}

/**
 * Prefetches cover art bytes in the background (fire-and-forget).
 * Called when search results are returned to pre-download cover art.
 */
fun prefetchCoverArtBytes(url: String?) {
    if (url.isNullOrBlank()) return

    // Check if already in cache
    byteCacheLock.lock()
    val alreadyCached = coverArtByteCache.containsKey(url)
    byteCacheLock.unlock()
    if (alreadyCached) return

    // Fire-and-forget: download in background
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val bytes = loadImageBytesFromUrl(url)
            if (bytes != null) {
                // Store in cache with LRU eviction
                byteCacheLock.lock()
                try {
                    // Remove oldest entries if at capacity
                    while (coverArtByteCache.size >= MAX_BYTE_CACHE_SIZE) {
                        val oldestKey = coverArtByteCache.keys.first()
                        coverArtByteCache.remove(oldestKey)
                    }
                    coverArtByteCache[url] = bytes
                } finally {
                    byteCacheLock.unlock()
                }
            }
        } catch (e: Exception) {
            // Silently ignore prefetch failures
        }
    }
}

/**
 * Gets cover art bytes, preferring cache over network.
 * Returns cached bytes if available, otherwise downloads and caches.
 */
suspend fun getCoverArtBytes(url: String?): ByteArray? {
    if (url.isNullOrBlank()) return null

    // Try cache first
    byteCacheLock.lock()
    val cached = coverArtByteCache[url]
    byteCacheLock.unlock()
    if (cached != null) {
        return cached
    }

    // Download from network
    val bytes = loadImageBytesFromUrl(url) ?: return null

    // Store in cache with LRU eviction
    byteCacheLock.lock()
    try {
        while (coverArtByteCache.size >= MAX_BYTE_CACHE_SIZE) {
            val oldestKey = coverArtByteCache.keys.first()
            coverArtByteCache.remove(oldestKey)
        }
        coverArtByteCache[url] = bytes
    } finally {
        byteCacheLock.unlock()
    }

    return bytes
}

/**
 * Loads an image from URL and returns as ByteArray.
 * Used for downloading album art to save to audio files.
 */
suspend fun loadImageBytesFromUrl(url: String?): ByteArray? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            if (url.startsWith("data:image", ignoreCase = true)) {
                val base64 = url.substringAfter("base64,", "")
                if (base64.isNotBlank()) {
                    return@runCatching Base64.getDecoder().decode(base64)
                }
            }

            // Set appropriate Referer based on the image URL domain
            val referer = when {
                url.contains("y.gtimg.cn") -> "https://y.qq.com"
                url.contains("music.126.net") -> "https://music.163.com"
                url.contains("music.163.com") -> "https://music.163.com"
                url.contains("mzstatic.com") -> "https://music.apple.com"
                url.contains("appleusercontent.com") -> "https://music.apple.com"
                url.contains("itunes.apple.com") -> "https://www.apple.com"
                url.contains("coverartarchive.org") -> "https://musicbrainz.org"
                else -> "https://y.qq.com"
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NetworkConstants.IMAGE_CONNECT_TIMEOUT_MS.toInt()
                readTimeout = NetworkConstants.IMAGE_READ_TIMEOUT_MS.toInt()
                setRequestProperty("User-Agent", NetworkConstants.USER_AGENT_ANDROID)
                setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                setRequestProperty("Referer", referer)
            }
            connection.inputStream.use { stream ->
                stream.readBytes()
            }
        }.getOrNull()
    }
}

/**
 * Legacy fallback for local album art loading.
 * Prefer loadAlbumArtThumbnail() which uses AlbumArtCacheManager.
 */
@Deprecated(
    message = "Use loadAlbumArtThumbnail() for consistent cache-backed loading",
    replaceWith = ReplaceWith("loadAlbumArtThumbnail(context, filePath, targetSizePx)")
)
fun loadLocalAlbumArt(filePath: String, targetSizePx: Int = 300): Bitmap? {
    if (filePath.isBlank()) return null

    // Check cache with size-aware key
    localCacheLock.lock()
    val cached = localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)]
    localCacheLock.unlock()
    if (cached != null && !cached.isRecycled) {
        return cached
    }

    // Load from file
    val bitmap = loadEmbeddedAlbumArtSized(filePath, targetSizePx)

    // Cache the result with size-aware key
    if (bitmap != null) {
        localCacheLock.lock()
        try {
            // Remove oldest entries if at capacity - don't recycle immediately,
            // just remove from map. Let GC handle the actual memory release.
            // This prevents crashes when old bitmaps are still referenced by Compose.
            while (localAlbumArtCache.size >= MAX_LOCAL_CACHE_SIZE) {
                localAlbumArtCache.keys.firstOrNull()?.let { key ->
                    localAlbumArtCache.remove(key)
                }
            }
            localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)] = bitmap
        } finally {
            localCacheLock.unlock()
        }
    }

    return bitmap
}

/**
 * Loads album art thumbnail via AlbumArtCacheManager (preferred path for UI).
 * Falls back to folder cover art (cover.jpg, folder.jpg, etc.) if no embedded art found.
 */
suspend fun loadAlbumArtThumbnail(
    context: Context,
    filePath: String,
    targetSizePx: Int = 300
): Bitmap? {
    if (filePath.isBlank()) return null

    val safeTargetSize = if (targetSizePx > 0) targetSizePx else 300

    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AlbumArtCacheEntryPoint::class.java
    )
    val cacheManager = entryPoint.albumArtCacheManager()

    // 1. Check AlbumArtCacheManager (L1 memory, L2 Room, L3 file embedded)
    cacheManager.getThumbnail(filePath)?.let { return it }

    // 2. Fallback: read from TagLib to seed cache (embedded art only)
    val metadataProcessor = entryPoint.tagLibMetadataProcessor()
    val complete = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = true)
    complete?.albumArt?.let { artBytes ->
        cacheManager.cacheAlbumArt(filePath, artBytes)
    }

    // 3. Return embedded art if found
    cacheManager.getThumbnail(filePath)?.let { return it }
    complete?.albumArt?.let { return decodeHighQualityBitmapFromBytes(it, safeTargetSize) }

    // 4. Final fallback: folder cover art (cover.jpg, folder.jpg, etc.)
    return loadFolderCoverArt(filePath, safeTargetSize)
}

/**
 * Loads full-resolution album art and decodes to target size.
 */
suspend fun loadAlbumArtOriginalBitmap(
    context: Context,
    filePath: String,
    targetSizePx: Int
): Bitmap? {
    if (filePath.isBlank()) return null

    val safeTargetSize = if (targetSizePx > 0) targetSizePx else 384

    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AlbumArtCacheEntryPoint::class.java
    )
    val cacheManager = entryPoint.albumArtCacheManager()

    val originalBytes = cacheManager.getOriginalArt(filePath)
        ?: run {
            val metadataProcessor = entryPoint.tagLibMetadataProcessor()
            val complete = metadataProcessor.readAllMetadata(filePath, includeAlbumArt = true)
            complete?.albumArt?.also { cacheManager.cacheAlbumArt(filePath, it) }
        }

    return originalBytes?.let { decodeHighQualityBitmapFromBytes(it, safeTargetSize) }
}

/**
 * Legacy fallback for local album art loading.
 * Prefer loadAlbumArtThumbnail() which uses AlbumArtCacheManager.
 */
@Deprecated(
    message = "Use loadAlbumArtThumbnail() for consistent cache-backed loading",
    replaceWith = ReplaceWith("loadAlbumArtThumbnail(context, filePath, targetSizePx)")
)
fun loadLocalAlbumArtSized(filePath: String, targetSizePx: Int): Bitmap? {
    return loadLocalAlbumArt(filePath, targetSizePx)
}

/**
 * Loads embedded album art from an audio file using MediaMetadataRetriever.
 */
private fun loadEmbeddedAlbumArt(filePath: String): Bitmap? {
    return loadEmbeddedAlbumArtSized(filePath, 300)
}

/**
 * 提取封面字节并写入Bytes Cache。
 * 单次MediaMetadataRetriever调用，同时完成existence check和bytes提取。
 * 对同一filePath重复调用是安全的（幂等）。
 */
@PublishedApi
internal fun extractAndCacheCoverBytes(filePath: String): ByteArray? {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture  // 一次性获取
            if (artBytes != null) {
                byteCacheLock.lock()
                try {
                    while (coverArtByteCache.size >= MAX_BYTE_CACHE_SIZE) {
                        coverArtByteCache.keys.firstOrNull()?.let { coverArtByteCache.remove(it) }
                    }
                    coverArtByteCache[filePath] = artBytes
                } finally {
                    byteCacheLock.unlock()
                }
            }
            artBytes
        } finally {
            retriever.release()  // 在finally中确保释放
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Loads embedded album art with explicit target size.
 */
private fun loadEmbeddedAlbumArtSized(filePath: String, targetSizePx: Int): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                decodeSampledBitmapFromBytes(artBytes, targetSizePx)
            } else {
                // Try to load from folder cover (cover.jpg, folder.jpg, etc.)
                loadFolderCoverArt(filePath, targetSizePx)
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Loads folder cover art from the parent directory of the audio file.
 */
private fun loadFolderCoverArt(filePath: String, targetSizePx: Int): Bitmap? {
    val folder = File(filePath).parentFile ?: return null
    val coverFileNames = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "album.jpg", "album.png")

    for (fileName in coverFileNames) {
        val coverFile = File(folder, fileName)
        if (coverFile.exists()) {
            return try {
                decodeSampledBitmapFromFile(coverFile.absolutePath, targetSizePx)
            } catch (e: Exception) {
                null
            }
        }
    }
    return null
}

/**
 * Decodes a sampled bitmap from byte array to reduce memory usage.
 */
private fun decodeSampledBitmapFromBytes(bytes: ByteArray, targetSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    var sampleSize = 1
    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
}

/**
 * High-quality decode: coarse sample + precise scale with filtering.
 */
private fun decodeHighQualityBitmapFromBytes(bytes: ByteArray, targetSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    var sampleSize = 1
    while (options.outWidth / sampleSize > targetSize * 2 || options.outHeight / sampleSize > targetSize * 2) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null

    val maxDim = maxOf(decoded.width, decoded.height)
    if (maxDim <= targetSize) return decoded

    val scale = targetSize.toFloat() / maxDim.toFloat()
    val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
    if (scaled != decoded) decoded.recycle()
    return scaled
}

/**
 * Decodes a sampled bitmap from file path to reduce memory usage.
 */
private fun decodeSampledBitmapFromFile(filePath: String, targetSize: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(filePath, options)

    var sampleSize = 1
    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
    }
    return BitmapFactory.decodeFile(filePath, decodeOptions)
}

/**
 * Decodes a bitmap from bytes without downsampling.
 */
private fun decodeBitmapFromBytes(bytes: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/**
 * Preloads multiple album arts in the background (fire-and-forget).
 * Uses parallel dispatch with limited concurrency for faster loading.
 */
fun preloadLocalAlbumArts(context: Context, filePaths: List<String>) {
    if (filePaths.isEmpty()) return
    CoroutineScope(Dispatchers.IO).launch {
        val jobs = filePaths.map { path ->
            launch {
                try {
                    loadAlbumArtThumbnail(context, path)
                } catch (e: Exception) {
                    // Silently ignore preload failures
                }
            }
        }
        jobs.forEach { it.join() }
    }
}

/**
 * Preloads album arts for a specific range of items in a list.
 * Used by LazyList to preload items coming into view.
 */
fun preloadAlbumArtRange(
    context: Context,
    filePaths: List<String>,
    startIndex: Int,
    endIndex: Int
) {
    if (filePaths.isEmpty() || startIndex > endIndex) return
    val safeStart = startIndex.coerceAtLeast(0)
    val safeEnd = endIndex.coerceAtMost(filePaths.lastIndex)
    if (safeStart > safeEnd) return

    val pathsToPreload = filePaths.subList(safeStart, safeEnd + 1)
        .filter { it.isNotBlank() }
    if (pathsToPreload.isNotEmpty()) {
        preloadLocalAlbumArts(context, pathsToPreload)
    }
}

/**
 * Clears the local album art cache.
 * Call this when the app needs to free memory.
 * Note: We don't call recycle() here to avoid crashes if bitmaps are still
 * referenced by Compose. The bitmaps will be garbage collected naturally.
 */
fun clearLocalAlbumArtCache() {
    localCacheLock.lock()
    try {
        // Just clear the map references, let GC handle memory
        localAlbumArtCache.clear()
    } finally {
        localCacheLock.unlock()
    }
}

/**
 * Loads MediaStore album art with caching.
 * Uses content://media/external/audio/albumart URI.
 *
 * @param context Android context
 * @param albumId MediaStore album ID
 * @return Bitmap of the album art, or null if not found
 */
fun loadMediaStoreAlbumArt(context: Context, albumId: Long): Bitmap? {
    if (albumId <= 0L) return null

    val cacheKey = "mediastore_$albumId"

    // Check cache first
    mediaStoreAlbumCache[cacheKey]?.let { cached ->
        if (!cached.isRecycled) return cached
    }

    // Load from MediaStore
    val uri = Uri.withAppendedPath(
        Uri.parse("content://media/external/audio/albumart"),
        albumId.toString()
    )

    val bitmap = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            decodeSampledBitmapFromBytes(bytes, 300)
        }
    } catch (e: Exception) {
        null
    }

    // Cache the result
    if (bitmap != null) {
        // Don't recycle immediately on eviction - let GC handle memory.
        // This prevents crashes when old bitmaps are still referenced by Compose.
        while (mediaStoreAlbumCache.size >= MAX_MEDIASTORE_CACHE_SIZE) {
            mediaStoreAlbumCache.keys.firstOrNull()?.let { key ->
                mediaStoreAlbumCache.remove(key)
            }
        }
        mediaStoreAlbumCache[cacheKey] = bitmap
    }

    return bitmap
}

/**
 * Clears the MediaStore album art cache.
 * Note: We don't call recycle() here to avoid crashes if bitmaps are still
 * referenced by Compose. The bitmaps will be garbage collected naturally.
 */
fun clearMediaStoreAlbumCache() {
    mediaStoreAlbumCache.clear()
}

/**
 * Releases non-core cache, keeping core cache (detail page covers).
 * Triggered by: TRIM_MEMORY_RUNNING_LOW
 */
fun trimToCoreCache(context: Context) {
    localCacheLock.lock()
    try {
        // Keep first CORE_CACHE_SIZE entries
        val keysToRemove = localAlbumArtCache.keys.drop(CORE_CACHE_SIZE)
        keysToRemove.forEach { localAlbumArtCache.remove(it) }
    } finally {
        localCacheLock.unlock()
    }
    // Also handle mediaStoreAlbumCache
    synchronized(mediaStoreAlbumCache) {
        val keysToRemove = mediaStoreAlbumCache.keys.drop(CORE_CACHE_SIZE)
        keysToRemove.forEach { mediaStoreAlbumCache.remove(it) }
    }
    runCatching {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AlbumArtCacheEntryPoint::class.java
        )
        entryPoint.albumArtCacheManager().trimToCoreCache()
    }
    Timber.d(TAG, "Trimmed to core cache: $CORE_CACHE_SIZE entries retained")
}

/**
 * Releases non-core cache, keeping minimal cache (currently visible items).
 * Triggered by: TRIM_MEMORY_UI_HIDDEN
 */
fun trimToEssentialCache(context: Context) {
    localCacheLock.lock()
    try {
        val keysToRemove = localAlbumArtCache.keys.drop(ESSENTIAL_CACHE_SIZE)
        keysToRemove.forEach { localAlbumArtCache.remove(it) }
    } finally {
        localCacheLock.unlock()
    }
    synchronized(mediaStoreAlbumCache) {
        val keysToRemove = mediaStoreAlbumCache.keys.drop(ESSENTIAL_CACHE_SIZE)
        keysToRemove.forEach { mediaStoreAlbumCache.remove(it) }
    }
    runCatching {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AlbumArtCacheEntryPoint::class.java
        )
        entryPoint.albumArtCacheManager().trimToEssentialCache()
    }
    Timber.d(TAG, "Trimmed to essential cache: $ESSENTIAL_CACHE_SIZE entries retained")
}

/**
 * Releases all caches.
 * Triggered by: TRIM_MEMORY_COMPLETE
 */
fun clearAllCaches(context: Context) {
    localCacheLock.lock()
    try {
        localAlbumArtCache.clear()
    } finally {
        localCacheLock.unlock()
    }
    synchronized(mediaStoreAlbumCache) {
        mediaStoreAlbumCache.clear()
    }
    CoroutineScope(Dispatchers.IO).launch {
        runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AlbumArtCacheEntryPoint::class.java
            )
            entryPoint.albumArtCacheManager().clearAllCache()
        }
    }
    Timber.d(TAG, "Cleared all album art caches")
}

/**
 * Updates a single cover in cache (used after cover editing)
 */
fun updateAlbumArtCache(filePath: String, bitmap: Bitmap, sizePx: Int = 300) {
    localCacheLock.lock()
    try {
        localAlbumArtCache[getLocalArtCacheKey(filePath, sizePx)] = bitmap
    } finally {
        localCacheLock.unlock()
    }
}

/**
 * Removes a single cover from cache
 */
fun removeAlbumArtFromCache(filePath: String) {
    localCacheLock.lock()
    try {
        localAlbumArtCache.keys.filter { it.startsWith(filePath) }.forEach {
            localAlbumArtCache.remove(it)
        }
    } finally {
        localCacheLock.unlock()
    }
}

/**
 * Finds a cached cover (used by AlbumArtImage for sync cache checking)
 */
fun findCachedAlbumArt(filePath: String?, albumId: Long?, sizePx: Int): Bitmap? {
    if (!filePath.isNullOrBlank()) {
        localAlbumArtCache[getLocalArtCacheKey(filePath, sizePx)]?.let { return it }
    }
    if (albumId != null && albumId > 0) {
        mediaStoreAlbumCache[getMediaStoreCacheKey(albumId, sizePx)]?.let { return it }
    }
    return null
}

/**
 * Gets the local album art cache key
 */
private fun getLocalArtCacheKey(filePath: String, sizePx: Int): String {
    return "${filePath}_$sizePx"
}

/**
 * 获取本地文件封面原始字节，优先从Bytes Cache读取，否则从文件提取。
 * 线程安全：提取过程在锁内执行。
 * 注意：与getCoverArtBytes(url)不同，后者用于网络图片。
 */
suspend fun getLocalCoverBytes(filePath: String): ByteArray? {
    byteCacheLock.lock()
    val cached = coverArtByteCache[filePath]
    byteCacheLock.unlock()
    if (cached != null) return cached

    return withContext(Dispatchers.IO) {
        extractAndCacheCoverBytes(filePath)
    }
}

/**
 * Legacy fallback for carousel cover loading.
 * Prefer loadAlbumArtThumbnail() + loadAlbumArtOriginalBitmap() for seamless replace.
 */
@Deprecated(
    message = "Use loadAlbumArtThumbnail()/loadAlbumArtOriginalBitmap() for carousel",
    replaceWith = ReplaceWith("loadAlbumArtThumbnail(context, filePath)")
)
suspend fun loadCarouselCoverArt(filePath: String): Bitmap? {
    if (filePath.isBlank()) return null

    carouselCacheLock.lock()
    val cached = carouselCoverCache[filePath]
    carouselCacheLock.unlock()
    if (cached != null && !cached.isRecycled) return cached

    byteCacheLock.lock()
    val bytes = coverArtByteCache[filePath]
    byteCacheLock.unlock()

    val bitmap = withContext(Dispatchers.IO) {
        if (bytes != null) {
            decodeSampledBitmapFromBytes(bytes, CAROUSEL_TARGET_SIZE)
        } else {
            val extractedBytes = extractAndCacheCoverBytes(filePath)
            extractedBytes?.let { decodeSampledBitmapFromBytes(it, CAROUSEL_TARGET_SIZE) }
        }
    } ?: return null

    carouselCacheLock.lock()
    try {
        while (carouselCoverCache.size >= MAX_CAROUSEL_CACHE_SIZE) {
            carouselCoverCache.keys.firstOrNull()?.let { carouselCoverCache.remove(it) }
        }
        carouselCoverCache[filePath] = bitmap
    } finally {
        carouselCacheLock.unlock()
    }

    return bitmap
}

/**
 * Gets the MediaStore album art cache key
 */
private fun getMediaStoreCacheKey(albumId: Long, sizePx: Int): String {
    return "mediastore_${albumId}_$sizePx"
}
