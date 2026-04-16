package com.voxly.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voxly.data.remote.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.SoftReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import timber.log.Timber

// Semaphore for limiting concurrent album art preloading
// Prevents thread pool exhaustion when preloading large lists
private val preloadSemaphore = Semaphore(8)

@Volatile
private var imageLoaderScope: CoroutineScope? = null

private val fallbackScope by lazy {
    CoroutineScope(SupervisorJob().apply {
        // Ensure fallback scope can be tracked for proper cleanup
        invokeOnCompletion { cause ->
            if (cause != null) {
                Timber.w(TAG, "Fallback scope cancelled: $cause")
            }
        }
    } + Dispatchers.IO)
}

fun initImageLoaderScope(scope: CoroutineScope) {
    imageLoaderScope = scope
}

private fun getImageLoaderScope(): CoroutineScope = imageLoaderScope ?: fallbackScope

// Session-scoped LRU cache for search result album covers (ImageBitmap) - limited to 15 entries
private val searchResultCache = LinkedHashMap<String, ImageBitmap>(15, 0.75f, true)
private val cacheLock = ReentrantLock()
private const val MAX_SEARCH_CACHE_SIZE = 15

// REMOVED: coverArtByteCache - eliminated duplicate storage (ByteArray + Bitmap)
// Cover art bytes are now decoded directly without caching intermediate bytes

// LRU cache for local album art (Bitmap) with soft references for automatic GC under memory pressure
private val localAlbumArtCache = LinkedHashMap<String, SoftReference<Bitmap>>(50, 0.75f, true)
private val localCacheLock = ReentrantLock()
private const val MAX_LOCAL_CACHE_SIZE = 50

// LRU cache for cover art bytes to avoid repeated MediaMetadataRetriever calls
// Key: file path, Value: extracted cover bytes
// Sized to ~8MB max to prevent OOM with high-resolution embedded covers.
private val coverBytesCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

// Carousel dedicated cover cache (15 entries, 384px)
private val carouselCoverCache = LinkedHashMap<String, Bitmap>(15, 0.75f, true)
private val carouselCacheLock = ReentrantLock()
private const val MAX_CAROUSEL_CACHE_SIZE = 15
private const val CAROUSEL_TARGET_SIZE = 384

// MediaStore album art cache (Bitmap)
private val mediaStoreAlbumCache = LinkedHashMap<String, Bitmap>(50, 0.75f, true)
private const val MAX_MEDIASTORE_CACHE_SIZE = 50

// Cache tier thresholds
private const val CORE_CACHE_SIZE = 25   // Core cache: detail page covers (reduced from 50)
private const val ESSENTIAL_CACHE_SIZE = 10  // Minimal cache: currently visible items (reduced from 20)

private const val TAG = "ImageLoader"

/**
 * Transforms a cover art URL to request the highest resolution (3000x3000).
 * Handles provider-specific URL patterns:
 * - iTunes: replaces size segment (e.g., 100x100 → 3000x3000)
 * - NetEase: appends ?param=3000y3000 if not present
 * - QQ Music: replaces R500x500 with R3000x3000
 * - MusicBrainz: returned as-is (already high-res)
 */
fun toHighResCoverUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.contains("mzstatic.com") || url.contains("appleusercontent.com") || url.contains("itunes.apple.com") -> {
            url.replace(Regex("\\d+x\\d+"), "3000x3000")
                .replace("http://", "https://")
        }
        url.contains("music.126.net") || url.contains("p1.music.126.net") -> {
            if (url.contains("param=")) url else "$url?param=3000y3000"
        }
        url.contains("y.gtimg.cn") -> {
            url.replace("R500x500", "R3000x3000")
                .replace("R300x300", "R3000x3000")
                .replace("R150x150", "R3000x3000")
                .replace("http://", "https://")
        }
        else -> url
    }
}

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
 * Uses session-scoped LRU cache (max 15 entries) for search results.
 * Cache is automatically trimmed when exceeding size limit.
 */
suspend fun loadImageBitmapFromUrl(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null

    val highResUrl = toHighResCoverUrl(url) ?: url

    // Check session cache first with LRU eviction
    cacheLock.lock()
    val cached = searchResultCache[highResUrl]
    cacheLock.unlock()
    if (cached != null) {
        return cached
    }

    // Load from network
    val bytes = loadImageBytesFromUrl(highResUrl) ?: return null
    val bitmap = decodeBitmapFromBytes(bytes)?.asImageBitmap() ?: return null

    // Store in session cache with LRU eviction
    cacheLock.lock()
    try {
        // Remove oldest entries if at capacity
        while (searchResultCache.size >= MAX_SEARCH_CACHE_SIZE) {
            val oldestKey = searchResultCache.keys.firstOrNull()
            if (oldestKey != null) {
                searchResultCache.remove(oldestKey)
            }
        }
        searchResultCache[highResUrl] = bitmap
    } finally {
        cacheLock.unlock()
    }

    return bitmap
}

