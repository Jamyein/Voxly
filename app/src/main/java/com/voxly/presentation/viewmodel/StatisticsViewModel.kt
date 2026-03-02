package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.cache.MusicCacheDatabaseProvider
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Statistics screen.
 * Provides library statistics from Room database.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val recentEditsRepository: RecentEditsRepository,
    private val databaseProvider: MusicCacheDatabaseProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                _uiState.value = StatisticsUiState.Loading

                val db = withContext(Dispatchers.IO) {
                    databaseProvider.getDatabase()
                }
                val dao = db.audioFileDao()

                // Get real statistics from database
                val totalFiles = dao.getTotalFileCount()

                if (totalFiles == 0) {
                    _uiState.value = StatisticsUiState.Empty
                    return@launch
                }

                // Get real duration and size
                val totalDurationMs = dao.getTotalDuration()
                val totalSizeBytes = dao.getTotalSize()

                // Format total duration
                val totalDurationFormatted = formatDuration(totalDurationMs)

                // Get format distribution
                val formatDistributionRaw = dao.getFormatDistribution()
                val formatDistribution = formatDistributionRaw.associate { it.format to it.count }

                // Get top artists
                val topArtistsRaw = dao.getTopArtists(10)
                val topArtists = topArtistsRaw.map { it.artist to it.count }

                // Get top albums
                val topAlbumsRaw = dao.getTopAlbums(10)
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
