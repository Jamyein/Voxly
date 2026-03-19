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

                // Get year distribution
                val yearDistributionQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT year, COUNT(*) as count FROM cached_audio_files WHERE year IS NOT NULL AND year != '' AND year != '0' GROUP BY year ORDER BY year DESC"
                )
                val yearDistributionRaw = yearDistributionQuery?.let { dao.getYearDistributionFiltered(it) } ?: dao.getYearDistribution()

                // Group raw year data into ranges
                val yearGroups = mutableMapOf<String, Int>()
                yearDistributionRaw.forEach { yearCount ->
                    val group = groupYearIntoRange(yearCount.year)
                    yearGroups[group] = yearGroups.getOrDefault(group, 0) + yearCount.count
                }
                val yearDistribution = yearGroups.toSortedMap()  // 按键排序

                // Get bitrate distribution
                val bitrateDistributionQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist, "SELECT bitrate, COUNT(*) as count FROM cached_audio_files GROUP BY bitrate ORDER BY bitrate ASC"
                )
                val bitrateDistributionRaw = bitrateDistributionQuery?.let { dao.getBitrateDistributionFiltered(it) } ?: dao.getBitrateDistribution()

                // Group raw bitrate data into SQ/HQ/HiFi
                val bitrateGroups = mutableMapOf<String, Int>()
                bitrateDistributionRaw.forEach { bitrateCount ->
                    val group = groupBitrateIntoRange(bitrateCount.bitrate)
                    bitrateGroups[group] = bitrateGroups.getOrDefault(group, 0) + bitrateCount.count
                }
                val bitrateDistribution = bitrateGroups

                // Get genre distribution (top 10)
                val genreDistributionQuery = dao.buildPathFilterQuery(
                    whitelist, blacklist,
                    "SELECT genre, COUNT(*) as count FROM cached_audio_files WHERE genre IS NOT NULL AND genre != '' GROUP BY genre ORDER BY count DESC",
                    limit = 10
                )
                val genreDistributionRaw = genreDistributionQuery?.let { dao.getGenreDistributionFiltered(it) } ?: dao.getGenreDistribution(10)
                val genreDistribution = genreDistributionRaw.associate { it.genre to it.count }

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
                    monthEdits = monthEdits,
                    yearDistribution = yearDistribution,
                    genreDistribution = genreDistribution,
                    bitrateDistribution = bitrateDistribution
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

    /**
     * 将年份字符串分组到对应的年代区间。
     * 使用动态计算，适配不同年份的数据。
     *
     * 分组规则（按优先级）：
     * 1. year > currentYear -> "${currentYear}+"
     * 2. currentYear-5 <= year <= currentYear -> "${currentYear-5}-${currentYear}"
     * 3. currentYear-10 <= year <= currentYear-6 -> "${currentYear-10}-${currentYear-6}"
     * 4. currentYear-15 <= year <= currentYear-11 -> "${currentYear-15}-${currentYear-11}"
     * 5. year < currentYear-15 且 year >= 2000 -> 动态 decade 区间 (如 "2000-2010")
     * 6. year < 2000 -> "<2000"
     * 7. year 为空或无效 -> "Unknown" (不计入统计)
     */
    private fun groupYearIntoRange(yearStr: String): String {
        val year = yearStr.toIntOrNull() ?: return "Unknown"
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val fiveYearAgo = currentYear - 5
        val tenYearAgo = currentYear - 10
        val fifteenYearAgo = currentYear - 15

        return when {
            year > currentYear -> "${currentYear}+"
            year >= fiveYearAgo -> "${fiveYearAgo}-${currentYear}"
            year >= tenYearAgo -> "${tenYearAgo}-${fiveYearAgo - 1}"
            year >= fifteenYearAgo -> "${fifteenYearAgo}-${tenYearAgo - 1}"
            year >= 2000 -> "${2000}-${tenYearAgo - 1}"  // Gap: 2000 ~ (currentYear-16)
            year < 2000 -> "<2000"
            else -> "Unknown"
        }
    }

    /**
     * 将比特率值分组到对应的质量等级。
     * SQ: <192 kbps, HQ: 192-320 kbps, HiFi: >320 kbps
     */
    private fun groupBitrateIntoRange(bitrate: Int): String {
        return when {
            bitrate > 320 -> "HiFi"
            bitrate >= 192 -> "HQ"
            else -> "SQ"
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
        val monthEdits: Int,
        val yearDistribution: Map<String, Int>,   // e.g. "2021-2026" -> 150
        val genreDistribution: Map<String, Int>,  // e.g. "Pop" -> 120
        val bitrateDistribution: Map<String, Int> // e.g. "SQ" -> 50, "HQ" -> 80, "HiFi" -> 20
    ) : StatisticsUiState()
}