/**
 * Clears the search result image cache.
 * Call this when user selects a result.
 */
fun clearSearchResultImageCache() {
    cacheLock.lock()
    try {
        searchResultCache.clear()
    } finally {
        cacheLock.unlock()
    }
}

/**
 * Clears the cover art byte array cache.
 * DEPRECATED: Byte cache has been removed to eliminate duplicate storage.
 * This function is kept for API compatibility but does nothing.
 */
@Deprecated("Byte cache has been removed", ReplaceWith("clearSearchResultImageCache()"))
fun clearCoverArtByteCache() {
    // No-op: Byte caching has been eliminated to prevent duplicate memory usage
}

/**
 * Prefetches cover art in the background (fire-and-forget).
 * Downloads image and stores in bitmap cache for faster display.
 * Called when search results are returned to pre-download cover art.
 * Note: Now stores decoded Bitmap instead of ByteArray to eliminate duplicate storage.
 */
fun prefetchCoverArtBytes(url: String?) {
    if (url.isNullOrBlank()) return

    val highResUrl = toHighResCoverUrl(url) ?: url

    // Check if already in bitmap cache
    cacheLock.lock()
    val alreadyCached = searchResultCache.containsKey(highResUrl)
    cacheLock.unlock()
    if (alreadyCached) return

    // Fire-and-forget: download and decode in background
    getImageLoaderScope().launch(Dispatchers.IO) {
        try {
            val bytes = loadImageBytesFromUrl(highResUrl)
            if (bytes != null) {
                val bitmap = decodeBitmapFromBytes(bytes)?.asImageBitmap()
                if (bitmap != null) {
                    cacheLock.lock()
                    try {
                        // Remove oldest entries if at capacity
                        while (searchResultCache.size >= MAX_SEARCH_CACHE_SIZE) {
                            val oldestKey = searchResultCache.keys.firstOrNull()
                            if (oldestKey != null) {
                                searchResultCache.remove(oldestKey)
                            }
                        }
                        searchResultCache[highResUrl] = bitmap
                    } finally {
                        cacheLock.unlock()
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore prefetch failures
        }
    }
}

/**
 * Gets cover art bytes from network.
 * Note: Byte caching has been removed. Always downloads fresh.
 * Use loadImageBitmapFromUrl() for cached bitmap access.
 */
suspend fun getCoverArtBytes(url: String?): ByteArray? {
    if (url.isNullOrBlank()) return null
    val highResUrl = toHighResCoverUrl(url) ?: url
    return loadImageBytesFromUrl(highResUrl)
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
 * Prefer loadAlbumArtThumbnail() for consistent cache-backed loading.
 */
@Deprecated(
    message = "Use loadAlbumArtThumbnail() for consistent cache-backed loading",
    replaceWith = ReplaceWith("loadAlbumArtThumbnail(context, filePath, targetSizePx)")
)
fun loadLocalAlbumArt(filePath: String, targetSizePx: Int = 300): Bitmap? {
    if (filePath.isBlank()) return null

    // Check cache with size-aware key - unwrap SoftReference
    localCacheLock.lock()
    val cachedRef = localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)]
    localCacheLock.unlock()
    val cached = cachedRef?.get()
    if (cached != null && !cached.isRecycled) {
        return cached
    }

    // Load from file
    val bitmap = loadEmbeddedAlbumArtSized(filePath, targetSizePx)

    // Cache the result with size-aware key - wrap in SoftReference
    if (bitmap != null) {
        localCacheLock.lock()
        try {
            // Remove oldest entries if at capacity
            while (localAlbumArtCache.size >= MAX_LOCAL_CACHE_SIZE) {
                localAlbumArtCache.keys.firstOrNull()?.let { key ->
                    localAlbumArtCache.remove(key)
                }
            }
            localAlbumArtCache[getLocalArtCacheKey(filePath, targetSizePx)] = SoftReference(bitmap)
        } finally {
            localCacheLock.unlock()
        }
    }

    return bitmap
}

/**
 * Loads album art thumbnail from embedded art or folder cover.
 * Falls back to folder cover art (cover.jpg, folder.jpg, etc.) if no embedded art found.
 */
suspend fun loadAlbumArtThumbnail(
    context: Context,
    filePath: String,
    targetSizePx: Int = 300
): Bitmap? {
    if (filePath.isBlank()) return null

    val safeTargetSize = if (targetSizePx > 0) targetSizePx else 300
    val cacheKey = getLocalArtCacheKey(filePath, safeTargetSize)

    // 1. Check cache
    localCacheLock.lock()
    val cachedRef = localAlbumArtCache[cacheKey]
    localCacheLock.unlock()
    val cached = cachedRef?.get()
    if (cached != null && !cached.isRecycled) {
        return cached
    }

    return withContext(Dispatchers.IO) {
        // 2. Try embedded album art via MediaMetadataRetriever
        val bitmap = loadEmbeddedAlbumArtSized(filePath, safeTargetSize)
            ?: loadFolderCoverArt(filePath, safeTargetSize)

        // 3. Cache result
        if (bitmap != null) {
            localCacheLock.lock()
            try {
                while (localAlbumArtCache.size >= MAX_LOCAL_CACHE_SIZE) {
                    localAlbumArtCache.keys.firstOrNull()?.let { localAlbumArtCache.remove(it) }
                }
                localAlbumArtCache[cacheKey] = SoftReference(bitmap)
            } finally {
                localCacheLock.unlock()
            }
        }

        bitmap
    }
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

    return withContext(Dispatchers.IO) {
        extractAndCacheCoverBytes(filePath)?.let {
            decodeHighQualityBitmapFromBytes(it, safeTargetSize)
        }
    }
}

/**
 * Legacy fallback for local album art loading.
 * Prefer loadAlbumArtThumbnail() for consistent cache-backed loading.
 */
@Suppress("DEPRECATION")
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
 * Extracts cover art bytes from file with LRU caching.
 * Single MediaMetadataRetriever call per unique file (cached for session).
 * Prevents repeated I/O during scrolling and recomposition.
 */
@PublishedApi
internal fun extractAndCacheCoverBytes(filePath: String): ByteArray? {
    if (filePath.isBlank()) return null

    // Check LRU cache first (LruCache is thread-safe)
    coverBytesCache[filePath]?.let { return it }

    // Cache miss: extract from file
    val bytes: ByteArray? = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    } catch (e: Exception) {
        null
    }

    // Store in LRU cache
    if (bytes != null) {
        coverBytesCache.put(filePath, bytes)
    }

    return bytes
}

/**
 * Loads embedded album art with explicit target size.
 * Uses cached bytes from extractAndCacheCoverBytes to avoid repeated I/O.
 */
private fun loadEmbeddedAlbumArtSized(filePath: String, targetSizePx: Int): Bitmap? {
    return extractAndCacheCoverBytes(filePath)?.let { artBytes ->
        decodeSampledBitmapFromBytes(artBytes, targetSizePx)
    } ?: loadFolderCoverArt(filePath, targetSizePx)
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
internal fun decodeBitmapFromBytes(bytes: ByteArray): Bitmap? {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/**
 * Decodes a sampled bitmap from bytes to reduce memory usage.
 */
internal fun decodeBitmapFromBytes(bytes: ByteArray, targetSize: Int): Bitmap? {
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
 * Preloads multiple album arts in the background (fire-and-forget).
 * Uses parallel dispatch with limited concurrency for faster loading.
 * Semaphore limits concurrent loads to prevent thread pool exhaustion.
 */
fun preloadLocalAlbumArts(context: Context, filePaths: List<String>) {
    if (filePaths.isEmpty()) return
    getImageLoaderScope().launch(Dispatchers.IO) {
        filePaths.forEach { path ->
            launch {
                preloadSemaphore.withPermit {
                    try {
                        loadAlbumArtThumbnail(context, path)
                    } catch (e: Exception) {
                        // Silently ignore preload failures
                    }
                }
            }
        }
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
    Timber.d(TAG, "Cleared all album art caches")
}

/**
 * Updates a single cover in cache (used after cover editing)
 */
fun updateAlbumArtCache(filePath: String, bitmap: Bitmap, sizePx: Int = 300) {
    localCacheLock.lock()
    try {
        localAlbumArtCache[getLocalArtCacheKey(filePath, sizePx)] = SoftReference(bitmap)
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
        localAlbumArtCache[getLocalArtCacheKey(filePath, sizePx)]?.get()?.let { 
            if (!it.isRecycled) return it 
        }
    }
    if (albumId != null && albumId > 0) {
        mediaStoreAlbumCache[getMediaStoreCacheKey(albumId, sizePx)]?.let { 
            if (!it.isRecycled) return it 
        }
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
 * Gets local file cover bytes directly from file without caching.
 * Note: Byte caching has been removed to eliminate duplicate storage.
 */
suspend fun getLocalCoverBytes(filePath: String): ByteArray? {
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

    // Extract bytes directly without caching
    val bitmap = withContext(Dispatchers.IO) {
        extractAndCacheCoverBytes(filePath)?.let { bytes ->
            decodeSampledBitmapFromBytes(bytes, CAROUSEL_TARGET_SIZE)
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
