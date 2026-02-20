package com.voxly.data.local.metadata

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
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
                readMetadataFromMediaStore(filePath, includeAlbumArt)?.let { return@withContext it }

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

        // Helper function to find property key case-insensitively (for FLAC/Vorbis Comments compatibility)
        fun findKeyIgnoreCase(map: Map<String, Array<String>>, targetKey: String): String? {
            val lowerTarget = targetKey.lowercase()
            return map.keys.find { it.lowercase() == lowerTarget }
        }

        // Read custom fields
        val customFields = mutableMapOf<String, String>()
        propertyMap[CUSTOM_RECORD_LABEL]?.firstOrNull()?.let { customFields[CUSTOM_RECORD_LABEL] = it }
        propertyMap[CUSTOM_ENCODER]?.firstOrNull()?.let { customFields[CUSTOM_ENCODER] = it }
        propertyMap[CUSTOM_ISRC]?.firstOrNull()?.let { customFields[CUSTOM_ISRC] = it }
        propertyMap[CUSTOM_COPYRIGHT]?.firstOrNull()?.let { customFields[CUSTOM_COPYRIGHT] = it }
        
        // Read ReplayGain fields (case-insensitive for FLAC/Vorbis Comments compatibility)
        findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_TRACK_GAIN)?.let { actualKey ->
            propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN] = it }
        }
        findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_TRACK_PEAK)?.let { actualKey ->
            propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK] = it }
        }
        findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_ALBUM_GAIN)?.let { actualKey ->
            propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = it }
        }
        findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_ALBUM_PEAK)?.let { actualKey ->
            propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = it }
        }

        // Get album art from pictures
        val albumArt = if (includeAlbumArt) {
            try {
                metadata.pictures.firstOrNull()?.data
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get album art: ${file.absolutePath}", e)
                null
            }
        } else null

        // Normalize track number: some sources incorrectly add 1000 offset
        // e.g., "1001" should be "1", "1012" should be "12"
        fun normalizeTrackNumber(track: Int): Int {
            return if (track > 1000 && track < 10000) {
                // Likely a corrupted value: 1001-9999 likely means track + 1000 offset
                // Check if removing 1000 gives a valid track number
                val normalized = track - 1000
                if (normalized in 1..999) normalized else track
            } else {
                track
            }
        }

        // Helper function to parse track field - handles both "1" and "1/10" formats
        // Also handles corrupted track values like "1001" which should be "1"
        fun parseTrackField(value: String?): Pair<Int?, Int?> {
            if (value.isNullOrBlank()) return Pair(null, null)
            
            // Handle ID3v2 format: "1/10" where 10 is total tracks
            return if (value.contains('/')) {
                val parts = value.split('/')
                val track = parts.getOrNull(0)?.toIntOrNull()?.let { normalizeTrackNumber(it) }
                val total = parts.getOrNull(1)?.toIntOrNull()
                Pair(track?.takeIf { it > 0 }, total?.takeIf { it > 0 })
            } else {
                val track = value.toIntOrNull()?.let { normalizeTrackNumber(it) }
                Pair(track?.takeIf { it > 0 && it <= 9999 }, null)
            }
        }

        // Read TRACK field (case-insensitive for FLAC/Vorbis Comments compatibility)
        val trackKey = findKeyIgnoreCase(propertyMap, "TRACK")
        val trackValue = trackKey?.let { propertyMap[it]?.firstOrNull() }
        val (parsedTrack, parsedTotalFromTrack) = parseTrackField(trackValue)

        return AudioMetadata(
            title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() } 
                ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            trackNumber = parsedTrack,
            totalTracks = parsedTotalFromTrack
                ?: findKeyIgnoreCase(propertyMap, "TRACKTOTAL")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } }
                ?: findKeyIgnoreCase(propertyMap, "TOTALTRACKS")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } },
            discNumber = findKeyIgnoreCase(propertyMap, "DISCNUMBER")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { d -> d > 0 } },
            totalDiscs = findKeyIgnoreCase(propertyMap, "DISCTOTAL")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } }
                ?: findKeyIgnoreCase(propertyMap, "TOTALDISCS")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } },
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

    private fun readMetadataFromMediaStore(filePath: String, includeAlbumArt: Boolean): AudioMetadata? {
        val mediaUri = queryMediaStoreUriByPath(filePath) ?: return null
        return runCatching {
            val pfd = context.contentResolver.openFileDescriptor(mediaUri, "r") ?: return@runCatching null
            val fdForTagLib = pfd.dup().detachFd()
            val metadata = try {
                TagLib.getMetadata(fdForTagLib, readPictures = includeAlbumArt)
            } finally {
                pfd.close()
            } ?: return@runCatching null

            val propertyMap = metadata.propertyMap
            
            // Helper function to find property key case-insensitively (for FLAC/Vorbis Comments compatibility)
            fun findKeyIgnoreCase(map: Map<String, Array<String>>, targetKey: String): String? {
                val lowerTarget = targetKey.lowercase()
                return map.keys.find { it.lowercase() == lowerTarget }
            }
            
            val customFields = mutableMapOf<String, String>()
            propertyMap[CUSTOM_RECORD_LABEL]?.firstOrNull()?.let { customFields[CUSTOM_RECORD_LABEL] = it }
            propertyMap[CUSTOM_ENCODER]?.firstOrNull()?.let { customFields[CUSTOM_ENCODER] = it }
            propertyMap[CUSTOM_ISRC]?.firstOrNull()?.let { customFields[CUSTOM_ISRC] = it }
            propertyMap[CUSTOM_COPYRIGHT]?.firstOrNull()?.let { customFields[CUSTOM_COPYRIGHT] = it }
            
            // Read ReplayGain fields (case-insensitive for FLAC/Vorbis Comments compatibility)
            findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_TRACK_GAIN)?.let { actualKey ->
                propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN] = it }
            }
            findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_TRACK_PEAK)?.let { actualKey ->
                propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK] = it }
            }
            findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_ALBUM_GAIN)?.let { actualKey ->
                propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = it }
            }
            findKeyIgnoreCase(propertyMap, CUSTOM_REPLAYGAIN_ALBUM_PEAK)?.let { actualKey ->
                propertyMap[actualKey]?.firstOrNull()?.let { customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = it }
            }

            // Helper function to parse track field - handles both "1" and "1/10" formats
            fun parseTrackField(value: String?): Pair<Int?, Int?> {
                if (value.isNullOrBlank()) return Pair(null, null)
                // Handle ID3v2 format: "1/10" where 10 is total tracks
                return if (value.contains('/')) {
                    val parts = value.split('/')
                    val track = parts.getOrNull(0)?.toIntOrNull()
                    val total = parts.getOrNull(1)?.toIntOrNull()
                    Pair(track?.takeIf { it > 0 }, total?.takeIf { it > 0 })
                } else {
                    val track = value.toIntOrNull()
                    Pair(track?.takeIf { it > 0 && it <= 9999 }, null)
                }
            }

            // Read TRACK field (case-insensitive for FLAC/Vorbis Comments compatibility)
            val trackKey = findKeyIgnoreCase(propertyMap, "TRACK")
            val trackValue = trackKey?.let { propertyMap[it]?.firstOrNull() }
            val (parsedTrack, parsedTotalFromTrack) = parseTrackField(trackValue)

            AudioMetadata(
                title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                trackNumber = parsedTrack,
                totalTracks = parsedTotalFromTrack
                    ?: findKeyIgnoreCase(propertyMap, "TRACKTOTAL")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } }
                    ?: findKeyIgnoreCase(propertyMap, "TOTALTRACKS")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } },
                discNumber = findKeyIgnoreCase(propertyMap, "DISCNUMBER")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { d -> d > 0 } },
                totalDiscs = findKeyIgnoreCase(propertyMap, "DISCTOTAL")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } }
                    ?: findKeyIgnoreCase(propertyMap, "TOTALDISCS")?.let { propertyMap[it]?.firstOrNull()?.toIntOrNull()?.takeIf { t -> t > 0 && t <= 9999 } },
                composer = propertyMap["COMPOSER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["AUTHOR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                lyricist = propertyMap["LYRICIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                conductor = propertyMap["CONDUCTOR"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                originalArtist = propertyMap["ORIGINALARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                comment = propertyMap["COMMENT"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                lyrics = propertyMap["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                albumArt = if (includeAlbumArt) metadata.pictures.firstOrNull()?.data else null,
                customFields = customFields
            )
        }.onFailure {
            Log.w(TAG, "Failed to read metadata from MediaStore URI for: $filePath", it)
        }.getOrNull()
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
                Log.w(
                    TAG,
                    "SAF write failed, trying direct access: $filePath, reason=${safResult.exceptionOrNull()?.message}",
                    safResult.exceptionOrNull()
                )
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
                val mediaStoreUpdate = updateMetadataViaMediaStoreUri(filePath, metadata)
                if (mediaStoreUpdate.isSuccess) {
                    return mediaStoreUpdate
                }

                // Try alternative path resolution
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    file = File(resolvedPath)
                }
                if (!file.exists()) {
                    return Result.failure(
                        IllegalStateException(
                            "Cannot access file path directly. Grant directory write access via SAF and retry: $filePath"
                        )
                    )
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
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_PEAK] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = arrayOf(it)
            }

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

    private fun updateMetadataViaMediaStoreUri(filePath: String, metadata: AudioMetadata): Result<Unit> {
        return runCatching {
            val mediaUri = queryMediaStoreUriByPath(filePath)
                ?: return Result.failure(IllegalStateException("No MediaStore URI for: $filePath"))
            val pfd = context.contentResolver.openFileDescriptor(mediaUri, "rw")
                ?: return Result.failure(IllegalStateException("Cannot open MediaStore file descriptor"))
            val fdForTagLib = pfd.dup().detachFd()

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
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_PEAK] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = arrayOf(it)
            }

            val success = try {
                TagLib.savePropertyMap(fdForTagLib, properties)
            } finally {
                pfd.close()
            }
            if (!success) {
                return Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }
            Result.success(Unit)
        }.getOrElse { e ->
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
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_TRACK_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_TRACK_PEAK] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_GAIN]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_GAIN] = arrayOf(it)
            }
            metadata.customFields[CUSTOM_REPLAYGAIN_ALBUM_PEAK]?.let {
                properties[CUSTOM_REPLAYGAIN_ALBUM_PEAK] = arrayOf(it)
            }

            // TagLib takes ownership and closes its copy
            val success = TagLib.savePropertyMap(fdForTagLib, properties)
            pfd.close()

            if (!success) {
                return@withContext Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            // Prefer in-place overwrite for better compatibility with special filenames.
            val overwriteResult = runCatching {
                val stream = openWritableOutputStream(targetDocUri)
                    ?: throw IllegalStateException("Unable to open output stream for target document")
                stream.use { output ->
                    tempFile.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
            }
            if (overwriteResult.isSuccess) {
                return@withContext Result.success(Unit)
            }
            Log.w(
                TAG,
                "In-place SAF overwrite failed for: $filePath, fallback to recreate",
                overwriteResult.exceptionOrNull()
            )

            // Fallback: delete and recreate in the same parent directory.
            val targetDocId = runCatching { DocumentsContract.getDocumentId(targetDocUri) }.getOrNull()
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot resolve target document id for: $filePath")
                )
            val parentDocId = targetDocId.substringBeforeLast('/', missingDelimiterValue = "")
            val parentUri = if (parentDocId.isNotBlank()) {
                DocumentsContract.buildDocumentUriUsingTree(validPermission.uri, parentDocId)
            } else {
                DocumentsContract.buildDocumentUriUsingTree(
                    validPermission.uri,
                    DocumentsContract.getTreeDocumentId(validPermission.uri)
                )
            }

            runCatching { DocumentsContract.deleteDocument(context.contentResolver, targetDocUri) }
                .onFailure { Log.w(TAG, "Failed to delete target document for fallback recreate: $filePath", it) }

            val mimeType = getMimeType(fileExtension)
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                mimeType,
                File(filePath).name
            ) ?: return@withContext Result.failure(
                IllegalStateException("Failed to recreate document for: $filePath")
            )

            val outputStream = openWritableOutputStream(newDocUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Unable to open output stream for recreated document")
                )

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

    private fun openWritableOutputStream(uri: Uri) = try {
        context.contentResolver.openOutputStream(uri, "rwt")
            ?: context.contentResolver.openOutputStream(uri, "wt")
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: context.contentResolver.openOutputStream(uri)
    } catch (e: Exception) {
        null
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
                val mediaUri = queryMediaStoreUriByPath(filePath)
                if (mediaUri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(mediaUri, "r")
                    if (pfd != null) {
                        val fdForTagLib = pfd.dup().detachFd()
                        val pictures = try {
                            TagLib.getPictures(fdForTagLib)
                        } finally {
                            pfd.close()
                        }
                        return@withContext pictures.firstOrNull()?.data
                    }
                }

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
                val mediaUri = queryMediaStoreUriByPath(filePath)
                if (mediaUri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(mediaUri, "r")
                    if (pfd != null) {
                        val fdForTagLib = pfd.dup().detachFd()
                        val audioProperties = try {
                            TagLib.getAudioProperties(fdForTagLib)
                        } finally {
                            pfd.close()
                        }
                        if (audioProperties != null) {
                            return@withContext AudioInfo(
                                bitrate = audioProperties.bitrate,
                                sampleRate = audioProperties.sampleRate,
                                channels = audioProperties.channels,
                                durationMs = audioProperties.length.toLong() * 1000
                            )
                        }
                    }
                }

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
            volume.equals("home", ignoreCase = true) -> {
                val root = "/storage/emulated/0/Documents"
                if (relative.isEmpty()) root else "$root/$relative"
            }
            else -> if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
        }
    }

    private fun queryMediaStoreUriByPath(filePath: String): Uri? {
        return runCatching {
            fun queryBySelection(selection: String, selectionArgs: Array<String>): Uri? {
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    selection,
                    selectionArgs,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
                return null
            }

            // Legacy fast path
            queryBySelection(
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(filePath)
            )?.let { return@runCatching it }

            // Scoped-storage friendly fallback
            val file = File(filePath)
            val fileName = file.name
            val relativePath = file.parentFile?.absolutePath
                ?.removePrefix("/storage/emulated/0/")
                ?.trim('/')
                ?.let { if (it.isBlank()) "" else "$it/" }
                ?: ""

            queryBySelection(
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(fileName, relativePath)
            )?.let { return@runCatching it }

            queryBySelection(
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )
        }.getOrNull()
    }

    /**
     * Finds document URI in tree
     */
    private fun findDocumentUriInTree(treeUri: Uri, relativePath: String): Uri? {
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (relativePath.isBlank()) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        }

        // Try direct document-id composition first (faster and more robust for special chars).
        val normalizedRelative = relativePath.trim('/').replace('\\', '/')
        val directDocId = "$rootDocId/$normalizedRelative"
        val directUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, directDocId)
        val directReadable = runCatching {
            context.contentResolver.openFileDescriptor(directUri, "r")?.use { }
            true
        }.getOrDefault(false)
        if (directReadable) {
            return directUri
        }
        
        var currentDocId = rootDocId
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            var nextDocId: String? = null
            val normalizedSegment = Normalizer.normalize(segment, Normalizer.Form.NFC)
            
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
                    val displayName = cursor.getString(nameIndex)
                    val normalizedName = Normalizer.normalize(displayName ?: "", Normalizer.Form.NFC)
                    if (normalizedName == normalizedSegment) {
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
