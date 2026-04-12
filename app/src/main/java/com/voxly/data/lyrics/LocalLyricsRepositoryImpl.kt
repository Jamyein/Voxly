package com.voxly.data.lyrics

import android.content.Context
import com.voxly.data.local.metadata.TagLibMetadataProcessor
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.Lyrics
import com.voxly.domain.repository.LocalLyricsRepository
import com.voxly.domain.repository.LyricsException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataProcessor: TagLibMetadataProcessor
) : LocalLyricsRepository {

    private val multipleSlashesRegex = Regex("//+")

    override suspend fun readLyrics(filePath: String): Result<Lyrics?> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedPath = normalizeFilePath(filePath)
                if (!File(normalizedPath).exists() && !File(filePath).exists()) {
                    return@withContext Result.failure(
                        LyricsException("File not found: $filePath. The file may have been moved or deleted.")
                    )
                }

                val metadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)

                val lyricsText = metadata?.lyrics

                if (lyricsText.isNullOrBlank()) {
                    val comment = metadata?.comment
                    if (!comment.isNullOrBlank() && comment.contains("[")) {
                        return@withContext Result.success(Lyrics.parseLrc(comment))
                    }
                    return@withContext Result.success(null)
                }

                val lyrics = if (lyricsText.contains("[") && lyricsText.contains("]")) {
                    Lyrics.parseLrc(lyricsText)
                } else {
                    Lyrics.createUnsynced(lyricsText)
                }

                Result.success(lyrics)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to read lyrics", e))
            }
        }

    override suspend fun saveLyrics(filePath: String, lyrics: Lyrics): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedPath = normalizeFilePath(filePath)
                if (!File(normalizedPath).exists() && !File(filePath).exists()) {
                    return@withContext Result.failure(
                        LyricsException("File not accessible: $filePath. The file may have been moved or deleted.")
                    )
                }

                val lyricsText = if (lyrics.isSynced) {
                    lyrics.toLrcFormat()
                } else {
                    lyrics.text
                }

                val existingMetadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = lyricsText)
                    ?: AudioMetadata(
                        title = null,
                        artist = null,
                        album = null,
                        lyrics = lyricsText
                    )

                val metadataUpdateResult = metadataProcessor.updateMetadata(normalizedPath, updatedMetadata)
                    .recover { metadataProcessor.updateMetadata(filePath, updatedMetadata) }
                if (metadataUpdateResult.isFailure) {
                    return@withContext Result.failure(LyricsException("Failed to save lyrics"))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to save lyrics", e))
            }
        }

    override suspend fun removeLyrics(filePath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val normalizedPath = normalizeFilePath(filePath)
                if (!File(normalizedPath).exists() && !File(filePath).exists()) {
                    return@withContext Result.failure(
                        LyricsException("File not accessible: $filePath. The file may have been moved or deleted.")
                    )
                }

                val existingMetadata = metadataProcessor.readMetadata(normalizedPath, includeAlbumArt = false)
                    ?: metadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                val updatedMetadata = existingMetadata?.copy(lyrics = "")
                    ?: return@withContext Result.failure(LyricsException("Cannot read file metadata"))

                val metadataUpdateResult = metadataProcessor.updateMetadata(normalizedPath, updatedMetadata)
                    .recover { metadataProcessor.updateMetadata(filePath, updatedMetadata) }
                if (metadataUpdateResult.isFailure) {
                    return@withContext Result.failure(LyricsException("Failed to remove lyrics"))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(LyricsException("Failed to remove lyrics", e))
            }
        }

    private fun normalizeFilePath(filePath: String): String {
        return filePath
            .replace(multipleSlashesRegex, "/")
            .trimEnd('/')
    }
}