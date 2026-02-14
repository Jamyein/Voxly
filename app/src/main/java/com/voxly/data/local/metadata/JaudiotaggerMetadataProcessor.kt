package com.voxly.data.local.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.audio.exceptions.NoWritePermissionsException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.images.Artwork
import java.io.File
import java.io.FileInputStream
import java.nio.file.AccessDeniedException
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
        private const val CUSTOM_RECORD_LABEL = "record_label"
        private const val CUSTOM_ENCODER = "encoder"
        private const val CUSTOM_ISRC = "isrc"
        private const val CUSTOM_COPYRIGHT = "copyright"
    }
    private val canUseJavaxImageIO: Boolean by lazy {
        runCatching {
            Class.forName("javax.imageio.ImageIO")
            true
        }.getOrDefault(false)
    }

    data class AudioInfo(
        val bitrate: Int,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long
    )

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
            val customFields = buildMap {
                getOptionalField(tag, "RECORD_LABEL")?.let { put(CUSTOM_RECORD_LABEL, it) }
                getOptionalField(tag, "ENCODER")?.let { put(CUSTOM_ENCODER, it) }
                getOptionalField(tag, "ISRC")?.let { put(CUSTOM_ISRC, it) }
                getOptionalField(tag, "COPYRIGHT")?.let { put(CUSTOM_COPYRIGHT, it) }
            }

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
                albumArt = if (includeAlbumArt) extractAlbumArt(tag) else null,
                customFields = customFields
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
    suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("File does not exist: $filePath")
                    )
                }

                val audioFile: AudioFile = AudioFileIO.read(file)
                val tag = audioFile.tag ?: audioFile.createDefaultTag()
                applyMetadataToTag(tag, metadata)

                // Save the file
                AudioFileIO.write(audioFile)
                val persisted = readMetadata(filePath, includeAlbumArt = false)
                if (metadataConsistent(metadata, persisted)) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        IllegalStateException("Metadata verification failed after write: $filePath")
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Direct file write denied, trying SAF fallback: $filePath", e)
                updateMetadataViaSaf(filePath, metadata, e)
            } catch (e: NoWritePermissionsException) {
                Log.w(TAG, "Direct file write denied, trying SAF fallback: $filePath", e)
                updateMetadataViaSaf(filePath, metadata, e)
            } catch (e: AccessDeniedException) {
                Log.w(TAG, "Direct file write denied, trying SAF fallback: $filePath", e)
                updateMetadataViaSaf(filePath, metadata, e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update metadata: $filePath", e)
                Result.failure(e)
            }
        }

    private fun applyMetadataToTag(tag: Tag, metadata: AudioMetadata) {
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
        setOptionalField(tag, "RECORD_LABEL", metadata.customFields[CUSTOM_RECORD_LABEL])
        setOptionalField(tag, "ENCODER", metadata.customFields[CUSTOM_ENCODER])
        setOptionalField(tag, "ISRC", metadata.customFields[CUSTOM_ISRC])
        setOptionalField(tag, "COPYRIGHT", metadata.customFields[CUSTOM_COPYRIGHT])

        metadata.trackNumber?.let { tag.setField(FieldKey.TRACK, it.toString()) }
        metadata.totalTracks?.let { tag.setField(FieldKey.TRACK_TOTAL, it.toString()) }
        metadata.discNumber?.let { tag.setField(FieldKey.DISC_NO, it.toString()) }
        metadata.totalDiscs?.let { tag.setField(FieldKey.DISC_TOTAL, it.toString()) }

        if (metadata.albumArt != null && tag is FlacTag) {
            writeFlacArtwork(tag, metadata.albumArt)
        } else if (metadata.albumArt != null && canUseJavaxImageIO) {
            try {
                val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                artwork.setBinaryData(metadata.albumArt)
                artwork.setMimeType(guessMimeType(metadata.albumArt))
                artwork.setDescription("")
                artwork.setPictureType(org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID)
                tag.setField(artwork)
            } catch (t: Throwable) {
                if (t is VirtualMachineError || t is ThreadDeath) throw t
                Log.w(TAG, "Skipping album art write on this device/runtime", t)
            }
        } else if (metadata.albumArt != null) {
            Log.w(TAG, "Skipping album art write: javax.imageio.ImageIO is unavailable on Android runtime")
        } else {
            runCatching { tag.deleteArtworkField() }
                .onFailure { Log.w(TAG, "Failed to remove album art", it) }
        }
    }

    private fun writeFlacArtwork(flacTag: FlacTag, bytes: ByteArray) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth.coerceAtLeast(0)
            val height = bounds.outHeight.coerceAtLeast(0)
            val field = flacTag.createArtworkField(
                bytes,
                org.jaudiotagger.tag.reference.PictureTypes.DEFAULT_ID,
                guessMimeType(bytes),
                "",
                width,
                height,
                0,
                0
            )
            flacTag.setField(field)
        } catch (t: Throwable) {
            if (t is VirtualMachineError || t is ThreadDeath) throw t
            Log.w(TAG, "Failed writing FLAC artwork via MetadataBlockDataPicture", t)
        }
    }

    private fun guessMimeType(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) return "image/jpeg"

        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) return "image/png"

        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) return "image/webp"

        return "image/jpeg"
    }

    private suspend fun updateMetadataViaSaf(filePath: String, metadata: AudioMetadata, cause: Throwable): Result<Unit> {
        // Resolve the target file URI
        val targetUri = resolveDocumentUriForPath(filePath)
            ?: return Result.failure(
                IllegalStateException(
                    "No writable SAF directory found for this file. The folder permission may have been revoked. Please re-add the folder in Directory Management and grant write access.",
                    cause
                )
            )

        // Find the tree (directory) URI that contains this file and verify it still has write permission
        val normalizedPath = runCatching { File(filePath).canonicalPath }.getOrDefault(filePath)
        val validTreeUri = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }
            .mapNotNull { perm -> mapTreeUriToPath(perm.uri)?.let { perm.uri to it } }
            .firstOrNull { (_, treePath) ->
                val normalizedTree = treePath.trimEnd('/')
                normalizedPath == normalizedTree || normalizedPath.startsWith("$normalizedTree/")
            }?.first

        if (validTreeUri == null) {
            Log.w(TAG, "SAF permission no longer valid for: $filePath")
            return Result.failure(
                IllegalStateException(
                    "Storage permission for this file has expired or been revoked. Please re-add the folder in Directory Management to restore write access.",
                    cause
                )
            )
        }

        val sourceExt = File(filePath).extension
            .takeIf { it.isNotBlank() }
            ?.let { ".${it.lowercase()}" }
            ?: ".audio"
        val tempFile = runCatching { File.createTempFile("voxly-edit-", sourceExt, context.cacheDir) }.getOrNull()
            ?: return Result.failure(IllegalStateException("Failed to create temp file for SAF write", cause))

        return try {
            // Use the target URI to read the source file
            val sourceStream = context.contentResolver.openInputStream(targetUri)
                ?: return Result.failure(IllegalStateException("Unable to open source audio for SAF write. Please re-add the folder in Directory Management.", cause))

            sourceStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val tempAudio = AudioFileIO.read(tempFile)
            val tempTag = tempAudio.tag ?: tempAudio.createDefaultTag()
            applyMetadataToTag(tempTag, metadata)

            val outputStream = context.contentResolver.openOutputStream(targetUri, "rwt")
                ?: context.contentResolver.openOutputStream(targetUri, "w")
                ?: return Result.failure(
                    IllegalStateException(
                        "Unable to open target URI for SAF write. Please re-add the folder in Directory Management.",
                        cause
                    )
                )

            outputStream.use { output ->
                tempFile.inputStream().use { edited -> edited.copyTo(output) }
            }

            val persisted = readMetadata(filePath, includeAlbumArt = false)
            if (!metadataConsistent(metadata, persisted)) {
                return Result.failure(
                    IllegalStateException(
                        "SAF write completed but metadata verification failed. Please re-add the folder permission and retry.",
                        cause
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SAF metadata write failed: $filePath", e)
            Result.failure(
                IllegalStateException(
                    "Failed to save metadata through SAF. The file may be locked or the permission may have been revoked. Please re-add the folder in Directory Management and retry.",
                    e
                )
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun metadataConsistent(expected: AudioMetadata, actual: AudioMetadata?): Boolean {
        if (actual == null) return false

        fun norm(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

        return norm(expected.title) == norm(actual.title) &&
            norm(expected.artist) == norm(actual.artist) &&
            norm(expected.album) == norm(actual.album) &&
            norm(expected.albumArtist) == norm(actual.albumArtist) &&
            norm(expected.year) == norm(actual.year) &&
            norm(expected.genre) == norm(actual.genre) &&
            expected.trackNumber == actual.trackNumber &&
            expected.totalTracks == actual.totalTracks &&
            expected.discNumber == actual.discNumber &&
            expected.totalDiscs == actual.totalDiscs &&
            norm(expected.composer) == norm(actual.composer) &&
            norm(expected.lyricist) == norm(actual.lyricist) &&
            norm(expected.conductor) == norm(actual.conductor) &&
            norm(expected.originalArtist) == norm(actual.originalArtist) &&
            norm(expected.comment) == norm(actual.comment) &&
            norm(expected.lyrics) == norm(actual.lyrics) &&
            norm(expected.customFields[CUSTOM_RECORD_LABEL]) == norm(actual.customFields[CUSTOM_RECORD_LABEL]) &&
            norm(expected.customFields[CUSTOM_ENCODER]) == norm(actual.customFields[CUSTOM_ENCODER]) &&
            norm(expected.customFields[CUSTOM_ISRC]) == norm(actual.customFields[CUSTOM_ISRC]) &&
            norm(expected.customFields[CUSTOM_COPYRIGHT]) == norm(actual.customFields[CUSTOM_COPYRIGHT])
    }

    private fun resolveDocumentUriForPath(filePath: String): Uri? {
        val normalizedPath = runCatching { File(filePath).canonicalPath }.getOrDefault(filePath)
        val permissions = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }

        for (perm in permissions) {
            val treePath = mapTreeUriToPath(perm.uri) ?: continue
            val normalizedTree = treePath.trimEnd('/')
            val relative = when {
                normalizedPath == normalizedTree -> ""
                normalizedPath.startsWith("$normalizedTree/") -> normalizedPath.removePrefix("$normalizedTree/")
                else -> continue
            }
            val uri = findDocumentUriInTree(perm.uri, relative)
            if (uri != null) return uri
        }
        return null
    }

    private fun mapTreeUriToPath(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (documentId.startsWith("raw:")) return documentId.removePrefix("raw:")
        val parts = documentId.split(":", limit = 2)
        val volume = parts.firstOrNull().orEmpty()
        val relative = parts.getOrNull(1)?.trim('/').orEmpty()
        return when {
            volume.equals("primary", ignoreCase = true) -> {
                val root = "/storage/emulated/0"
                if (relative.isEmpty()) root else "$root/$relative"
            }
            volume.equals("home", ignoreCase = true) -> {
                val root = "/storage/emulated/0/Documents"
                if (relative.isEmpty()) root else "$root/$relative"
            }
            volume.isNotEmpty() -> if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
            else -> null
        }
    }

    private fun findDocumentUriInTree(treeUri: Uri, relativePath: String): Uri? {
        var currentDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (relativePath.isBlank()) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
        }
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            var nextDocId: String? = null
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (name == segment) {
                        nextDocId = cursor.getString(idIndex)
                        break
                    }
                }
            }
            currentDocId = nextDocId ?: return null
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
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

    private fun getOptionalField(tag: Tag, fieldName: String): String? {
        val key = runCatching { FieldKey.valueOf(fieldName) }.getOrNull() ?: return null
        return runCatching { tag.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun setOptionalField(tag: Tag, fieldName: String, value: String?) {
        val key = runCatching { FieldKey.valueOf(fieldName) }.getOrNull() ?: return
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            runCatching { tag.deleteField(key) }
            return
        }
        runCatching { tag.setField(key, normalized) }
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
     * @return AudioInfo with bitrate/sample rate/channels/duration, or null if reading fails
     */
    suspend fun readAudioInfo(filePath: String): AudioInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            val audioFile: AudioFile = AudioFileIO.read(file)
            val header: AudioHeader = audioFile.audioHeader

            AudioInfo(
                bitrate = header.bitRate?.toIntOrNull() ?: 0,
                sampleRate = header.sampleRate?.toIntOrNull() ?: 0,
                channels = header.channels?.toIntOrNull() ?: 0,
                durationMs = (header.trackLength?.toLong() ?: 0L) * 1000L
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
