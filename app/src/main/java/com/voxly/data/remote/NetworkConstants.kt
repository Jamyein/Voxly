package com.voxly.data.remote

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Centralized network constants for the application.
 * Consolidates User-Agent strings and timeout values to avoid duplication.
 */
object NetworkConstants {
    // User-Agent strings
    const val USER_AGENT_ANDROID = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    const val USER_AGENT_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val USER_AGENT_LINUX = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"
    const val USER_AGENT_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"

    // Default User-Agent for API calls
    const val DEFAULT_USER_AGENT = USER_AGENT_ANDROID

    // Network timeouts in seconds
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    // Image loader timeouts in milliseconds
    // Increased from 5s to 15s for better reliability on slow networks
    const val IMAGE_CONNECT_TIMEOUT_MS = 15000L
    const val IMAGE_READ_TIMEOUT_MS = 15000L
    const val IMAGE_MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024
}

fun downloadImageBytes(
    url: String,
    userAgent: String = NetworkConstants.DEFAULT_USER_AGENT,
    referer: String? = null,
    maxBytes: Int = NetworkConstants.IMAGE_MAX_DOWNLOAD_BYTES
): ByteArray {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = NetworkConstants.IMAGE_CONNECT_TIMEOUT_MS.toInt()
        readTimeout = NetworkConstants.IMAGE_READ_TIMEOUT_MS.toInt()
        setRequestProperty("User-Agent", userAgent)
        if (!referer.isNullOrBlank()) {
            setRequestProperty("Referer", referer)
        }
    }

    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("Image download failed: HTTP $responseCode")
        }

        val contentLength = connection.contentLengthLong
        if (contentLength > maxBytes) {
            throw IOException("Image download exceeds limit: $contentLength bytes")
        }

        connection.inputStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break

                totalRead += read
                if (totalRead > maxBytes) {
                    throw IOException("Image download exceeds limit: $totalRead bytes")
                }
                output.write(buffer, 0, read)
            }

            output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}
