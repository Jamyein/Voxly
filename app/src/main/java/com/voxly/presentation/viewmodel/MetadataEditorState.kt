package com.voxly.presentation.viewmodel

import androidx.compose.runtime.Immutable
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.domain.repository.OnlineRecording

/**
 * Sealed class representing metadata editor UI states.
 */
sealed class MetadataEditorUiState {
    data object Loading : MetadataEditorUiState()
    data object Saving : MetadataEditorUiState()
    data class Success(
        val audioFile: AudioFile,
        val editedMetadata: AudioMetadata
    ) : MetadataEditorUiState()
    data class Error(val message: String) : MetadataEditorUiState()
}

/**
 * Enum representing editable metadata fields.
 */
enum class MetadataField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    GENRE,
    COMPOSER,
    LYRICIST,
    CONDUCTOR,
    COMMENT,
    LYRICS
}

/**
 * Sealed class representing save operation results.
 */
sealed class SaveResult {
    data object Success : SaveResult()
    data class Error(
        val message: String,
        val requiresReauthorization: Boolean = false,
        val errorCode: SaveErrorCode = SaveErrorCode.SAVE_FAILED
    ) : SaveResult()
}

enum class SaveErrorCode {
    PERMISSION_REQUIRED,
    PERMISSION_REAUTHORIZE_FAILED,
    SAVE_FAILED
}

/**
 * Metadata field that can be selected for conversion.
 */
enum class ConvertibleField(val displayName: String) {
    TITLE("标题"),
    ARTIST("艺术家"),
    ALBUM("专辑"),
    ALBUM_ARTIST("专辑艺术家"),
    GENRE("流派"),
    COMPOSER("作曲"),
    LYRICIST("作词"),
    COMMENT("备注"),
    LYRICS("歌词")
}

@Immutable
data class LyricsSearchState(
    val results: List<OnlineLyricsResult> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)

@Immutable
data class CoverSearchState(
    val results: List<OnlineRecording> = emptyList(),
    val completedSources: Set<String> = emptySet(),
    val errorSources: Map<String, String> = emptyMap(),
    val isSearching: Boolean = false
)
