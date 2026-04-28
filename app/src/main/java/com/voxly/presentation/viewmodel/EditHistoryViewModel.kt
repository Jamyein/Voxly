package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.domain.repository.RecentEdit
import com.voxly.domain.repository.RecentEditsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class EditHistoryViewModel @Inject constructor(
    private val recentEditsRepository: RecentEditsRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_LIMIT = 50
    }

    val recentEdits: StateFlow<List<RecentEdit>> = recentEditsRepository
        .getRecentEdits(DEFAULT_LIMIT)
        .also { Timber.tag("Voxly").i("EditHistoryViewModel: loadHistory") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
