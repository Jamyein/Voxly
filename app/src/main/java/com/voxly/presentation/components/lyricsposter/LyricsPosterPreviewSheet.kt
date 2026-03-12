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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.Lyrics.Companion.parseToLines
import kotlinx.coroutines.launch

/**
 * Color theme options for poster - matching Rush's CardColors enum
 * MUTED: 柔和颜色 (muted swatch)
 * VIBRANT: 鲜艳颜色 (vibrant swatch)
 * CUSTOM: 用户自定义颜色
 */
enum class PosterColorTheme {
    MUTED,
    VIBRANT,
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

    // Parse lyrics - single source of truth
    val allLyricsLines = remember(lyricsText) {
        parseToLines(lyricsText)
    }

    // State
    var selectedTheme by remember { mutableStateOf(PosterColorTheme.VIBRANT) }
    var customColor by remember { mutableStateOf(ColorExtractor.colorOptions.first()) }
    var fontSizeScale by remember { mutableFloatStateOf(1.0f) }
    var generatedPoster by remember { mutableStateOf<Bitmap?>(null) }

    // Directly use preSelectedLyrics for poster generation
    val selectedLyricsForPoster = preSelectedLyrics

    // Extract colors from album art - Rush's approach
    val extractedColors = remember(selectedTheme, albumArtBitmap) {
        if (albumArtBitmap != null && selectedTheme != PosterColorTheme.CUSTOM) {
            ColorExtractor.extractColors(albumArtBitmap)
        } else null
    }

    // Get background and content colors based on selected theme - Rush's approach
    val backgroundColor = remember(selectedTheme, customColor, extractedColors) {
        when (selectedTheme) {
            PosterColorTheme.MUTED -> extractedColors?.backgroundMuted?.let { Color(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.VIBRANT -> extractedColors?.backgroundDominant?.let { Color(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.CUSTOM -> customColor
        }
    }

    // Get content (text) color for contrast
    val contentColor = remember(selectedTheme, extractedColors) {
        when (selectedTheme) {
            PosterColorTheme.MUTED -> extractedColors?.contentMuted?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.VIBRANT -> extractedColors?.contentDominant?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.CUSTOM -> ColorExtractor.getContrastingTextColor(customColor)
        }
    }

    // Generate poster when any parameter changes
    LaunchedEffect(title, artist, album, lyricsText, albumArtBitmap, backgroundColor, contentColor, preSelectedLyrics, fontSizeScale) {
        if (allLyricsLines.isNotEmpty()) {
            generatedPoster = LyricsPosterGenerator.generatePoster(
                title = title,
                artist = artist,
                album = album,
                lyricsText = lyricsText,
                albumArtBitmap = albumArtBitmap,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                selectedLyrics = selectedLyricsForPoster,
                fontSizeScale = fontSizeScale
            )
        }
    }

    // Get localized strings outside LazyRow
    val colorMuted = stringResource(R.string.color_soft)     // 柔和
    val colorVibrant = stringResource(R.string.color_vivid) // 鲜艳
    val colorCustom = stringResource(R.string.color_custom)

    val themes = listOf(
        PosterColorTheme.MUTED to colorMuted,
        PosterColorTheme.VIBRANT to colorVibrant,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = stringResource(R.string.lyrics_poster_preview),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preview - swipe up to expand to full screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                generatedPoster?.let { poster ->
                    Image(
                        bitmap = poster.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_poster_preview),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text(
                    text = stringResource(R.string.generating_poster),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable content - wrapped in Box with weight(1f) to fill available space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
