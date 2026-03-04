package com.voxly.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.data.local.AudioFileScanner
import com.voxly.data.repository.AlbumCacheRepository
import com.voxly.domain.model.AudioFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for AlbumDetailScreen.
 * Loads album data from memory cache.
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumCacheRepository: AlbumCacheRepository,
    private val audioFileScanner: AudioFileScanner
) : ViewModel() {

    private val _albumName = MutableStateFlow("")
    val albumName: StateFlow<String> = _albumName.asStateFlow()

    private val _albumArtist = MutableStateFlow<String?>(null)
    val albumArtist: StateFlow<String?> = _albumArtist.asStateFlow()

    private val _albumYear = MutableStateFlow<String?>(null)
    val albumYear: StateFlow<String?> = _albumYear.asStateFlow()

    private val _albumBitrate = MutableStateFlow(0)
    val albumBitrate: StateFlow<Int> = _albumBitrate.asStateFlow()

    private val _albumSampleRate = MutableStateFlow(0)
    val albumSampleRate: StateFlow<Int> = _albumSampleRate.asStateFlow()

    private val _coverPath = MutableStateFlow<String?>(null)
    val coverPath: StateFlow<String?> = _coverPath.asStateFlow()

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files.asStateFlow()

    /**
     * Load album data from cache by album name and artist.
     * Year is loaded from file tags (not MediaStore) for accurate results.
     */
    fun loadAlbum(albumName: String, albumArtist: String?) {
        viewModelScope.launch {
            try {
                val albumGroup = albumCacheRepository.getAlbum(albumName, albumArtist)

                if (albumGroup != null) {
                    _albumName.value = albumGroup.name
                    _albumArtist.value = albumGroup.artist
                    _coverPath.value = albumGroup.coverPath

                    // Use the AudioFile objects directly from the cached AlbumGroup
                    _files.value = albumGroup.files

                    // Get bitrate, sample rate, and year from first file
                    albumGroup.files.firstOrNull()?.let { firstFile ->
                        _albumBitrate.value = firstFile.bitrate
                        _albumSampleRate.value = firstFile.sampleRate

                        // Load detailed metadata to get year from file tags (not MediaStore)
                        val detailedMetadata = audioFileScanner.loadDetailedMetadata(firstFile.path)
                        _albumYear.value = detailedMetadata?.year
                    }
                } else {
                    // Album not found in cache - should not happen if caching is done correctly
                    _albumName.value = albumName
                    _albumArtist.value = albumArtist
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
