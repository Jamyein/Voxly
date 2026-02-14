package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.SettingsDataStore
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Statistics screen.
 * Calculates and provides library statistics.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val recentEditsRepository: RecentEditsRepository,
    private val settingsDataStore: SettingsDataStore
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

                val recentEdits = recentEditsRepository.getRecentEdits(limit = 1000).first()
                
                if (recentEdits.isEmpty()) {
                    _uiState.value = StatisticsUiState.Empty
                    return@launch
                }

                // Calculate statistics from recent edits
                val totalFiles = recentEdits.size
                val editedFilesCount = recentEdits.distinctBy { it.filePath }.size
                
                // Format total duration (placeholder - would need duration from audio files)
                val totalDurationFormatted = "0h 0m" // Placeholder

                // Calculate format distribution
                val formatDistribution = mutableMapOf<String, Int>()
                recentEdits.forEach { edit ->
                    val extension = edit.fileName.substringAfterLast('.', "Unknown").uppercase()
                    formatDistribution[extension] = (formatDistribution[extension] ?: 0) + 1
                }

                // Calculate top artists
                val artistCounts = mutableMapOf<String, Int>()
                recentEdits.forEach { edit ->
                    val artist = edit.newMetadata.artist ?: "Unknown Artist"
                    artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                }
                val topArtists = artistCounts.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { it.key to it.value }

                // Calculate top albums
                val albumCounts = mutableMapOf<String, Int>()
                recentEdits.forEach { edit ->
                    val album = edit.newMetadata.album ?: "Unknown Album"
                    albumCounts[album] = (albumCounts[album] ?: 0) + 1
                }
                val topAlbums = albumCounts.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { it.key to it.value }

                // Calculate recent activity
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L
                val weekMs = 7 * dayMs
                val monthMs = 30 * dayMs

                val todayEdits = recentEdits.count { now - it.timestamp < dayMs }
                val weekEdits = recentEdits.count { now - it.timestamp < weekMs }
                val monthEdits = recentEdits.count { now - it.timestamp < monthMs }

                // Calculate total size (placeholder)
                val totalSizeFormatted = calculateTotalSize(recentEdits.size * 5_000_000L) // ~5MB per file estimate

                _uiState.value = StatisticsUiState.Success(
                    totalFiles = totalFiles,
                    totalDurationFormatted = totalDurationFormatted,
                    totalSizeFormatted = totalSizeFormatted,
                    editedFilesCount = editedFilesCount,
                    formatDistribution = formatDistribution,
                    topArtists = topArtists,
                    topAlbums = topAlbums,
                    todayEdits = todayEdits,
                    weekEdits = weekEdits,
                    monthEdits = monthEdits
                )
            } catch (e: Exception) {
                _uiState.value = StatisticsUiState.Empty
            }
        }
    }

    private fun calculateTotalSize(bytes: Long): String {
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
