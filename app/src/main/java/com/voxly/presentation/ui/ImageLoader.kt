package com.voxly.presentation.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

suspend fun loadImageBitmapFromUrl(url: String?): ImageBitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            if (url.startsWith("data:image", ignoreCase = true)) {
                val base64 = url.substringAfter("base64,", "")
                if (base64.isNotBlank()) {
                    val bytes = Base64.getDecoder().decode(base64)
                    return@runCatching decodeBitmapFromBytes(bytes)?.asImageBitmap()
                }
            }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Voxly/1.0")
                setRequestProperty("Referer", "https://y.qq.com")
            }
            connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                decodeBitmapFromBytes(bytes)?.asImageBitmap()
            }
        }.getOrNull()
    }
}
