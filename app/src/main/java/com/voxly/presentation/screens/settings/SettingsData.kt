package com.voxly.presentation.screens.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.voxly.domain.model.SourceConfigurations

/**
 * Data classes for settings screen
 */

data class LanguageOption(
    @StringRes val labelResId: Int,
    val languageTag: String?
)

data class LoudnessOption(
    val loudnessValue: Float,
    @StringRes val labelResId: Int
)

data class AppleCountryOption(
    val countryValue: String,
    @StringRes val labelResId: Int
)

data class SearchLimitOption(
    val limitValue: Int,
    @StringRes val labelResId: Int? = null
) {
    @androidx.compose.runtime.Composable
    fun displayLabel(): String {
        return labelResId?.let { androidx.compose.ui.res.stringResource(it) } ?: limitValue.toString()
    }
}

data class ScanModeOption(
    val modeValue: String,
    @StringRes val labelResId: Int
)

data class ConnectedIconOption<T>(
    val optionValue: T,
    val icon: ImageVector? = null,
    val tooltip: String,
    val text: String? = null
)

data class SourceItemState(
    val sourceId: String,
    val enabled: Boolean,
    val extraOptions: Map<String, String> = emptyMap(),
    val expanded: Boolean = false
)

// Helper function for connected group width
fun connectedGroupWidth(optionCount: Int): Dp {
    val perButtonBase = 40.dp
    val spacing = 2.dp
    val count = optionCount.coerceAtLeast(1)
    val width = perButtonBase * count + spacing * (count - 1)
    return width.coerceIn(124.dp, 220.dp)
}

// Language resolution
fun resolveCurrentLanguageTag(): String? {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) {
        return null
    }
    val firstTag = locales.toLanguageTags()
        .split(",")
        .firstOrNull()
        ?.trim()
        ?.ifBlank { null }
        ?: return null
    return when {
        firstTag.startsWith("zh", ignoreCase = true) -> "zh-CN"
        firstTag.startsWith("en", ignoreCase = true) -> "en"
        else -> firstTag
    }
}

fun normalizeLanguageTag(tag: String?): String? = tag?.lowercase()

fun sourceToDisplayName(source: String): String = when (source) {
    "itunes" -> "iTunes"
    "musicbrainz" -> "MusicBrainz"
    "netease" -> "NetEase"
    "qq_music" -> "QQ Music"
    else -> source
}

fun sourceHasExtraOptions(sourceId: String): Boolean = sourceId == "itunes"

fun getExtraOptionLabel(sourceId: String): String = when (sourceId) {
    "itunes" -> "Country Code"
    else -> ""
}

/**
 * UI state holder for Settings screen.
 * Combines all individual setting states into a single immutable state class.
 */
data class SettingsUiState(
    val dynamicColors: Boolean = false,
    val metadataEditorDynamicAlbumColor: Boolean = true,
    val savedLanguageTag: String? = null,
    val themeMode: String = "system",
    val appleCountryCode: String = "us",
    val onlineSearchLimit: Int = 10,
    val onlineSearchLimitMusicBrainz: Int = 10,
    val onlineSearchLimitITunes: Int = 10,
    val onlineSearchLimitNetease: Int = 10,
    val onlineSearchLimitQQMusic: Int = 10,
    val sourceConfigurations: SourceConfigurations = SourceConfigurations(),
    val loggingEnabled: Boolean = false,
    val replayGainTargetLoudness: Float = -14f,
    val scanMode: String = "TRACK_ONLY",
    val minDurationFilterEnabled: Boolean = true,
    val lyricsTimestampFormatEnabled: Boolean = false,
    val floatingBottomNavEnabled: Boolean = false
)
