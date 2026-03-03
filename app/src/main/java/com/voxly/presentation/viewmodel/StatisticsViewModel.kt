package com.voxly.presentation.viewmodel

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.data.local.cache.CachedAudioFileEntity
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.data.repository.ArtistCacheRepository
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Normalizer
import javax.inject.Inject

/**
 * ViewModel for the Statistics screen.
 * Provides library statistics from Room database.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val recentEditsRepository: RecentEditsRepository,
    private val databaseProvider: MusicCacheDatabaseProvider,
    private val settingsDataStore: SettingsDataStore,
    private val artistCacheRepository: ArtistCacheRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
        // Listen to whitelist/blacklist settings changes and refresh statistics
        viewModelScope.launch {
            combine(
                settingsDataStore.whitelistEnabled,
                settingsDataStore.blacklistEnabled,
                settingsDataStore.selectedDirectoryUris,
                settingsDataStore.blacklistDirectoryUris
            ) { _, _, _, _ -> Unit }
                .collect {
                    // Settings changed, refresh statistics
                    loadStatistics()
                }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                _uiState.value = StatisticsUiState.Loading

                // Read whitelist and blacklist settings
                val whitelistEnabled = settingsDataStore.whitelistEnabled.first()
                val blacklistEnabled = settingsDataStore.blacklistEnabled.first()
                val whitelistUris = settingsDataStore.selectedDirectoryUris.first()
                val blacklistUris = settingsDataStore.blacklistDirectoryUris.first()

                // Convert URIs to paths
                val whitelistPaths = if (whitelistEnabled && whitelistUris.isNotEmpty()) {
                    whitelistUris.map { uri -> mapUriToPath(uri) }.filter { it.isNotBlank() }
                } else emptyList()

                val blacklistPaths = if (blacklistEnabled && blacklistUris.isNotEmpty()) {
                    blacklistUris.map { uri -> mapUriToPath(uri) }.filter { it.isNotBlank() }
                } else emptyList()

                // Build path filter query
                val db = withContext(Dispatchers.IO) {
                    databaseProvider.getDatabase()
                }
                val dao = db.audioFileDao()
                val whitelist = whitelistPaths.takeIf { it.isNotEmpty() }
                val blacklist = blacklistPaths.takeIf { it.isNotEmpty() }

                // Get real statistics from database (with optional path filtering)
                val totalFiles = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT COUNT(*) FROM cached_audio_files"
                )?.let { dao.getTotalFileCountFiltered(it) } ?: dao.getTotalFileCount()

                if (totalFiles == 0) {
                    _uiState.value = StatisticsUiState.Empty
                    return@launch
                }

                // Get real duration and size
                val totalDurationQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT COALESCE(SUM(duration), 0) FROM cached_audio_files"
                )
                val totalDurationMs = totalDurationQuery?.let { dao.getTotalDurationFiltered(it) } ?: dao.getTotalDuration()

                val totalSizeQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT COALESCE(SUM(size), 0) FROM cached_audio_files"
                )
                val totalSizeBytes = totalSizeQuery?.let { dao.getTotalSizeFiltered(it) } ?: dao.getTotalSize()

                // Format total duration
                val totalDurationFormatted = formatDuration(totalDurationMs)

                // Get format distribution
                val formatDistributionQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT format, COUNT(*) as count FROM cached_audio_files GROUP BY format ORDER BY count DESC"
                )
                val formatDistributionRaw = formatDistributionQuery?.let { dao.getFormatDistributionFiltered(it) } ?: dao.getFormatDistribution()
                val formatDistribution = formatDistributionRaw.associate { it.format to it.count }

                // Get top artists
                val topArtistsQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist,
                    "SELECT artist, COUNT(*) as count FROM cached_audio_files WHERE artist IS NOT NULL AND artist != '' GROUP BY artist ORDER BY count DESC",
                    limit = 10
                )
                val topArtistsRaw = topArtistsQuery?.let { dao.getTopArtistsFiltered(it) } ?: dao.getTopArtists(10)
                val topArtists = topArtistsRaw.map { it.artist to it.count }

                // Cache top artists data for navigation
                topArtistsRaw.take(5).forEach { artistCount ->
                    val files = dao.getAudioFilesByArtistOnce(artistCount.artist)
                    if (files.isNotEmpty()) {
                        val audioFiles = files.map { entity -> entity.toAudioFile() }
                        artistCacheRepository.cacheArtist(
                            com.voxly.data.repository.ArtistGroup(
                                name = artistCount.artist,
                                files = audioFiles
                            )
                        )
                    }
                }

                // Get top albums
                val topAlbumsQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist,
                    "SELECT album, artist, COUNT(*) as count FROM cached_audio_files WHERE album IS NOT NULL AND album != '' GROUP BY album, artist ORDER BY count DESC",
                    limit = 10
                )
                val topAlbumsRaw = topAlbumsQuery?.let { dao.getTopAlbumsFiltered(it) } ?: dao.getTopAlbums(10)
                val topAlbums = topAlbumsRaw.map { "${it.album}" to it.count }

                // Calculate total size formatted
                val totalSizeFormatted = formatSize(totalSizeBytes)

                // Get recent activity from recent edits
                val recentEdits = recentEditsRepository.getRecentEdits(limit = 1000).first()
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L
                val weekMs = 7 * dayMs
                val monthMs = 30 * dayMs

                val todayEdits = recentEdits.count { now - it.timestamp < dayMs }
                val weekEdits = recentEdits.count { now - it.timestamp < weekMs }
                val monthEdits = recentEdits.count { now - it.timestamp < monthMs }

                _uiState.value = StatisticsUiState.Success(
                    totalFiles = totalFiles,
                    totalDurationFormatted = totalDurationFormatted,
                    totalSizeFormatted = totalSizeFormatted,
                    editedFilesCount = recentEdits.distinctBy { it.filePath }.size,
                    formatDistribution = formatDistribution,
                    topArtists = topArtists,
                    topAlbums = topAlbums,
                    todayEdits = todayEdits,
                    weekEdits = weekEdits,
                    monthEdits = monthEdits
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load statistics")
                // Check if database is empty vs query error
                try {
                    val diagnosticDb = withContext(Dispatchers.IO) {
                        databaseProvider.getDatabase()
                    }
                    val diagnosticDao = diagnosticDb.audioFileDao()
                    val count = diagnosticDao.getTotalFileCount()
                    Timber.d("Database has $count files")
                    if (count == 0) {
                        _uiState.value = StatisticsUiState.Empty
                    } else {
                        // Database has data but other queries failed
                        Timber.w("Statistics queries failed but database has data")
                        _uiState.value = StatisticsUiState.Empty
                    }
                } catch (countError: Exception) {
                    Timber.e(countError, "Failed to get file count for error diagnosis")
                    _uiState.value = StatisticsUiState.Empty
                }
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    /**
     * Converts a content URI to a filesystem path.
     * Used for whitelist/blacklist path filtering.
     */
    private fun mapUriToPath(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") return uri.path ?: ""

            if (uri.scheme != "content") return uri.path ?: ""

            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId.startsWith("raw:")) {
                return documentId.removePrefix("raw:")
            }

            val idParts = documentId.split(":", limit = 2)
            val volume = idParts.firstOrNull().orEmpty()
            val relativePath = idParts.getOrNull(1)?.trim('/').orEmpty()

            val result = when {
                volume.equals("primary", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    if (relativePath.isEmpty()) externalRoot else "$externalRoot/$relativePath"
                }
                volume.equals("home", ignoreCase = true) -> {
                    val externalRoot = Environment.getExternalStorageDirectory().absolutePath
                    val documentsRoot = "$externalRoot/Documents"
                    if (relativePath.isEmpty()) documentsRoot else "$documentsRoot/$relativePath"
                }
                volume.isNotEmpty() -> {
                    if (relativePath.isEmpty()) "/storage/$volume" else "/storage/$volume/$relativePath"
                }
                else -> uri.path ?: ""
            }
            // Apply NFC normalization to ensure consistent path matching
            Normalizer.normalize(result, Normalizer.Form.NFC)
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert URI to path: $uriString")
            Uri.parse(uriString).path.orEmpty()
        }
    }

    fun refresh() {
        loadStatistics()
    }
}

/**
 * UI State for Statistics screen.
 */
sealed class StatisticsUiState {
    data object Loading : StatisticsUiState()
    data object Empty : StatisticsUiState()
    data class Success(
        val totalFiles: Int,
        val totalDurationFormatted: String,
        val totalSizeFormatted: String,
        val editedFilesCount: Int,
        val formatDistribution: Map<String, Int>,
        val topArtists: List<Pair<String, Int>>,
        val topAlbums: List<Pair<String, Int>>,
        val todayEdits: Int,
        val weekEdits: Int,
        val monthEdits: Int
    ) : StatisticsUiState()
}
