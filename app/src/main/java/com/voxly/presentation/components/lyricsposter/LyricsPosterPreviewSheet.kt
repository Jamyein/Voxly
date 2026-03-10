package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.Lyrics.Companion.parseLrc
import kotlinx.coroutines.launch

/**
 * Color theme options for the poster.
 */
enum class PosterColorTheme {
    DOMINANT,
    VIBRANT,
    LIGHT_VIBRANT,
    DARK_VIBRANT,
    MUTED,
    LIGHT_MUTED,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPosterPreviewSheet(
    title: String,
    artist: String,
    album: String = "",
    lyricsText: String,
    albumArtBytes: ByteArray?,
    preSelectedLyrics: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onShare: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Decode album art
    val albumArtBitmap = remember(albumArtBytes) {
        albumArtBytes?.let {
            try {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Parse lyrics to get total line count
    val totalLyricLines = remember(lyricsText) {
        parseLyricsForRange(lyricsText).size
    }

    // State
    var selectedTheme by remember { mutableStateOf(PosterColorTheme.DOMINANT) }
    var customColor by remember { mutableStateOf(ColorExtractor.colorOptions.first()) }
    var selectedTextColor by remember { mutableStateOf<Color?>(null) }
    var selectedLyricsForPoster by remember {
        mutableStateOf(preSelectedLyrics)
    }
    var fontSizeScale by remember { mutableFloatStateOf(1.0f) }
    var generatedPoster by remember { mutableStateOf<Bitmap?>(null) }

    // Update selected lyrics when pre-selected lyrics change
    LaunchedEffect(preSelectedLyrics) {
        if (preSelectedLyrics.isNotEmpty()) {
            selectedLyricsForPoster = preSelectedLyrics
        }
    }

    // Get all lyrics for button check
    val allLyricsLines = remember(lyricsText) {
        parseLyricsForRange(lyricsText)
    }

    // Generate poster when parameters change
    val backgroundColor = remember(selectedTheme, customColor, albumArtBitmap) {
        when (selectedTheme) {
            PosterColorTheme.DOMINANT -> albumArtBitmap?.let { ColorExtractor.extractDominantColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.VIBRANT -> albumArtBitmap?.let { ColorExtractor.extractVibrantColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.LIGHT_VIBRANT -> albumArtBitmap?.let { ColorExtractor.extractLightVibrantColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.DARK_VIBRANT -> albumArtBitmap?.let { ColorExtractor.extractDarkVibrantColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.MUTED -> albumArtBitmap?.let { ColorExtractor.extractMutedColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.LIGHT_MUTED -> albumArtBitmap?.let { ColorExtractor.extractLightMutedColor(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.CUSTOM -> customColor
        }
    }

    // Generate poster when any parameter changes
    LaunchedEffect(title, artist, album, lyricsText, albumArtBitmap, backgroundColor, selectedTextColor, selectedLyricsForPoster, fontSizeScale) {
        if (totalLyricLines > 0) {
            generatedPoster = LyricsPosterGenerator.generatePoster(
                title = title,
                artist = artist,
                album = album,
                lyricsText = lyricsText,
                albumArtBitmap = albumArtBitmap,
                backgroundColor = backgroundColor,
                textColor = selectedTextColor,
                selectedLyrics = selectedLyricsForPoster,
                fontSizeScale = fontSizeScale
            )
        }
    }

    // Get localized strings outside LazyRow
    val colorDominant = stringResource(R.string.color_dominant)
    val colorVibrant = stringResource(R.string.color_vibrant)
    val colorLightVibrant = stringResource(R.string.color_light_vibrant)
    val colorDarkVibrant = stringResource(R.string.color_dark_vibrant)
    val colorMuted = stringResource(R.string.color_muted)
    val colorLightMuted = stringResource(R.string.color_light_muted)
    val colorCustom = stringResource(R.string.color_custom)

    val themes = listOf(
        PosterColorTheme.DOMINANT to colorDominant,
        PosterColorTheme.VIBRANT to colorVibrant,
        PosterColorTheme.LIGHT_VIBRANT to colorLightVibrant,
        PosterColorTheme.DARK_VIBRANT to colorDarkVibrant,
        PosterColorTheme.MUTED to colorMuted,
        PosterColorTheme.LIGHT_MUTED to colorLightMuted,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = stringResource(R.string.lyrics_poster_preview),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preview
            generatedPoster?.let { poster ->
                Image(
                    bitmap = poster.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_poster_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Color theme selector
            Text(
                text = stringResource(R.string.select_color_theme),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Theme options
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(themes) { (theme, label) ->
                    ColorThemeChip(
                        label = label,
                        isSelected = selectedTheme == theme,
                        onClick = { selectedTheme = theme }
                    )
                }

                // Custom color option
                item {
                    ColorThemeChip(
                        label = colorCustom,
                        isSelected = selectedTheme == PosterColorTheme.CUSTOM,
                        onClick = { selectedTheme = PosterColorTheme.CUSTOM }
                    )
                }
            }

            // Custom color picker
            if (selectedTheme == PosterColorTheme.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(ColorExtractor.colorOptions) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (customColor == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { customColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (customColor == color) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ColorExtractor.getContrastingTextColor(color),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text color selector
            Text(
                text = stringResource(R.string.text_color),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // Auto option
                item {
                    Surface(
                        onClick = { selectedTextColor = null },
                        shape = MaterialTheme.shapes.medium,
                        color = if (selectedTextColor == null)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Auto",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                items(ColorExtractor.textColorOptions) { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedTextColor == color) 3.dp else 1.dp,
                                color = if (selectedTextColor == color)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { selectedTextColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedTextColor == color) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = ColorExtractor.getContrastingTextColor(color),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font size selector
            Text(
                text = stringResource(R.string.font_size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.small),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = fontSizeScale,
                    onValueChange = { fontSizeScale = it },
                    valueRange = 0.7f..1.5f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text(
                    text = stringResource(R.string.large),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Share button
            Button(
                onClick = {
                    generatedPoster?.let { poster ->
                        scope.launch {
                            LyricsPosterShare.sharePoster(context, poster, title)
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = generatedPoster != null && allLyricsLines.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.share_poster))
            }
        }
    }
}

/**
 * Parses lyrics text and returns plain text lines for range selection.
 */
private fun parseLyricsForRange(lyricsText: String): List<String> {
    if (lyricsText.isBlank()) {
        return listOf("No lyrics available")
    }

    return try {
        val lyrics = parseLrc(lyricsText)
        if (lyrics.isSynced && lyrics.syncedLines.isNotEmpty()) {
            lyrics.syncedLines.map { it.text }.filter { it.isNotBlank() }
        } else {
            lyricsText.lines().filter { it.isNotBlank() }
        }
    } catch (e: Exception) {
        lyricsText.lines().filter { it.isNotBlank() }
    }
}

@Composable
private fun ColorThemeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
