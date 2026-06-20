package com.voxly.data.local.metadata

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.voxly.data.local.MusicLibraryCache
import com.voxly.data.local.SafPermissionCache
import com.voxly.data.local.saf.SafWriteAccessService
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.parseMediaStoreTrackField
import com.voxly.data.local.metadata.lightweight.LightweightMetadataParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.Normalizer
import javax.inject.Inject
import com.voxly.core.util.Constants
import com.voxly.core.util.PathUtils
import com.voxly.presentation.ui.extractAndCacheCoverBytes
import javax.inject.Singleton

// Common base directories for path normalization
private val BASE_DIRECTORIES = listOf(
    Environment.getExternalStorageDirectory().absolutePath,
    "/storage/emulated/0",
    "/sdcard"
)

// Search directories for file resolution
private val SEARCH_DIRECTORIES = listOf(
    "${Environment.getExternalStorageDirectory()}/Music",
    "${Environment.getExternalStorageDirectory()}/Download",
    "${Environment.getExternalStorageDirectory()}/Downloads",
    "${Environment.getExternalStorageDirectory()}/Ringtones",
    "${Environment.getExternalStorageDirectory()}/Podcasts",
    "${Environment.getExternalStorageDirectory()}/Audiobooks",
    Environment.getExternalStorageDirectory().absolutePath,
    "/sdcard/Music",
    "/sdcard/Download",
    "/sdcard"
)

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
    @ApplicationContext private val context: Context,
    private val safPermissionCache: SafPermissionCache,
    private val safWriteAccessService: SafWriteAccessService,
    private val musicLibraryCache: MusicLibraryCache
) {
    // Memory cache for hot data (50 entries, ~2-5MB)
    private val memoryCache = LruCache<String, MetadataCacheEntry>(MEMORY_CACHE_SIZE)

    // Path resolution cache to avoid repeated file system searches
    private val pathResolutionCache = LruCache<String, String?>(PATH_CACHE_SIZE)

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

        // Memory cache for hot data (50 entries, ~2-5MB)
        private const val MEMORY_CACHE_SIZE = 50

        // Path resolution cache to avoid repeated file system searches
        private const val PATH_CACHE_SIZE = 100
    }

    /**
     * Cache entry for metadata + audio info
     */
    data class MetadataCacheEntry(
        val filePath: String,
        val lastModified: Long,
        val metadata: AudioMetadata,
        val audioInfo: AudioInfo?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MetadataCacheEntry
            return filePath == other.filePath && lastModified == other.lastModified
        }

        override fun hashCode(): Int {
            var result = filePath.hashCode()
            result = 31 * result + lastModified.hashCode()
            return result
        }
    }

    /**
     * Complete metadata result including all data from a single read operation
     */
    data class CompleteMetadata(
        val metadata: AudioMetadata,
        val audioInfo: AudioInfo?,
        val albumArt: ByteArray?
    ) {
        fun toCacheEntry(filePath: String, lastModified: Long): MetadataCacheEntry {
            return MetadataCacheEntry(
                filePath = filePath,
                lastModified = lastModified,
                metadata = metadata,
                audioInfo = audioInfo
                // albumArt is not stored in memory cache - read from file when needed
            )
        }
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

    // ==================== Cache Management ====================

    /**
     * Clears the memory cache. Call this when memory is low or after bulk operations.
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
        pathResolutionCache.evictAll()
        Timber.tag(TAG).d("Memory cache cleared")
    }

    /**
     * Clears both caches. Call this when the processor instance needs to be reset.
     */
    fun clearCache() {
        memoryCache.evictAll()
        pathResolutionCache.evictAll()
        Timber.tag(TAG).d("Cache cleared")
    }

    /**
     * Evicts cache entries for a single file. Should be called after a metadata write
     * so the next read returns the fresh data rather than the pre-write cached value.
     */
    fun invalidateFile(filePath: String) {
        val normalized = PathUtils.normalizeFilePath(filePath)
        memoryCache.remove(normalized)
        pathResolutionCache.remove(normalized)
    }

    /**
     * Gets cache statistics for debugging.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            memoryCacheHits = memoryCache.hitCount(),
            memoryCacheMisses = memoryCache.missCount(),
            pathCacheHits = pathResolutionCache.hitCount(),
            pathCacheMisses = pathResolutionCache.missCount()
        )
    }

    data class CacheStats(
        val memoryCacheHits: Int,
        val memoryCacheMisses: Int,
        val pathCacheHits: Int,
        val pathCacheMisses: Int
    )

    /**
     * Checks if cache entry is valid (file hasn't been modified).
     */
    private fun isCacheValid(cacheEntry: MetadataCacheEntry): Boolean {
        return try {
            val file = File(cacheEntry.filePath)
            file.exists() && file.lastModified() == cacheEntry.lastModified
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets cache key for a file path.
     */
    private fun getCacheKey(filePath: String): String {
        return PathUtils.normalizeFilePath(filePath)
    }

    /**
     * Puts entry into memory cache.
     */
    private fun putInMemoryCache(entry: MetadataCacheEntry) {
        memoryCache.put(getCacheKey(entry.filePath), entry)
    }

    /**
     * Gets entry from memory cache if valid.
     */
    private fun getFromMemoryCache(filePath: String): MetadataCacheEntry? {
        val key = getCacheKey(filePath)
        val entry = memoryCache.get(key)
        return if (entry != null && isCacheValid(entry)) {
            entry
        } else {
            if (entry != null) {
                // Entry exists but is stale, remove it
                memoryCache.remove(key)
            }
            null
        }
    }


    /**
     * Detects the MIME type of an image from its byte data.
     * @param imageBytes The first few bytes of the image data
     * @return The detected MIME type (e.g., "image/jpeg", "image/png", "image/webp")
     */
    private fun detectImageMimeType(imageBytes: ByteArray): String {
        return when {
            imageBytes.size >= 2 -> {
                when {
                    // JPEG: starts with FF D8
                    imageBytes[0].toInt() and 0xFF == 0xFF && imageBytes[1].toInt() and 0xFF == 0xD8 -> "image/jpeg"
                    // PNG: starts with 89 50 4E 47
                    imageBytes[0].toInt() and 0xFF == 0x89 && imageBytes[1].toInt() and 0xFF == 0x50 -> "image/png"
                    // GIF: starts with 47 49 46 38
                    imageBytes[0].toInt() and 0xFF == 0x47 && imageBytes[1].toInt() and 0xFF == 0x49 -> "image/gif"
                    // WebP: starts with 52 49 46 46 (RIFF) + 57 45 42 50 (WEBP)
                    imageBytes.size >= 12 && 
                        imageBytes[0].toInt() and 0xFF == 0x52 && imageBytes[1].toInt() and 0xFF == 0x49 &&
                        imageBytes[2].toInt() and 0xFF == 0x46 && imageBytes[3].toInt() and 0xFF == 0x46 &&
                        imageBytes[8].toInt() and 0xFF == 0x57 && imageBytes[9].toInt() and 0xFF == 0x45 &&
                        imageBytes[10].toInt() and 0xFF == 0x42 && imageBytes[11].toInt() and 0xFF == 0x50 -> "image/webp"
                    // BMP: starts with 42 4D
                    imageBytes[0].toInt() and 0xFF == 0x42 && imageBytes[1].toInt() and 0xFF == 0x4D -> "image/bmp"
                    else -> "image/jpeg" // Default to JPEG for compatibility
                }
            }
            else -> "image/jpeg" // Default to JPEG for compatibility
        }
    }

    /**
     * Builds TagLib properties map from AudioMetadata.
     */
    private fun buildPropertiesMap(metadata: AudioMetadata): java.util.HashMap<String, Array<String>> {
        val properties = java.util.HashMap<String, Array<String>>()

        metadata.title?.let { properties["TITLE"] = arrayOf(it) }
        metadata.artist?.let { properties["ARTIST"] = arrayOf(it) }
        metadata.album?.let { properties["ALBUM"] = arrayOf(it) }
        metadata.year?.let { properties["DATE"] = arrayOf(it) }
        metadata.genre?.let { properties["GENRE"] = arrayOf(it) }
        metadata.trackNumber?.let { properties["TRACKNUMBER"] = arrayOf(it.toString()) }
        metadata.comment?.let { properties["COMMENT"] = arrayOf(it) }
        metadata.lyrics?.let { properties["LYRICS"] = arrayOf(it) }

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

        return properties
    }

    /**
     * Reads complete metadata (metadata + audio info + album art) in a single operation.
     * This is the recommended method for loading all data at once.
     * Uses cache first (memory -> database -> file).
     * @param filePath Path to the audio file
     * @param includeAlbumArt Whether to include album art bytes
     * @param bypassCache If true, always reads from file (ignores all caches)
     * @return CompleteMetadata or null if reading fails
     */
    suspend fun readAllMetadata(
        filePath: String,
        includeAlbumArt: Boolean = true,
        bypassCache: Boolean = false
    ): CompleteMetadata? = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = PathUtils.normalizeFilePath(filePath)
            val file = File(normalizedPath)

            // Check memory cache first (fastest) - skip if bypassing cache
            if (!bypassCache) {
                getFromMemoryCache(normalizedPath)?.let { cached ->
                    Timber.tag(TAG).d("Memory cache hit for: $filePath")
                    val albumArt = if (includeAlbumArt) {
                        extractAndCacheCoverBytes(normalizedPath)
                    } else null
                    return@withContext CompleteMetadata(
                        metadata = cached.metadata,
                        audioInfo = cached.audioInfo,
                        albumArt = albumArt
                    )
                }

                // Check database cache
                val cachedFile = musicLibraryCache.getCachedFile(normalizedPath)
                if (cachedFile != null) {
                    val file = File(normalizedPath)
                    // Validate the cache against the file on disk: mtime must match.
                    // This prevents returning stale metadata when the file has been
                    // modified outside the app (e.g. by another tool or by the user).
                    val cachedEntity = musicLibraryCache.getCachedFileEntity(normalizedPath)
                    val mtimeValid = cachedEntity != null &&
                        file.exists() &&
                        cachedEntity.fileLastModifiedAt == file.lastModified()

                    // Check if file exists and hasn't been modified since cache
                    if (mtimeValid) {
                        val hasValidAudioInfo = cachedFile.sampleRate > 0 && cachedFile.duration > 0
                        // If we need album art, try cache first
                        // Otherwise use cached data (no file read needed)
                        if (hasValidAudioInfo && !includeAlbumArt) {
                            Timber.tag(TAG).d("Database cache hit for: $filePath")
                            val cachedMetadata = CompleteMetadata(
                                metadata = cachedFile.metadata,
                                audioInfo = AudioInfo(
                                    bitrate = cachedFile.bitrate * Constants.BPS_TO_KBPS, // Convert back to bps
                                    sampleRate = cachedFile.sampleRate,
                                    channels = cachedFile.channels,
                                    durationMs = cachedFile.duration
                                ),
                                albumArt = null // No album art needed
                            )
                            return@withContext cachedMetadata
                        } else if (hasValidAudioInfo) {
                            val cachedAlbumArt = extractAndCacheCoverBytes(normalizedPath)
                            if (cachedAlbumArt != null) {
                                Timber.tag(TAG).d("Album art cache hit for: $filePath")
                                val cachedMetadata = CompleteMetadata(
                                    metadata = cachedFile.metadata,
                                    audioInfo = AudioInfo(
                                        bitrate = cachedFile.bitrate * Constants.BPS_TO_KBPS,
                                        sampleRate = cachedFile.sampleRate,
                                        channels = cachedFile.channels,
                                        durationMs = cachedFile.duration
                                    ),
                                    albumArt = cachedAlbumArt
                                )
                                return@withContext cachedMetadata
                            }
                        }
                        // Fall through to read all data including album art
                    }
                }
            }

            // Cache miss - read from file
            val resolvedFile = if (file.exists()) {
                file
            } else {
                // Try alternative path resolution
                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) File(resolvedPath) else null
            }

            if (resolvedFile == null || !resolvedFile.exists()) {
                Timber.tag(TAG).w("File does not exist: $filePath")
                return@withContext null
            }

            // Read all data in one operation
            val completeMetadata = readAllFromFile(resolvedFile, includeAlbumArt)

            // Cache the result (without album art in memory cache)
            completeMetadata?.let { metadata ->
                val entry = metadata.toCacheEntry(resolvedFile.absolutePath, resolvedFile.lastModified())
                putInMemoryCache(entry)
                
                // OPTIMIZATION: Cache album art bytes for direct access
                metadata.albumArt?.let { artBytes ->
                    extractAndCacheCoverBytes(resolvedFile.absolutePath)
                }
            }

            completeMetadata
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation must always propagate.
            throw e
        } catch (e: SecurityException) {
            // Missing permission for SAF / MediaStore. Caller may prompt user for access.
            Timber.tag(TAG).w("SecurityException reading metadata: $filePath", e)
            null
        } catch (e: java.io.IOException) {
            // I/O failure (file removed, device error, etc.). Expected runtime.
            Timber.tag(TAG).w("IOException reading metadata: $filePath", e)
            null
        } catch (e: Exception) {
            // Programming error (NPE, IllegalState). Log with stack trace so it's
            // visible during development without silently passing as a null result.
            Timber.tag(TAG).e("Unexpected error reading metadata: $filePath", e)
            null
        }
    }

    /**
     * Reads all metadata from a file in a single operation.
     * Optimized: Reuses file descriptor to reduce file open operations by 50%.
     */
    private fun readAllFromFile(file: File, includeAlbumArt: Boolean): CompleteMetadata? {
        // OPTIMIZATION: Open file only once, then use dup() for multiple operations
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        return try {
            // First use: Read metadata and pictures
            val fdForMetadata = pfd.dup().detachFd()
            val taglibMetadata = TagLib.getMetadata(fdForMetadata, readPictures = includeAlbumArt)

            // Second use: Reuse same pfd to get audio properties (avoids reopening file)
            val fdForAudio = pfd.dup().detachFd()
            val audioProperties = try {
                TagLib.getAudioProperties(fdForAudio)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed to read audio properties", e)
                null
            }

            if (taglibMetadata == null) {
                Timber.tag(TAG).w("Failed to read metadata from: ${file.absolutePath}")
                return null
            }

            val metadata = parseTagLibMetadata(taglibMetadata, includeAlbumArt)
            val audioInfo = if (audioProperties != null && audioProperties.sampleRate > 0 && audioProperties.length > 0) {
                AudioInfo(
                    bitrate = audioProperties.bitrate,
                    sampleRate = audioProperties.sampleRate,
                    channels = audioProperties.channels,
                    durationMs = audioProperties.length.toLong()
                )
            } else {
                Timber.tag(TAG).w("TagLib returned invalid audio properties in readAllFromFile, setting audioInfo to null")
                null
            }

            CompleteMetadata(
                metadata = metadata,
                audioInfo = audioInfo,
                albumArt = if (includeAlbumArt) {
                    try {
                        taglibMetadata.pictures.firstOrNull()?.data
                    } catch (e: Exception) {
                        null
                    }
                } else null
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error reading from file: ${file.absolutePath}", e)
            null
        } finally {
            // Only close once - all dup() descriptors are owned by TagLib
            pfd.close()
        }
    }

    /**
     * Reads metadata from an audio file with caching support.
     * @param filePath Path to the audio file
     * @param includeAlbumArt Whether to include album art bytes
     * @return AudioMetadata object or null if reading fails
     */
    suspend fun readMetadata(
        filePath: String,
        includeAlbumArt: Boolean = true
    ): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            // Check memory cache first
            getFromMemoryCache(filePath)?.let { cached ->
                return@withContext cached.metadata
            }

            // Check database cache with mtime validation
            val cachedEntity = musicLibraryCache.getCachedFileEntity(filePath)
            if (cachedEntity != null) {
                val normalizedPath = PathUtils.normalizeFilePath(filePath)
                val file = File(normalizedPath)
                // Validate cache by comparing file mtime
                // If file was modified since cache, re-read from file to get fresh data
                if (file.exists() && cachedEntity.fileLastModifiedAt == file.lastModified()) {
                    return@withContext cachedEntity.toAudioFile().metadata
                }
            }

            // Try complete metadata read (will cache result)
            readAllMetadata(filePath, includeAlbumArt)?.let {
                return@withContext it.metadata
            }

            // Fallback to original logic
            val normalizedPath = PathUtils.normalizeFilePath(filePath)
            val file = File(normalizedPath)

            if (!file.exists()) {
                readMetadataFromMediaStore(filePath, includeAlbumArt)?.let { return@withContext it }

                val resolvedPath = resolveFilePath(filePath, file.name)
                if (resolvedPath != null) {
                    val resolvedFile = File(resolvedPath)
                    if (resolvedFile.exists()) {
                        return@withContext readMetadataFromFile(resolvedFile, includeAlbumArt)
                    }
                }
                Timber.tag(TAG).w("File does not exist: $filePath (normalized: $normalizedPath)")
                return@withContext null
            }

            readMetadataFromFile(file, includeAlbumArt)
        } catch (e: Exception) {
            Timber.tag(TAG).e("Failed to read metadata: $filePath", e)
            null
        }
    }

    /**
     * Reads metadata via MediaStore only.
     * This is a fallback for cases where TagLib cannot read the year.
     */
    suspend fun readMetadataFromMediaStoreOnly(filePath: String): AudioMetadata? =
        withContext(Dispatchers.IO) {
            readMetadataFromMediaStore(filePath, includeAlbumArt = false)
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
            // Extract relative path components
            val pathParts = originalPath
                .replace(Regex("//+"), "/")
                .trimStart('/')
                .split('/')
                .filter { it.isNotBlank() }

            for (baseDir in BASE_DIRECTORIES) {
                val candidatePath = "$baseDir/${pathParts.joinToString("/")}"
                if (File(candidatePath).exists()) {
                    return candidatePath
                }
            }

            // Strategy 3: Search in common directories for the file
            for (searchDir in SEARCH_DIRECTORIES) {
                val dir = File(searchDir)
                if (dir.exists() && dir.isDirectory) {
                    // Recursively search for the file (limited depth)
                    val found = searchForFile(dir, fileName, currentDepth = 0, maxDepth = 3)
                    if (found != null) return found.absolutePath
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w( "Path resolution failed for: $originalPath", e)
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
     * Parses TagLib metadata into AudioMetadata.
     * Extracted as a separate function to avoid code duplication.
     */
    private fun parseTagLibMetadata(
        metadata: com.kyant.taglib.Metadata,
        includeAlbumArt: Boolean
    ): AudioMetadata {
        val propertyMap = metadata.propertyMap

        // Helper function to find property key case-insensitively
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

        // Read ReplayGain fields
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

        // Normalize track number
        fun normalizeTrackNumber(track: Int): Int {
            val (normalized, _) = parseMediaStoreTrackField(track)
            return normalized ?: track
        }

        // Parse track field
        fun parseTrackField(value: String?): Pair<Int?, Int?> {
            if (value.isNullOrBlank()) return Pair(null, null)
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

        val trackKey = findKeyIgnoreCase(propertyMap, "TRACKNUMBER")
            ?: findKeyIgnoreCase(propertyMap, "TRACK")
        val trackValue = trackKey?.let { propertyMap[it]?.firstOrNull() }
        val (parsedTrack, parsedTotalFromTrack) = parseTrackField(trackValue)

        return AudioMetadata(
            title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
            year = findKeyIgnoreCase(propertyMap, "DATE")?.let { propertyMap[it]?.firstOrNull()?.takeIf { y -> y.isNotBlank() } }
                ?: findKeyIgnoreCase(propertyMap, "YEAR")?.let { propertyMap[it]?.firstOrNull()?.takeIf { y -> y.isNotBlank() } },
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
            albumArt = if (includeAlbumArt) {
                try {
                    metadata.pictures.firstOrNull()?.data
                } catch (e: Exception) {
                    null
                }
            } else null,
            customFields = customFields
        )
    }

    /**
     * Reads metadata from a File object.
     */
    private fun readMetadataFromFile(file: File, includeAlbumArt: Boolean): AudioMetadata? {
        // Get file descriptor - use dup.detachFd to give TagLib its own copy
        // TagLib will close its copy, we close our ParcelFileDescriptor
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val fdForTagLib = pfd.dup().detachFd()

            // Read metadata using TagLib - TagLib takes ownership and closes its copy
            val metadata = try {
                TagLib.getMetadata(fdForTagLib, readPictures = includeAlbumArt)
            } catch (e: Exception) {
                Timber.tag(TAG).w( "TagLib.getMetadata failed", e)
                try { ParcelFileDescriptor.adoptFd(fdForTagLib).close() } catch (_: Exception) {}
                null
            }

            if (metadata == null) {
                Timber.tag(TAG).w( "Failed to read metadata: ${file.absolutePath}")
                return null
            }

            return parseTagLibMetadata(metadata, includeAlbumArt)
        } finally {
            pfd.close()
        }
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

            parseTagLibMetadata(metadata, includeAlbumArt)
        }.onFailure {
            Timber.tag(TAG).w( "Failed to read metadata from MediaStore URI for: $filePath", it)
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
            val result = if (isExternalStorage(filePath)) {
                val safResult = tryUpdateMetadataViaSaf(filePath, metadata)
                if (safResult.isSuccess) {
                    Timber.d(TAG, "SAF write successful: $filePath")
                    return@withContext safResult
                }
                Timber.w(
                    TAG,
                    "SAF write failed, trying MediaStore: $filePath, reason=${safResult.exceptionOrNull()?.message}",
                    safResult.exceptionOrNull()
                )
                val mediaStoreResult = updateMetadataViaMediaStoreUri(filePath, metadata)
                if (mediaStoreResult.isSuccess) {
                    Timber.d(TAG, "MediaStore write successful: $filePath")
                    return@withContext mediaStoreResult
                }
                val errorMsg = "Cannot write to file: $filePath. " +
                    "Please add a working directory containing this file, " +
                    "or re-select the file through the file browser to grant write access."
                Timber.tag(TAG).e( errorMsg)
                Result.failure(
                    IllegalStateException(errorMsg)
                )
            } else {
                try {
                    val directResult = updateMetadataDirect(filePath, metadata)
                    if (directResult.isSuccess) {
                        Timber.d(TAG, "Direct write successful: $filePath")
                    }
                    directResult
                } catch (e: Exception) {
                    Timber.tag(TAG).e( "Direct write failed: $filePath", e)
                    Result.failure(e)
                }
            }

            if (result.isSuccess) {
                // Drop cached reads of this file so the next read goes to disk
                // and returns the post-write data.
                invalidateFile(filePath)
                // MediaStore refresh is performed by TagWriteManager (the single owner
                // of the write side-effect) to avoid duplicate scans.
            }
            result
        }

    /**
     * Updates metadata using direct file access (for internal storage)
     */
    private fun updateMetadataDirect(filePath: String, metadata: AudioMetadata): Result<Unit> {
        return try {
            // Normalize the file path first
            val normalizedPath = PathUtils.normalizeFilePath(filePath)
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
            val properties = buildPropertiesMap(metadata)

            // Write metadata using TagLib - TagLib takes ownership and closes its copy
            val success = TagLib.savePropertyMap(fdForTagLib, properties)
            
            // Save album art if provided
            metadata.albumArt?.let { albumArtBytes ->
                writeAlbumArt(fdForTagLib, albumArtBytes, filePath)
            }
            
            pfd.close()

            if (!success) {
                return Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e( "Failed to update metadata directly: $filePath", e)
            // Check for permission denied error
            val errorMessage = if (e.message?.contains("EACCES") == true ||
                e.message?.contains("Permission denied") == true) {
                "Write permission denied for: $filePath. The SAF permission may have expired. Please re-select the file or its parent directory through the file browser to restore write access."
            } else {
                e.message ?: "Failed to update metadata for: $filePath"
            }
            Result.failure(IllegalStateException(errorMessage, e))
        }
    }

    private fun updateMetadataViaMediaStoreUri(filePath: String, metadata: AudioMetadata): Result<Unit> {
        val mediaUri = queryMediaStoreUriByPath(filePath)
            ?: return Result.failure(IllegalStateException("No MediaStore URI for: $filePath"))
        
        var pfd: ParcelFileDescriptor? = null
        var fdForTagLib: Int = -1
        
        try {
            pfd = context.contentResolver.openFileDescriptor(mediaUri, "rw")
                ?: return Result.failure(IllegalStateException("Cannot open MediaStore file descriptor"))
            fdForTagLib = pfd.dup().detachFd()

            val properties = buildPropertiesMap(metadata)
            val success = TagLib.savePropertyMap(fdForTagLib, properties)
            
            if (!success) {
                return Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }
            
            // Save album art if provided
            metadata.albumArt?.let { albumArtBytes ->
                val pfd2 = context.contentResolver.openFileDescriptor(mediaUri, "rw")
                    ?: return Result.failure(IllegalStateException("Cannot reopen file descriptor for picture save"))
                val fdForTagLib2 = pfd2.dup().detachFd()
                try {
                    writeAlbumArt(fdForTagLib2, albumArtBytes, filePath)
                } finally {
                    try { pfd2.close() } catch (_: Exception) { /* ignore - best effort */ }
                }
            }

            Result.success(Unit)
        } catch (e: RecoverableSecurityException) {
            val intentSender = try {
                val method = e.userAction.javaClass.getMethod("getIntentSender")
                method.invoke(e.userAction) as? IntentSender
            } catch (_: Exception) {
                null
            }
            if (intentSender != null) {
                throw RecoverableMediaStoreException(
                    "MediaStore write permission denied for: $filePath. Please grant write access through the system permission dialog to edit this file.",
                    intentSender,
                    e
                )
            }
            return Result.failure(
                IllegalStateException(
                    "MediaStore write permission denied for: $filePath. Please grant write access through the system permission dialog to edit this file.",
                    e
                )
            )
        } catch (e: Exception) {
            val errorMessage = if (e.message?.contains("EACCES") == true || e.message?.contains("Permission denied") == true) {
                "Write permission denied for: $filePath. ${e.message ?: ""}"
            } else {
                e.message ?: "Failed to update metadata via MediaStore for: $filePath"
            }
            Result.failure(IllegalStateException(errorMessage, e))
        } finally {
            // Best-effort cleanup; file descriptor will be released by system
            try { pfd?.close() } catch (_: Exception) { /* ignore - best effort */ }
        }
        return Result.failure(IllegalStateException("Unexpected exit in updateMetadataViaMediaStoreUri"))
    }

    /**
     * Updates metadata using SAF (Storage Access Framework)
     * This is required for Android 11+ scoped storage
     */
    private suspend fun tryUpdateMetadataViaSaf(
        filePath: String,
        metadata: AudioMetadata
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val validPermission = safWriteAccessService.findValidWritePermission(filePath)
        if (validPermission == null) {
            val errorMsg = "SAF write permission expired or not granted for: $filePath. Please re-select the file or its parent directory through the file browser to restore write access."
            Timber.tag(TAG).e( errorMsg)
            return@withContext Result.failure(
                IllegalStateException(errorMsg)
            )
        }

        val targetDocUri = safWriteAccessService.resolveDocumentUri(filePath, validPermission)
        if (targetDocUri == null) {
            return@withContext Result.failure(
                IllegalStateException("Cannot find document URI for: $filePath. The file may have been moved or deleted.")
            )
        }

        Timber.d(TAG, "Found document URI: $targetDocUri for file: $filePath")

        // OPTIMIZATION: Try direct SAF write first (avoids temp file copy)
        val directResult = tryUpdateMetadataViaSafDirect(targetDocUri, metadata, filePath)
        if (directResult.isSuccess) {
            Timber.d(TAG, "Direct SAF write successful: $filePath")
            return@withContext directResult
        }
        Timber.w(
            TAG,
            "Direct SAF write failed, falling back to temp file: $filePath, reason=${directResult.exceptionOrNull()?.message}",
            directResult.exceptionOrNull()
        )

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
     * @param retryCount Internal parameter to limit recursion depth
     */
    private suspend fun doSafUpdate(
        targetDocUri: Uri,
        tempFile: File,
        metadata: AudioMetadata,
        validPermission: android.content.UriPermission,
        fileExtension: String,
        filePath: String,
        retryCount: Int = 0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Validate permission before starting the update (limit retries to prevent infinite recursion)
        if (retryCount < 2 && !safWriteAccessService.isPermissionValid(validPermission, filePath)) {
            Timber.tag(TAG).w( "doSafUpdate: Permission became invalid (retry $retryCount), attempting to reacquire")

            // Try to reacquire permission
            val newPermission = safWriteAccessService.findValidWritePermission(filePath)
            if (newPermission == null) {
                return@withContext Result.failure(
                    IllegalStateException("SAF permission no longer valid and could not be reacquired. Please re-select the file or its parent directory through the file browser to restore write access.")
                )
            }
            val newTargetDocUri = safWriteAccessService.resolveDocumentUri(filePath, newPermission)
            if (newTargetDocUri == null) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot find document URI after permission reacquisition.")
                )
            }

            // Continue with new permission (increment retry count)
            return@withContext doSafUpdate(newTargetDocUri, tempFile, metadata, newPermission, fileExtension, filePath, retryCount + 1)
        }

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
            val success: Boolean
            try {
                val fdForTagLib = pfd.dup().detachFd()

                // Build properties map
                val properties = buildPropertiesMap(metadata)

                // TagLib takes ownership and closes its copy
                success = TagLib.savePropertyMap(fdForTagLib, properties)
            } finally {
                pfd.close()
            }

            // Save album art if provided
            metadata.albumArt?.let { albumArtBytes ->
                val pfd2 = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)
                val fdForTagLib2 = pfd2.dup().detachFd()
                try {
                    writeAlbumArt(fdForTagLib2, albumArtBytes, filePath)
                } finally {
                    pfd2.close()
                }
            }

            if (!success) {
                return@withContext Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            // Prefer in-place overwrite for better compatibility with special filenames.
            val overwriteResult = runCatching {
                val stream = openWritableOutputStream(targetDocUri)
                    ?: throw IllegalStateException("Unable to open output stream for target document")
                stream.use { output ->
                    tempFile.inputStream().use { input ->
                        val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                        var bytesRead = input.read(buffer)
                        while (bytesRead >= 0) {
                            if (bytesRead > 0) {
                                output.write(buffer, 0, bytesRead)
                            }
                            bytesRead = input.read(buffer)
                        }
                        output.flush()
                    }
                }
            }
            if (overwriteResult.isSuccess) {
                return@withContext Result.success(Unit)
            }
            Timber.w(
                TAG,
                "In-place SAF overwrite failed for: $filePath, fallback to recreate",
                overwriteResult.exceptionOrNull()
            )

            val isTreePermission = runCatching { DocumentsContract.getTreeDocumentId(validPermission.uri) }.isSuccess
            if (!isTreePermission) {
                return@withContext Result.failure(
                    IllegalStateException("In-place SAF overwrite failed and fallback recreate requires directory permission. Please re-select the parent directory.")
                )
            }

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
                .onFailure { Timber.tag(TAG).w( "Failed to delete target document for fallback recreate: $filePath", it) }

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
                    val buffer = ByteArray(Constants.FILE_BUFFER_SIZE)
                    var bytesRead = input.read(buffer)
                    while (bytesRead >= 0) {
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                        bytesRead = input.read(buffer)
                    }
                    output.flush()
                }
            }

            // Give system time to persist
            kotlinx.coroutines.delay(500)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e( "SAF write operation failed: $filePath", e)
            Result.failure(e)
        }
    }

    /**
     * Attempts direct SAF write without temp file copy.
     */
    private fun tryUpdateMetadataViaSafDirect(
        targetDocUri: Uri,
        metadata: AudioMetadata,
        filePath: String
    ): Result<Unit> {
        return runCatching {
            val pfd = context.contentResolver.openFileDescriptor(targetDocUri, "rw")
                ?: return Result.failure(IllegalStateException("Cannot open SAF file descriptor"))
            val fdForTagLib = pfd.dup().detachFd()

            val properties = buildPropertiesMap(metadata)
            val success = try {
                TagLib.savePropertyMap(fdForTagLib, properties)
            } finally {
                pfd.close()
            }

            // Save album art if provided (requires new FD since TagLib closes its copy)
            metadata.albumArt?.let { albumArtBytes ->
                val pfd2 = context.contentResolver.openFileDescriptor(targetDocUri, "rw")
                    ?: return Result.failure(IllegalStateException("Cannot reopen SAF file descriptor for picture save"))
                val fdForTagLib2 = pfd2.dup().detachFd()
                try {
                    writeAlbumArt(fdForTagLib2, albumArtBytes, filePath)
                } finally {
                    pfd2.close()
                }
            }

            if (!success) {
                return Result.failure(IllegalStateException("TagLib.savePropertyMap() returned false"))
            }

            Result.success(Unit)
        }.getOrElse { e ->
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
            val normalizedPath = PathUtils.normalizeFilePath(filePath)
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
            try {
                val fdForTagLib = pfd.dup().detachFd()

                // TagLib takes ownership and closes its copy
                val pictures = TagLib.getPictures(fdForTagLib)

                pictures.firstOrNull()?.data
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w( "Failed to extract album art: $filePath", e)
            null
        }
    }

    /**
     * Reads audio technical information
     */
    suspend fun readAudioInfo(filePath: String): AudioInfo? = withContext(Dispatchers.IO) {
        try {
            // Normalize the file path first
            val normalizedPath = PathUtils.normalizeFilePath(filePath)
            var file = File(normalizedPath)
            
            if (!file.exists()) {
                val mediaUri = queryMediaStoreUriByPath(filePath)
                if (mediaUri != null) {
                    val pfd = context.contentResolver.openFileDescriptor(mediaUri, "r")
                    if (pfd != null) {
                        val fdForTagLib = pfd.dup().detachFd()
                        val audioProperties = try {
                            TagLib.getAudioProperties(fdForTagLib)
                        } catch (e: Exception) {
                            Timber.tag(TAG).w("Failed to read audio properties via MediaStore", e)
                            null
                        } finally {
                            pfd.close()
                        }
                        if (audioProperties != null && audioProperties.sampleRate > 0 && audioProperties.length > 0) {
                            return@withContext AudioInfo(
                                bitrate = audioProperties.bitrate,
                                sampleRate = audioProperties.sampleRate,
                                channels = audioProperties.channels,
                                durationMs = audioProperties.length.toLong()
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
            val audioProperties = try {
                TagLib.getAudioProperties(fdForTagLib)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Failed to read audio properties", e)
                null
            } finally {
                pfd.close()
            }

            if (audioProperties == null || audioProperties.sampleRate <= 0 || audioProperties.length <= 0) {
                Timber.tag(TAG).w("TagLib returned invalid audio properties, trying LightweightMetadataParser")
                val lightweightResult = try {
                    LightweightMetadataParser.parse(file)
                } catch (e: Exception) {
                    null
                }
                if (lightweightResult?.audioInfo != null) {
                    return@withContext lightweightResult.audioInfo
                }
                return@withContext null
            }

            AudioInfo(
                bitrate = audioProperties.bitrate,
                sampleRate = audioProperties.sampleRate,
                channels = audioProperties.channels,
                durationMs = audioProperties.length.toLong()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w( "Failed to read audio info: $filePath", e)
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

    /**
     * Writes embedded album art to a file via the given native file descriptor.
     * Centralizes the picture-construction logic that was previously repeated
     * across the four write paths (direct / MediaStore / SAF-direct / SAF-temp).
     */
    private fun writeAlbumArt(fd: Int, albumArtBytes: ByteArray, filePath: String) {
        val picture = Picture(
            data = albumArtBytes,
            description = "Front Cover",
            pictureType = "Front Cover",
            mimeType = detectImageMimeType(albumArtBytes)
        )
        val saved = TagLib.savePictures(fd, arrayOf(picture))
        if (!saved) {
            Timber.tag(TAG).w("Failed to save album art for: $filePath")
        }
    }

    /**
     * Resolves a file path to a MediaStore content URI. Used as a fallback for
     * files that are not directly accessible (e.g. inside an SD card volume)
     * but have been indexed by MediaStore.
     */
    private fun queryMediaStoreUriByPath(filePath: String): Uri? {
        return runCatching {
            val file = File(filePath)
            val fileName = file.name
            val relativePath = file.parentFile?.absolutePath
                ?.removePrefix("/storage/emulated/0/")
                ?.trim('/')
                ?.let { if (it.isBlank()) "" else "$it/" }
                ?: ""

            fun query(selection: String, args: Array<String>): Uri? {
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    selection,
                    args,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        return ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                }
                return null
            }

            query(
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(fileName, relativePath)
            ) ?: query(
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )
        }.getOrNull()
    }
}
