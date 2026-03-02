package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.repository.RecentEdit
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Recent Edits screen.
 * Manages the list of recently edited audio files.
 */
@HiltViewModel
class RecentEditsViewModel @Inject constructor(
    private val recentEditsRepository: RecentEditsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecentEditsUiState>(RecentEditsUiState.Loading)
    val uiState: StateFlow<RecentEditsUiState> = _uiState.asStateFlow()

    init {
        loadRecentEdits()
    }

    private fun loadRecentEdits() {
        viewModelScope.launch {
            try {
                _uiState.value = RecentEditsUiState.Loading
                recentEditsRepository.getRecentEdits(limit = 50).collect { edits ->
                    if (edits.isEmpty()) {
                        _uiState.value = RecentEditsUiState.Empty
                    } else {
                        _uiState.value = RecentEditsUiState.Success(edits)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RecentEditsUiState.Empty
            }
        }
    }

    fun refresh() {
        loadRecentEdits()
    }

    fun clearHistory() {
        viewModelScope.launch {
            recentEditsRepository.clearRecentEdits()
            _uiState.value = RecentEditsUiState.Empty
        }
    }
}

/**
 * UI State for Recent Edits screen.
 */
sealed class RecentEditsUiState {
    data object Loading : RecentEditsUiState()
    data object Empty : RecentEditsUiState()
    data class Success(
        val edits: List<RecentEdit>
    ) : RecentEditsUiState()
}
