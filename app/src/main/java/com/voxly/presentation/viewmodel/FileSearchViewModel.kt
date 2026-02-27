package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.MusicLibraryCache
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * ViewModel for FileSearchScreen.
 * Loads audio files from cache based on file paths.
 */
@HiltViewModel
class FileSearchViewModel @Inject constructor(
    private val musicLibraryCache: MusicLibraryCache
) : ViewModel() {

    /**
     * Gets audio files for the given file paths from the cache.
     */
    fun getAudioFilesForPaths(filePaths: List<String>): Flow<List<AudioFile>> {
        return musicLibraryCache.getCachedAudioFiles().map { cachedFiles ->
            val pathSet = filePaths.toSet()
            cachedFiles.filter { it.path in pathSet }
        }
    }
}
