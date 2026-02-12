package com.voxly.data.local.metadata

import android.content.Context
import android.util.Log
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadata processor using jaudiotagger library.
 * Supports MP3, FLAC, OGG, M4A, WMA, WAV, APE, and other formats.
 */
@Singleton
class JaudiotaggerMetadataProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MetadataProcessor"
    }

    /**
     * Reads metadata from an audio file.
     * @param filePath Path to the audio file
     * @param includeAlbumArt Whether to decode embedded artwork bytes
     * @return AudioMetadata object or null if reading fails
     */
    suspend fun readMetadata(
        filePath: String,
        includeAlbumArt: Boolean = true
    ): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext null
            }

            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag: Tag = audioFile.tag ?: return@withContext null

            AudioMetadata(
                title = tag.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() },
                artist = tag.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() },
                album = tag.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() },
                albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST)?.takeIf { it.isNotBlank() },
                year = tag.getFirst(FieldKey.YEAR)?.takeIf { it.isNotBlank() },
                genre = tag.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() },
                trackNumber = tag.getFirst(FieldKey.TRACK)?.toIntOrNull(),
                totalTracks = tag.getFirst(FieldKey.TRACK_TOTAL)?.toIntOrNull(),
                discNumber = tag.getFirst(FieldKey.DISC_NO)?.toIntOrNull(),
                totalDiscs = tag.getFirst(FieldKey.DISC_TOTAL)?.toIntOrNull(),
                composer = tag.getFirst(FieldKey.COMPOSER)?.takeIf { it.isNotBlank() },
                lyricist = tag.getFirst(FieldKey.LYRICIST)?.takeIf { it.isNotBlank() },
                conductor = tag.getFirst(FieldKey.CONDUCTOR)?.takeIf { it.isNotBlank() },
                originalArtist = tag.getFirst(FieldKey.ORIGINAL_ARTIST)?.takeIf { it.isNotBlank() },
                comment = tag.getFirst(FieldKey.COMMENT)?.takeIf { it.isNotBlank() },
                lyrics = tag.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() },
                albumArt = if (includeAlbumArt) extractAlbumArt(tag) else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading metadata: $filePath", e)
            null
        }
    }

    /**
     * Updates metadata for an audio file.
     * @param filePath Path to the audio file
     * @param metadata Metadata to write
     * @return true if successful, false otherwise
     */
    suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists() || !file.canRead() || !file.canWrite()) {
                    return@withContext false
                }

                val audioFile: AudioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: audioFile.createDefaultTag()

                // Set text fields
                metadata.title?.let { tag.setField(FieldKey.TITLE, it) }
                metadata.artist?.let { tag.setField(FieldKey.ARTIST, it) }
                metadata.album?.let { tag.setField(FieldKey.ALBUM, it) }
                metadata.albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
                metadata.year?.let { tag.setField(FieldKey.YEAR, it) }
                metadata.genre?.let { tag.setField(FieldKey.GENRE, it) }
                metadata.composer?.let { tag.setField(FieldKey.COMPOSER, it) }
                metadata.lyricist?.let { tag.setField(FieldKey.LYRICIST, it) }
                metadata.conductor?.let { tag.setField(FieldKey.CONDUCTOR, it) }
                metadata.originalArtist?.let { tag.setField(FieldKey.ORIGINAL_ARTIST, it) }
                metadata.comment?.let { tag.setField(FieldKey.COMMENT, it) }
                metadata.lyrics?.let { tag.setField(FieldKey.LYRICS, it) }

                // Set numeric fields
                metadata.trackNumber?.let {
                    tag.setField(FieldKey.TRACK, it.toString())
                }
                metadata.totalTracks?.let {
                    tag.setField(FieldKey.TRACK_TOTAL, it.toString())
                }
                metadata.discNumber?.let {
                    tag.setField(FieldKey.DISC_NO, it.toString())
                }
                metadata.totalDiscs?.let {
                    tag.setField(FieldKey.DISC_TOTAL, it.toString())
                }

                // Set album art
                metadata.albumArt?.let { artBytes ->
                    val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                    artwork.setBinaryData(artBytes)
                    artwork.setMimeType("image/jpeg")
                    artwork.setDescription("")
                    artwork.setPictureType(org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID)
                    tag.setField(artwork)
                } ?: run {
                    // Remove album art if bytes are null
                    tag.deleteArtworkField()
                }

                // Save the file
                AudioFileIO.write(audioFile)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    /**
     * Extracts album art from a tag.
     * @param tag The audio tag
     * @return ByteArray of album art or null if not available
     */
    private fun extractAlbumArt(tag: Tag): ByteArray? {
        return try {
            val artwork: Artwork? = tag.firstArtwork
            artwork?.binaryData
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts album art from a file.
     * @param filePath Path to the audio file
     * @return ByteArray of album art or null if not available
     */
    suspend fun extractAlbumArt(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag = audioFile.tag

            tag?.firstArtwork?.binaryData
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads audio file technical information.
     * @param filePath Path to the audio file
     * @return Triple of (bitrate, sampleRate, channels) or null if reading fails
     */
    suspend fun readAudioInfo(filePath: String): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val header: AudioHeader = audioFile.audioHeader

            Triple(
                header.bitRate?.toIntOrNull() ?: 0,
                header.sampleRate?.toIntOrNull() ?: 0,
                header.channels?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a file format is supported.
     * @param filePath Path to check
     * @return true if supported
     */
    suspend fun isFormatSupported(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val extension = filePath.substringAfterLast('.').lowercase()
            val supportedExtensions = setOf(
                "mp3", "flac", "ogg", "m4a", "mp4", "wma", "wav", "ape", "wv", "opus"
            )
            extension in supportedExtensions
        } catch (e: Exception) {
            false
        }
    }
}
