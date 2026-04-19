package com.voxly.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Immutable
data class SearchSeed(
    val filePath: String,
    val title: String,
    val artist: String?,
    val album: String?
)

@ActivityRetainedScoped
class SearchSeedHolder @Inject constructor() : ViewModel() {

    private val _seedsByFile = MutableStateFlow<Map<String, SearchSeed>>(emptyMap())

    fun updateSeed(filePath: String, title: String, artist: String?, album: String?) {
        _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
            put(filePath, SearchSeed(filePath, title, artist, album))
        }
    }

    fun peekSeed(filePath: String): SearchSeed? {
        return _seedsByFile.value[filePath]
    }

    fun getAndClearSeed(filePath: String): SearchSeed? {
        val seed = _seedsByFile.value[filePath]
        if (seed != null) {
            _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
                remove(filePath)
            }
        }
        return seed
    }

    fun removeSeedForFile(filePath: String) {
        _seedsByFile.value = _seedsByFile.value.toMutableMap().apply {
            remove(filePath)
        }
    }

    fun clearAllSeeds() {
        _seedsByFile.value = emptyMap()
    }
}