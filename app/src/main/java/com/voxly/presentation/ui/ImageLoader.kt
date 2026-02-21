package com.voxly.presentation.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.voxly.data.remote.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Loads an image from URL and returns as ImageBitmap.
 */
suspend fun loadImageBitmapFromUrl(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    val bytes = loadImageBytesFromUrl(url) ?: return null
    return decodeBitmapFromBytes(bytes)?.asImageBitmap()
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
