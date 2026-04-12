package com.voxly.data.local.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.cover.CoverDiskCache
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named
import kotlin.math.min

/**
 * Deep enrichment processor - second pass of two-pass scanning.
 * Parses SampleRate, Lyrics, and Cover Art in background.
 * Cover processing: FLAC extract → WebP 512x512 → context.cacheDir/covers/ → coverKey存Room
 */
@Singleton
class DeepEnrichProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor,
    private val coverDiskCache: CoverDiskCache,
    private val musicLibraryCache: MusicLibraryCache,
    @Named("ApplicationScope") private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "DeepEnrichProcessor"
        private const val COVER_SIZE = 512
    }

    /**
     * Enriches AudioFile with detailed metadata in background.
     * Reads SampleRate, Lyrics, and Cover Art.
     */
    suspend fun enrich(
        audioFile: AudioFile,
        albumArtist: String?,
        albumName: String?
    ): AudioFile = withContext(Dispatchers.IO) {
        try {
            val completeMetadata = metadataProcessor.readAllMetadata(audioFile.path, includeAlbumArt = true)
            val metadata = completeMetadata?.metadata ?: audioFile.metadata
            val audioInfo = completeMetadata?.audioInfo

            val coverKey = if (albumArtist != null && albumName != null) {
                cacheCoverArt(audioFile.path, albumArtist, albumName, completeMetadata?.albumArt)
            } else null

            audioFile.copy(
                sampleRate = audioInfo?.sampleRate ?: audioFile.sampleRate,
                metadata = metadata
            )
        } catch (e: Exception) {
            Timber.w(TAG, "Deep enrich failed: ${audioFile.path}", e)
            audioFile
        }
    }

    /**
     * Enriches multiple files in parallel.
     */
    suspend fun enrichBatch(
        files: List<AudioFile>,
        maxConcurrency: Int = 4
    ): List<AudioFile> = coroutineScope {
        files.map { file ->
            async(Dispatchers.IO) {
                val albumArtist = file.metadata.albumArtist ?: file.metadata.artist
                val albumName = file.metadata.album
                enrich(file, albumArtist, albumName)
            }
        }.map { it.await() }
    }

    /**
     * Caches cover art to disk and returns coverKey.
     */
    private suspend fun cacheCoverArt(
        filePath: String,
        albumArtist: String,
        albumName: String,
        albumArt: ByteArray?
    ): String? = withContext(Dispatchers.IO) {
        if (albumArt == null || albumArt.isEmpty()) return@withContext null

        val coverKey = generateCoverKey(albumArtist, albumName)

        val bitmap = android.graphics.BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
            ?: return@withContext null

        val scaled = scaleBitmap(bitmap, COVER_SIZE)
        bitmap.recycle()

        val webp = encodeWebP(scaled)
        scaled.recycle()

        if (webp != null) {
            coverDiskCache.put(coverKey, webp)
        }

        coverKey
    }

    private fun generateCoverKey(albumArtist: String, albumName: String): String {
        val input = "$albumArtist|$albumName"
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val ratio = min(
            maxSize.toFloat() / bitmap.width,
            maxSize.toFloat() / bitmap.height
        )
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun encodeWebP(bitmap: Bitmap): ByteArray? {
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            Timber.w(TAG, "WebP encoding failed", e)
            null
        }
    }
}