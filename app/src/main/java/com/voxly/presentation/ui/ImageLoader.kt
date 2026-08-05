package com.voxly.presentation.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import com.voxly.data.local.cover.CoverUriProvider
import com.voxly.data.remote.NetworkConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

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
 * Gets cover art bytes from a URL (network download, no local cache).
 */
suspend fun getCoverArtBytes(url: String?): ByteArray? {
    if (url.isNullOrBlank()) return null
    return loadImageBytesFromUrl(toHighResCoverUrl(url) ?: url)
}

/**
 * Extracts embedded album art bytes from an audio file via MediaMetadataRetriever.
 * No cache — callers that hit the same file repeatedly are expected to dedupe
 * themselves (the scan already reads art once per file through TagLib).
 */
fun extractEmbeddedCoverBytes(filePath: String): ByteArray? {
    if (filePath.isBlank()) return null
    return try {
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
}

/**
 * Loads local cover art bytes (used by the metadata editor for re-encoding).
 */
suspend fun getLocalCoverBytes(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
    extractEmbeddedCoverBytes(filePath)
}

/**
 * Decodes cover art through Coil — Coil owns the memory/disk LRU, so list,
 * detail, editor and preload paths all share one cache. Converts hardware
 * bitmaps to software so the result can feed Palette / be re-encoded.
 */
private suspend fun loadBitmapViaCoil(context: Context, data: Any, targetSizePx: Int): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(data)
        .size(targetSizePx)
        .build()
    val bitmap = (context.imageLoader.execute(request).image as? BitmapImage)?.bitmap ?: return null
    return if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
}

/**
 * Loads MediaStore album art bitmap through Coil.
 */
suspend fun loadMediaStoreAlbumArt(context: Context, albumId: Long): Bitmap? {
    if (albumId <= 0L) return null
    val uri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)
    return loadBitmapViaCoil(context, uri, 300)
}

/**
 * Loads full-resolution album art (embedded or folder cover) through Coil.
 */
suspend fun loadAlbumArtOriginalBitmap(
    context: Context,
    filePath: String,
    targetSizePx: Int
): Bitmap? {
    if (filePath.isBlank()) return null
    val coverUri = CoverUriProvider(context).getCoverUri(albumId = null, filePath = filePath) ?: return null
    return loadBitmapViaCoil(context, coverUri, targetSizePx)
}

/**
 * Loads a thumbnail of an album's cover art through Coil.
 */
suspend fun loadAlbumArtThumbnail(
    context: Context,
    filePath: String,
    targetSizePx: Int = 300
): Bitmap? {
    if (filePath.isBlank()) return null
    val coverUri = CoverUriProvider(context).getCoverUri(albumId = null, filePath = filePath) ?: return null
    return loadBitmapViaCoil(context, coverUri, targetSizePx)
}
