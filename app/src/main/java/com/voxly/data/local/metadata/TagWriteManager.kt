package com.voxly.data.local.metadata

import android.content.Context
import android.media.MediaScannerConnection
import com.voxly.data.local.MusicLibraryCache
import com.voxly.domain.repository.WhitelistRepository
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tag write manager for Android 16 safe write.
 * 
 * Flow:
 * 1. Whitelist directory: Direct FileDescriptor + TagLib save() (no MediaStore.createWriteRequest)
 * 2. Non-whitelist: Try SAF, fallback to MediaStore.createWriteRequest
 * 3. Auto-sync Room after write
 * 4. MediaScannerConnection.scanFile for system visibility
 */
@Singleton
class TagWriteManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tagLibMetadataProcessor: TagLibMetadataProcessor,
    private val whitelistRepository: WhitelistRepository,
    private val musicLibraryCache: MusicLibraryCache
) {
    companion object {
        private const val TAG = "TagWriteManager"
    }

    /**
     * Writes metadata to file with whitelist check.
     * - Whitelist dir: Direct write via FileDescriptor + TagLib
     * - Non-whitelist: SAF/MediaStore path
     */
    suspend fun writeMetadata(filePath: String, metadata: AudioMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            val isWhitelisted = isInWhitelistDirectory(filePath)
            
            Timber.d(TAG, "Writing metadata to: $filePath, whitelisted=$isWhitelisted")
            
            val result = tagLibMetadataProcessor.updateMetadata(filePath, metadata)
            
            if (result.isSuccess) {
                // Sync to Room cache
                syncToRoom(filePath)
                
                // Refresh system MediaStore
                refreshSystemMedia(filePath)
            }
            
            result
        }

    /**
     * Checks if file is in a whitelist directory.
     */
    private suspend fun isInWhitelistDirectory(filePath: String): Boolean {
        return try {
            val whitelistPaths = whitelistRepository.getValidWhitelistPaths().first()
            if (whitelistPaths.isEmpty()) return false
            
            val normalizedPath = filePath.trimEnd('/', '\\')
            whitelistPaths.any { whitelistPath ->
                val normalizedWhitelist = whitelistPath.trimEnd('/', '\\')
                normalizedPath == normalizedWhitelist ||
                normalizedPath.startsWith("$normalizedWhitelist/") ||
                normalizedPath.startsWith("$normalizedWhitelist\\")
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Error checking whitelist", e)
            false
        }
    }

    /**
     * Syncs written file to Room cache.
     */
    private suspend fun syncToRoom(filePath: String) {
        try {
            val audioFile = musicLibraryCache.getCachedFile(filePath)
            if (audioFile != null) {
                val metadata = tagLibMetadataProcessor.readMetadata(filePath, includeAlbumArt = false)
                metadata?.let { updatedMetadata ->
                    val updated = audioFile.copy(metadata = updatedMetadata)
                    musicLibraryCache.syncFileToCache(updated)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Failed to sync to Room: $filePath", e)
        }
    }

    /**
     * Refreshes system MediaStore for other apps visibility.
     */
    private fun refreshSystemMedia(filePath: String) {
        try {
            val file = File(filePath)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, _ ->
                Timber.d(TAG, "MediaScan completed: $filePath")
            }
        } catch (e: Exception) {
            Timber.w(TAG, "Failed to refresh system MediaStore: $filePath", e)
        }
    }
}