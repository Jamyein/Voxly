package com.voxly.presentation.screens.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.Lyrics.Companion.parseToLines
import com.voxly.presentation.components.lyricsposter.ColorExtractor

import com.voxly.presentation.components.lyricsposter.LyricsPosterGenerator
import com.voxly.presentation.components.lyricsposter.LyricsPosterShare
import com.voxly.presentation.components.lyricsposter.LyricsAlignment
import com.voxly.presentation.components.lyricsposter.PosterColorTheme
import com.voxly.presentation.components.lyricsposter.PosterConfig
import com.voxly.presentation.components.lyricsposter.PosterFontWeight
import com.voxly.presentation.components.lyricsposter.PosterShape
import com.voxly.presentation.components.lyricsposter.WatermarkPosition
import com.voxly.presentation.theme.ExpressiveMotion
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 歌词海报生成器 Screen
 * 
 * 从 LyricsSelector 导航过来，显示海报预览和配置选项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPosterScreen(
    filePath: String,
    title: String,
    artist: String,
    album: String,
    lyricsText: String,
    selectedLyricsIndices: List<Int>,
    onNavigateBack: () -> Unit,
    viewModel: LyricsPosterViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val albumArtBytes by viewModel.albumArtBytes.collectAsState()

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

    // Parse selected lyrics
    val allLyricsLines = remember(lyricsText) {
        parseToLines(lyricsText)
    }
    
    val selectedLyrics = remember(selectedLyricsIndices, allLyricsLines) {
        selectedLyricsIndices.mapNotNull { index ->
            allLyricsLines.getOrNull(index)
        }
    }

    // State
    var selectedTheme by remember { mutableStateOf(PosterColorTheme.VIBRANT) }
    var customColor by remember { mutableStateOf(ColorExtractor.colorOptions.first()) }
    var isGenerating by remember { mutableStateOf(false) }
    
    // Poster configuration
    var posterConfig by remember {
        mutableStateOf(PosterConfig())
    }

    // Extract colors from album art
    val extractedColors = remember(selectedTheme, albumArtBitmap) {
        if (albumArtBitmap != null && selectedTheme != PosterColorTheme.CUSTOM) {
            ColorExtractor.extractColors(albumArtBitmap)
        } else null
    }

    // Get background color based on selected theme
    val targetBackgroundColor = remember(selectedTheme, customColor, extractedColors) {
        when (selectedTheme) {
            PosterColorTheme.MUTED -> extractedColors?.backgroundMuted?.let { Color(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.VIBRANT -> extractedColors?.backgroundDominant?.let { Color(it) }
                ?: ColorExtractor.colorOptions.first()
            PosterColorTheme.CUSTOM -> customColor
        }
    }

    // Animate background color
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = ExpressiveMotion.SlowSpringColor,
        label = "background_color"
    )

    // Get content color for contrast
    val targetContentColor = remember(selectedTheme, extractedColors) {
        when (selectedTheme) {
            PosterColorTheme.MUTED -> extractedColors?.contentMuted?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.VIBRANT -> extractedColors?.contentDominant?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.CUSTOM -> ColorExtractor.getContrastingTextColor(customColor)
        }
    }

    // Animate content color
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = ExpressiveMotion.SlowSpringColor,
        label = "content_color"
    )

    // Preload shapes for better performance
    LaunchedEffect(Unit) {
        LyricsPosterGenerator.preloadShapes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_poster_preview)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // Share button in top bar
                    IconButton(
                        onClick = {
                            if (!isGenerating && selectedLyrics.isNotEmpty()) {
                                scope.launch {
                                    isGenerating = true
                                    // Create config with same colors as preview
                                    val shareConfig = posterConfig.copy(
                                        colorTheme = PosterColorTheme.CUSTOM,
                                        customBackgroundColor = backgroundColor,
                                        customContentColor = contentColor
                                    )
                                    val poster = LyricsPosterGenerator.generatePoster(
                                        context = context,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        lyricsText = lyricsText,
                                        albumArtBitmap = albumArtBitmap,
                                        backgroundColor = backgroundColor,
                                        contentColor = contentColor,
                                        selectedLyrics = selectedLyrics,
                                        config = shareConfig
                                    )
                                    LyricsPosterShare.sharePoster(context, poster, title)
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = selectedLyrics.isNotEmpty() && !isGenerating
                    ) {
                        if (isGenerating) {
                            Text("...")
                        } else {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share_poster)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Preview with Compose UI (real-time)
            item {
                PosterPreviewCompose(
                    title = title,
                    artist = artist,
                    albumArtBitmap = albumArtBitmap,
                    lyrics = selectedLyrics,
                    config = posterConfig,
                    backgroundColor = backgroundColor,
                    contentColor = contentColor
                )
            }

            // Shape selector
            item {
                ShapeSelector(
                    selectedShape = posterConfig.coverShape,
                    onShapeSelected = { shape ->
                        posterConfig = posterConfig.copy(coverShape = shape)
                    }
                )
            }

            // Color theme selector
            item {
                ColorThemeSelector(
                    selectedTheme = selectedTheme,
                    customColor = customColor,
                    onThemeSelected = { theme ->
                        selectedTheme = theme
                    },
                    onCustomColorSelected = { color ->
                        selectedTheme = PosterColorTheme.CUSTOM
                        customColor = color
                    }
                )
            }

            // Font settings
            item {
                FontSettingsSection(
                    fontWeight = posterConfig.fontWeight,
                    fontSizeScale = posterConfig.fontSizeScale,
                    onFontWeightChange = { weight ->
                        posterConfig = posterConfig.copy(fontWeight = weight)
                    },
                    onFontSizeChange = { scale ->
                        posterConfig = posterConfig.copy(fontSizeScale = scale)
                    }
                )
            }

            // Layout options
            item {
                LayoutOptionsSection(
                    lyricsAlignment = posterConfig.lyricsAlignment,
                    watermarkPosition = posterConfig.watermarkPosition,
                    lineSpacing = posterConfig.lineSpacingMultiplier,
                    onAlignmentChange = { alignment ->
                        posterConfig = posterConfig.copy(lyricsAlignment = alignment)
                    },
                    onWatermarkPositionChange = { position ->
                        posterConfig = posterConfig.copy(watermarkPosition = position)
                    },
                    onLineSpacingChange = { spacing ->
                        posterConfig = posterConfig.copy(lineSpacingMultiplier = spacing)
                    }
                )
            }
            
            // Watermark toggle
            item {
                WatermarkToggle(
                    showWatermark = posterConfig.showWatermark,
                    onToggle = { show ->
                        posterConfig = posterConfig.copy(showWatermark = show)
                    }
                )
            }


        }
    }
}

/**
 * 海报预览组件
 * 
 * 使用 LyricsPosterGenerator 生成 Bitmap，确保与分享结果完全一致
 */
@Composable
private fun PosterPreviewCompose(
    title: String,
    artist: String,
    albumArtBitmap: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    backgroundColor: Color,
    contentColor: Color
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 生成预览 Bitmap（当配置变更时）
    LaunchedEffect(title, artist, albumArtBitmap, lyrics, config, backgroundColor, contentColor) {
        if (lyrics.isNotEmpty() && !isLoading) {
            isLoading = true
            previewBitmap = LyricsPosterGenerator.generatePoster(
                context = context,
                title = title,
                artist = artist,
                lyricsText = "", // lyrics 通过 selectedLyrics 传入
                albumArtBitmap = albumArtBitmap,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                selectedLyrics = lyrics,
                config = config
            )
            isLoading = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp)
                )
            }
            previewBitmap != null -> {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_poster_preview),
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.generating_poster),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

/**
 * 形状选择器
 */
@Composable
private fun ShapeSelector(
    selectedShape: PosterShape,
    onShapeSelected: (PosterShape) -> Unit
) {
    val standardShapes = PosterShape.standardShapes()
    val expressiveShapes = PosterShape.expressiveShapes()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "封面形状",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "标准",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(standardShapes) { shape ->
                    ShapeChip(
                        shape = shape,
                        isSelected = selectedShape == shape,
                        onClick = { onShapeSelected(shape) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "创意",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(expressiveShapes) { shape ->
                    ShapeChip(
                        shape = shape,
                        isSelected = selectedShape == shape,
                        onClick = { onShapeSelected(shape) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeChip(
    shape: PosterShape,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(shape.getDisplayName()) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun ColorThemeSelector(
    selectedTheme: PosterColorTheme,
    customColor: Color,
    onThemeSelected: (PosterColorTheme) -> Unit,
    onCustomColorSelected: (Color) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "颜色主题",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTheme == PosterColorTheme.MUTED,
                    onClick = { onThemeSelected(PosterColorTheme.MUTED) },
                    label = { Text("柔和") }
                )
                
                FilterChip(
                    selected = selectedTheme == PosterColorTheme.VIBRANT,
                    onClick = { onThemeSelected(PosterColorTheme.VIBRANT) },
                    label = { Text("鲜艳") }
                )
                
                FilterChip(
                    selected = selectedTheme == PosterColorTheme.CUSTOM,
                    onClick = { onThemeSelected(PosterColorTheme.CUSTOM) },
                    label = { Text("自定义") }
                )
            }

            if (selectedTheme == PosterColorTheme.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .clickable { onCustomColorSelected(color) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (customColor == color) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (ColorExtractor.isDarkColor(color)) Color.White else Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontSettingsSection(
    fontWeight: PosterFontWeight,
    fontSizeScale: Float,
    onFontWeightChange: (PosterFontWeight) -> Unit,
    onFontSizeChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "字体设置",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = fontWeight == PosterFontWeight.REGULAR,
                    onClick = { onFontWeightChange(PosterFontWeight.REGULAR) },
                    label = { Text("常规") }
                )
                
                FilterChip(
                    selected = fontWeight == PosterFontWeight.BOLD,
                    onClick = { onFontWeightChange(PosterFontWeight.BOLD) },
                    label = { Text("粗体") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "字体大小",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "小",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(32.dp)
                )
                
                Slider(
                    value = fontSizeScale,
                    onValueChange = onFontSizeChange,
                    valueRange = 0.7f..1.3f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "大",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun LayoutOptionsSection(
    lyricsAlignment: LyricsAlignment,
    watermarkPosition: WatermarkPosition,
    lineSpacing: Float,
    onAlignmentChange: (LyricsAlignment) -> Unit,
    onWatermarkPositionChange: (WatermarkPosition) -> Unit,
    onLineSpacingChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "布局选项",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "歌词对齐",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                FilterChip(
                    selected = lyricsAlignment == LyricsAlignment.START,
                    onClick = { onAlignmentChange(LyricsAlignment.START) },
                    label = { Text("左对齐") }
                )
                
                FilterChip(
                    selected = lyricsAlignment == LyricsAlignment.CENTER,
                    onClick = { onAlignmentChange(LyricsAlignment.CENTER) },
                    label = { Text("居中") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "水印位置",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                FilterChip(
                    selected = watermarkPosition == WatermarkPosition.START,
                    onClick = { onWatermarkPositionChange(WatermarkPosition.START) },
                    label = { Text("左侧") }
                )
                
                FilterChip(
                    selected = watermarkPosition == WatermarkPosition.END,
                    onClick = { onWatermarkPositionChange(WatermarkPosition.END) },
                    label = { Text("右侧") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "行间距",
                style = MaterialTheme.typography.labelMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "紧凑",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(48.dp)
                )
                
                Slider(
                    value = lineSpacing,
                    onValueChange = onLineSpacingChange,
                    valueRange = 1.2f..1.6f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "宽松",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun WatermarkToggle(
    showWatermark: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "显示水印",
                style = MaterialTheme.typography.titleMedium
            )
            
            Switch(
                checked = showWatermark,
                onCheckedChange = onToggle
            )
        }
    }
}
