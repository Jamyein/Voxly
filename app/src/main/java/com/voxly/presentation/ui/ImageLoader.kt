package com.voxly.presentation.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voxly.data.remote.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

// Session-scoped LRU cache for search result album covers
private val searchResultCache = mutableMapOf<String, ImageBitmap>()
private val cacheLock = ReentrantLock()

/**
 * Loads an image from URL and returns as ImageBitmap.
 * Uses session-scoped cache for search results - cleared after selection.
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
