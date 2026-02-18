package com.voxly.data.local.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio metadata processor using Kyant0/taglib library.
 * 
 * TagLib is a Kotlin wrapper around TagLib that supports Android's Storage Access Framework (SAF).
 * This processor handles reading and writing metadata for various audio formats:
 * MP3, FLAC, OGG, M4A, WAV, WMA, APE, Opus, WV
 * 
 * Note: This processor uses io.github.kyant0:taglib from Maven Central (replaces KTagLib from JitPack)
 */
@Singleton
class TagLibMetadataProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TagLibProcessor"
        
        // Custom field keys
        const val CUSTOM_RECORD_LABEL = "RECORD_LABEL"
        const val CUSTOM_ENCODER = "ENCODER"
        const val CUSTOM_ISRC = "ISRC"
        const val CUSTOM_COPYRIGHT = "COPYRIGHT"
        
        // ReplayGain field keys
        const val CUSTOM_REPLAYGAIN_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
        const val CUSTOM_REPLAYGAIN_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
        const val CUSTOM_REPLAYGAIN_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
        const val CUSTOM_REPLAYGAIN_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"
        
        // Supported extensions
        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "m4a", "mp4", "wav", "wma", "ape", "opus", "wv"
        )
    }

    /**
     * Data class for audio technical information
     */
    data class AudioInfo(
        val bitrate: Int,
        val sampleRate: Int,
        val channels: Int,
        val durationMs: Long
    )

    /**
     * Normalizes a file path to handle common path issues.
     * - Removes duplicate slashes
     * - Removes trailing slashes
     * - Resolves canonical path when possible
     */
    private fun normalizeFilePath(filePath: String): String {
        return try {
            // First, clean up obvious issues
            var normalized = filePath
                .replace(Regex("//+"), "/")  // Remove duplicate slashes
                .trimEnd('/')                // Remove trailing slash
            
            // Try to get canonical path for proper path resolution
            val file = File(normalized)
            if (file.exists()) {
                file.canonicalPath
            } else {
                // Try without canonical resolution - might be a path that needs SAF
                normalized
            }
        } catch (e: Exception) {
            // If anything fails, return cleaned original path
            filePath.replace(Regex("//+"), "/").trimEnd('/')
        }
    }

    /**
     * Reads metadata from an audio file.
     * @param filePath Path to the audio file
     * @param includeAlbumArt Whether to include album art bytes
     * @return AudioMetadata object or null if reading fails
     */
    suspend fun readMetadata(
        filePath: String,
        includeAlbumArt: Boolean = true
    ): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            // Normalize the file path first
            val normalizedPath = normalizeFilePath(filePath)
            val file = File(normalizedPath)
            
            if (!file.exists()) {
                // Try alternative path resolution strategies
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    val resolvedFile = File(resolvedPath)
                    if (resolvedFile.exists()) {
                        return@withContext readMetadataFromFile(resolvedFile, includeAlbumArt)
                    }
                }
                Log.w(TAG, "File does not exist: $filePath (normalized: $normalizedPath)")
                return@withContext null
            }

            readMetadataFromFile(file, includeAlbumArt)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read metadata: $filePath", e)
            null
        }
    }

    /**
     * Attempts to resolve a file path by searching in likely locations.
     * This handles cases where MediaStore paths become stale.
     */
    private fun resolveFilePath(originalPath: String, fileName: String): String? {
        try {
            // Strategy 1: Try removing double slashes
            val cleanedPath = originalPath.replace(Regex("//+"), "/")
            if (File(cleanedPath).exists()) return cleanedPath

            // Strategy 2: Try common base directories
            val baseDirs = listOf(
                "/storage/emulated/0",
                "/storage/emulated/0/",
                "/sdcard",
                "/sdcard/"
            )
            
            // Extract relative path components
            val pathParts = originalPath
                .replace(Regex("//+"), "/")
                .trimStart('/')
                .split('/')
                .filter { it.isNotBlank() }
            
            for (baseDir in baseDirs) {
                val candidatePath = "$baseDir/${pathParts.joinToString("/")}"
                if (File(candidatePath).exists()) {
                    return candidatePath
                }
            }

            // Strategy 3: Search in common directories for the file
            val searchDirs = listOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/Ringtones",
                "/storage/emulated/0/Podcasts",
                "/storage/emulated/0/Audiobooks",
                "/storage/emulated/0",
                "/sdcard/Music",
                "/sdcard/Download",
                "/sdcard"
            )
            
            for (searchDir in searchDirs) {
                val dir = File(searchDir)
                if (dir.exists() && dir.isDirectory) {
                    // Recursively search for the file (limited depth)
                    val found = searchForFile(dir, fileName, currentDepth = 0, maxDepth = 3)
                    if (found != null) return found.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Path resolution failed for: $originalPath", e)
        }
        return null
    }

    /**
     * Recursively searches for a file by name in a directory.
     */
    private fun searchForFile(directory: File, targetName: String, currentDepth: Int, maxDepth: Int): File? {
        if (currentDepth > maxDepth) return null
        
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.isHidden) {
                    val result = searchForFile(file, targetName, currentDepth + 1, maxDepth)
                    if (result != null) return result
                } else if (file.isFile && file.name == targetName) {
                    return file
                }
            }
        } catch (e: SecurityException) {
            // Skip directories we can't access
        }
        return null
    }

    /**
     * Reads metadata from a File object.
     */
    private fun readMetadataFromFile(file: File, includeAlbumArt: Boolean): AudioMetadata? {
        // Get file descriptor - use dup.detachFd to give TagLib its own copy
        // TagLib will close its copy, we close our ParcelFileDescriptor
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val fdForTagLib = pfd.dup().detachFd()
        
        // Read metadata using TagLib - TagLib takes ownership and closes its copy
        val metadata = try {
            TagLib.getMetadata(fdForTagLib, readPictures = includeAlbumArt)
        } catch (e: Exception) {
            Log.w(TAG, "TagLib.getMetadata failed", e)
            null
        }

        pfd.close()

        if (metadata == null) {
            Log.w(TAG, "Failed to read metadata: ${file.absolutePath}")
            return null
        }

        val propertyMap = metadata.propertyMap

        // Read custom fields
        val customFields = mutableMapOf<String, String>()
        propertyMap[CUSTOM_RECORD_LABEL]?.firstOrNull()?.let { customFields[CUSTOM_RECORD_LABEL] = it }
        propertyMap[CUSTOM_ENCODER]?.firstOrNull()?.let { customFields[CUSTOM_ENCODER] = it }
        propertyMap[CUSTOM_ISRC]?.firstOrNull()?.let { customFields[CUSTOM_ISRC] = it }
        propertyMap[CUSTOM_COPYRIGHT]?.firstOrNull()?.let { customFields[CUSTOM_COPYRIGHT] = it }
        
        // Read ReplayGain fields
        propertyMap[CUSTOM_REPLAYGAIN_TRACK_GAIN]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN] = it }
        propertyMap[CUSTOM_REPLAYGAIN_TRACK_PEAK]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK] = it }
        propertyMap[CUSTOM_REPLAYGAIN_ALBUM_GAIN]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = it }
        propertyMap[CUSTOM_REPLAYGAIN_ALBUM_PEAK]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = it }

        // Get album art from pictures
        val albumArt = if (includeAlbumArt) {
            try {
                metadata.pictures.firstOrNull()?.data
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get album art: ${file.absolutePath}", e)
                null
            }
        } else null

        return AudioMetadata(
            title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() } 
                ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            trackNumber = propertyMap["TRACK"]?.firstOrNull()?.toIntOrNull(),
            totalTracks = propertyMap["TRACKTOTAL"]?.firstOrNull()?.toIntOrNull() 
                ?: propertyMap["TOTALTRACKS"]?.firstOrNull()?.toIntOrNull(),
            discNumber = propertyMap["DISCNUMBER"]?.firstOrNull()?.toIntOrNull(),
            totalDiscs = propertyMap["DISCTOTAL"]?.firstOrNull()?.toIntOrNull() 
                ?: propertyMap["TOTALDISCS"]?.firstOrNull()?.toIntOrNull(),
            composer = propertyMap["COMPOSER"]?.firstOrNull()?.takeIf { it.isNotBlank() } 
                ?: propertyMap["AUTHOR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            lyricist = propertyMap["LYRICIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            conductor = propertyMap["CONDUCTOR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            originalArtist = propertyMap["ORIGINALARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            comment = propertyMap["COMMENT"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            lyrics = propertyMap["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            albumArt = albumArt,
            customFields = customFields
        )
    }

    /**
     * Updates metadata for an audio file.
     * Uses SAF (Storage Access Framework) for external storage, direct access for internal storage.
     * @param filePath Path to the audio file
     * @param metadata Metadata to write
     * @return Result.success() if successful
     */
    suspend fun updateMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            // First try SAF approach for external storage
            if (isExternalStorage(filePath)) {
                val safResult = tryUpdateMetadataViaSaf(filePath, metadata)
                if (safResult.isSuccess) {
                    Log.d(TAG, "SAF write successful: $filePath")
                    return@withContext safResult
                }
                Log.w(TAG, "SAF write failed, trying direct access: $filePath", safResult.exceptionOrNull())
            }

            // Direct file access for internal storage
            try {
                val result = updateMetadataDirect(filePath, metadata)
                if (result.isSuccess) {
                    Log.d(TAG, "Direct write successful: $filePath")
                }
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Direct write failed: $filePath", e)
                return@withContext Result.failure(e)
            }
        }

    /**
     * Updates metadata using direct file access (for internal storage)
     */
    private fun updateMetadataDirect(filePath: String, metadata: AudioMetadata): Result<Unit> {
        return try {
            // Normalize the file path first
            val normalizedPath = normalizeFilePath(filePath)
            var file = File(normalizedPath)
            
            if (!file.exists()) {
                // Try alternative path resolution
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    file = File(resolvedPath)
                }
                if (!file.exists()) {
                    return Result.failure(IllegalStateException("File does not exist: $filePath"))
                }
            }

            // Get file descriptor for writing - use MODE_READ_WRITE as TagLib needs to read while modifying
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
            val fdForTagLib = pfd.dup().detachFd()

            // Build properties map - TagLib uses Map<String, Array<String>>
            val properties = java.util.HashMap<String, Array<String>>()
            
            metadata.title?.let { properties["TITLE"] = arrayOf(it) }
            metadata.artist?.let { properties["ARTIST"] = arrayOf(it) }
            metadata.album?.let { properties["ALBUM"] = arrayOf(it) }
            metadata.year?.let { properties["DATE"] = arrayOf(it) }
            metadata.genre?.let { properties["GENRE"] = arrayOf(it) }
            metadata.trackNumber?.let { properties["TRACK"] = arrayOf(it.toString()) }
            metadata.comment?.let { properties["COMMENT"] = arrayOf(it) }

            metadata.albumArtist?.let { properties["ALBUMARTIST"] = arrayOf(it) }
            metadata.discNumber?.let { properties["DISCNUMBER"] = arrayOf(it.toString()) }
            metadata.composer?.let { properties["COMPOSER"] = arrayOf(it) }

            metadata.customFields[CUSTOM_RECORD_LABEL]?.let { properties[CUSTOM_RECORD_LABEL] = arrayOf(it) }
            metadata.customFields[CUSTOM_ENCODER]?.let { properties[CUSTOM_ENCODER] = arrayOf(it) }
            metadata.customFields[CUSTOM_ISRC]?.let { properties[CUSTOM_ISRC] = arrayOf(it) }
            metadata.customFields[CUSTOM_COPYRIGHT]?.let { properties[CUSTOM_COPYRIGHT] = arrayOf(it) }

            // Write metadata using TagLib - TagLib takes ownership and closes its copy
            val success = TagLib.savePropertyMap(fdForTagLib, properties)
            
            pfd.close()

            if (!success) {
                return Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update metadata directly: $filePath", e)
            Result.failure(e)
        }
    }

    /**
     * Updates metadata using SAF (Storage Access Framework)
     * This is required for Android 11+ scoped storage
     */
    private suspend fun tryUpdateMetadataViaSaf(
        filePath: String,
        metadata: AudioMetadata
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Find valid persisted URI permission
        val validPermission = findValidPermission(filePath)
        if (validPermission == null) {
            return@withContext Result.failure(
                IllegalStateException("No SAF write permission for: $filePath")
            )
        }

        // Get document URI
        val relativePath = getRelativePath(filePath, validPermission)
        val targetDocUri = findDocumentUriInTree(validPermission.uri, relativePath)
        if (targetDocUri == null) {
            return@withContext Result.failure(
                IllegalStateException("Cannot find document URI for: $filePath")
            )
        }

        Log.d(TAG, "Found document URI: $targetDocUri for file: $filePath")

        // Create temp file for editing
        val fileExtension = File(filePath).extension.lowercase()
        val sourceExt = if (fileExtension.isNotBlank()) ".$fileExtension" else ".audio"
        val tempFile = try {
            File.createTempFile("voxly-edit-", sourceExt, context.cacheDir)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        // Do the SAF update
        val result = doSafUpdate(targetDocUri, tempFile, metadata, validPermission, fileExtension, filePath)
        tempFile.delete()
        result
    }

    /**
     * Performs the actual SAF update operation
     */
    private suspend fun doSafUpdate(
        targetDocUri: Uri,
        tempFile: File,
        metadata: AudioMetadata,
        validPermission: android.content.UriPermission,
        fileExtension: String,
        filePath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Read original file via SAF
            val sourceStream = context.contentResolver.openInputStream(targetDocUri)
            if (sourceStream == null) {
                return@withContext Result.failure(
                    IllegalStateException("Unable to open source file")
                )
            }

            sourceStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Edit metadata using TagLib on temp file - use MODE_READ_WRITE as TagLib may need to read while modifying
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
            val fdForTagLib = pfd.dup().detachFd()

            // Build properties map
            val properties = java.util.HashMap<String, Array<String>>()
            metadata.title?.let { properties["TITLE"] = arrayOf(it) }
            metadata.artist?.let { properties["ARTIST"] = arrayOf(it) }
            metadata.album?.let { properties["ALBUM"] = arrayOf(it) }
            metadata.year?.let { properties["DATE"] = arrayOf(it) }
            metadata.genre?.let { properties["GENRE"] = arrayOf(it) }
            metadata.trackNumber?.let { properties["TRACK"] = arrayOf(it.toString()) }
            metadata.comment?.let { properties["COMMENT"] = arrayOf(it) }

            metadata.albumArtist?.let { properties["ALBUMARTIST"] = arrayOf(it) }
            metadata.discNumber?.let { properties["DISCNUMBER"] = arrayOf(it.toString()) }
            metadata.composer?.let { properties["COMPOSER"] = arrayOf(it) }
            
            metadata.customFields[CUSTOM_RECORD_LABEL]?.let { properties[CUSTOM_RECORD_LABEL] = arrayOf(it) }
            metadata.customFields[CUSTOM_ENCODER]?.let { properties[CUSTOM_ENCODER] = arrayOf(it) }
            metadata.customFields[CUSTOM_ISRC]?.let { properties[CUSTOM_ISRC] = arrayOf(it) }
            metadata.customFields[CUSTOM_COPYRIGHT]?.let { properties[CUSTOM_COPYRIGHT] = arrayOf(it) }

            // TagLib takes ownership and closes its copy
            val success = TagLib.savePropertyMap(fdForTagLib, properties)
            pfd.close()

            if (!success) {
                return@withContext Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            // Delete original and create new (required for some SAF providers)
            try {
                DocumentsContract.deleteDocument(context.contentResolver, targetDocUri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete document, trying overwrite", e)
            }

            // Write modified content to new document
            val mimeType = getMimeType(fileExtension)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                validPermission.uri,
                DocumentsContract.getTreeDocumentId(validPermission.uri)
            )
            
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mimeType,
                File(filePath).name
            )

            val targetUri = newDocUri ?: targetDocUri
            
            // Write to output stream
            val outputStream = try {
                context.contentResolver.openOutputStream(targetUri, "wt")
            } catch (e: Exception) {
                Log.w(TAG, "Failed with 'wt', trying 'w'", e)
                try {
                    context.contentResolver.openOutputStream(targetUri, "w")
                } catch (e2: Exception) {
                    Log.w(TAG, "Failed with 'w', trying default", e2)
                    context.contentResolver.openOutputStream(targetUri)
                }
            }

            if (outputStream == null) {
                return@withContext Result.failure(
                    IllegalStateException("Unable to open output stream for SAF write")
                )
            }

            outputStream.use { output ->
                tempFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Give system time to persist
            kotlinx.coroutines.delay(500)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "SAF write operation failed: $filePath", e)
            Result.failure(e)
        }
    }

    /**
     * Extracts album art from an audio file
     */
    suspend fun extractAlbumArt(filePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // Normalize the file path first
            val normalizedPath = normalizeFilePath(filePath)
            var file = File(normalizedPath)
            
            if (!file.exists()) {
                // Try alternative path resolution
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    file = File(resolvedPath)
                }
                if (!file.exists()) return@withContext null
            }

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val fdForTagLib = pfd.dup().detachFd()
            
            // TagLib takes ownership and closes its copy
            val pictures = TagLib.getPictures(fdForTagLib)
            pfd.close()
            
            pictures.firstOrNull()?.data
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract album art: $filePath", e)
            null
        }
    }

    /**
     * Reads audio technical information
     */
    suspend fun readAudioInfo(filePath: String): AudioInfo? = withContext(Dispatchers.IO) {
        try {
            // Normalize the file path first
            val normalizedPath = normalizeFilePath(filePath)
            var file = File(normalizedPath)
            
            if (!file.exists()) {
                // Try alternative path resolution
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    file = File(resolvedPath)
                }
                if (!file.exists()) return@withContext null
            }

            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val fdForTagLib = pfd.dup().detachFd()
            
            // TagLib takes ownership and closes its copy
            val audioProperties = TagLib.getAudioProperties(fdForTagLib)
            pfd.close()

            if (audioProperties == null) {
                return@withContext null
            }

            AudioInfo(
                bitrate = audioProperties.bitrate,
                sampleRate = audioProperties.sampleRate,
                channels = audioProperties.channels,
                durationMs = audioProperties.length.toLong() * 1000
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read audio info: $filePath", e)
            null
        }
    }

    /**
     * Checks if a file format is supported
     */
    suspend fun isFormatSupported(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        extension in SUPPORTED_EXTENSIONS
    }

    // ===== Helper Methods =====

    /**
     * Checks if file is on external storage
     */
    private fun isExternalStorage(filePath: String): Boolean {
        return filePath.startsWith("/storage/emulated/") ||
            filePath.startsWith("/sdcard/") ||
            filePath.startsWith("/storage/")
    }

    /**
     * Finds a valid persisted URI permission for the given file path
     */
    private fun findValidPermission(filePath: String): android.content.UriPermission? {
        val normalizedPath = runCatching { File(filePath).canonicalPath }.getOrDefault(filePath)
        
        val permissions = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }

        for (perm in permissions) {
            val treePath = mapTreeUriToPath(perm.uri) ?: continue
            val normalizedTree = treePath.trimEnd('/')
            
            if (normalizedPath.startsWith("$normalizedTree/") || normalizedPath == normalizedTree) {
                return perm
            }
        }
        return null
    }

    /**
     * Gets relative path from file path and permission
     */
    private fun getRelativePath(filePath: String, permission: android.content.UriPermission): String {
        val treePath = mapTreeUriToPath(permission.uri) ?: return filePath
        val normalizedTree = treePath.trimEnd('/')
        return if (filePath.startsWith("$normalizedTree/")) {
            filePath.removePrefix("$normalizedTree/")
        } else {
            File(filePath).name
        }
    }

    /**
     * Maps tree URI to file system path
     */
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
            else -> if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
        }
    }

    /**
     * Finds document URI in tree
     */
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
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == segment) {
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
     * Gets MIME type for file extension
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "ape" -> "audio/x-ape"
            "wv" -> "audio/x-wavpack"
            else -> "application/octet-stream"
        }
    }
}
