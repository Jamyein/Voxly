package com.voxly.presentation.screens.metadata

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
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.Lyrics.Companion.parseToLines
import com.voxly.presentation.components.lyricsposter.ColorExtractor
import com.voxly.presentation.components.lyricsposter.LyricsPosterCardWithBlurBackground
import com.voxly.presentation.components.lyricsposter.LyricsPosterShare
import com.voxly.presentation.components.lyricsposter.LyricsAlignment
import com.voxly.presentation.components.lyricsposter.PosterCaptureBox
import com.voxly.presentation.components.lyricsposter.PosterColorTheme
import com.voxly.presentation.components.lyricsposter.PosterConfig
import com.voxly.presentation.components.lyricsposter.PosterFontWeight
import com.voxly.presentation.components.lyricsposter.PosterShape
import com.voxly.presentation.components.lyricsposter.WatermarkPosition
import com.voxly.presentation.components.lyricsposter.rememberPosterCapture
import com.voxly.presentation.viewmodel.LyricsPosterViewModel
import androidx.compose.runtime.collectAsState

/**
 * 歌词海报生成器 Screen
 *
 * 使用 Compose UI 绘制海报，通过 GraphicsLayer 捕获生成图片
 * 预览和分享使用完全相同的 Compose UI 代码，确保像素级一致
 *
 * 核心设计：
 * 1. 使用 rememberPosterCapture() 创建 GraphicsLayer 捕获器
 * 2. 使用 PosterCaptureBox 包裹海报 Compose UI
 * 3. 预览时显示 Compose UI（实时响应配置变化）
 * 4. 分享时调用 captureAsync() 生成 Bitmap
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

    // GraphicsLayer 海报捕获器 - 用于预览和导出
    val posterCapture = rememberPosterCapture()

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
    var isSaving by remember { mutableStateOf(false) }
    
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
            PosterColorTheme.BLURRED_COVER, PosterColorTheme.GRADIENT -> 
                extractedColors?.backgroundDominant?.let { Color(it) }
                ?: Color.DarkGray // 模糊封面和渐变色主题使用提取的主色调作为参考
            PosterColorTheme.CUSTOM -> customColor
        }
    }

    // Animate background color
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "background_color"
    )

    // Get content color for contrast
    val targetContentColor = remember(selectedTheme, extractedColors) {
        when (selectedTheme) {
            PosterColorTheme.MUTED -> extractedColors?.contentMuted?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.VIBRANT -> extractedColors?.contentDominant?.let { Color(it) }
                ?: Color.White
            PosterColorTheme.BLURRED_COVER, PosterColorTheme.GRADIENT -> Color.White // 模糊封面和渐变色主题固定使用白色文字
            PosterColorTheme.CUSTOM -> ColorExtractor.getContrastingTextColor(customColor)
        }
    }

    // Animate content color
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "content_color"
    )

    // Unified config for both preview and share
    // 特殊背景主题（模糊封面、渐变色）需要保留原主题类型
    val unifiedConfig = if (selectedTheme == PosterColorTheme.BLURRED_COVER || selectedTheme == PosterColorTheme.GRADIENT) {
        posterConfig.copy(
            colorTheme = selectedTheme,
            customBackgroundColor = backgroundColor,
            customContentColor = contentColor
        )
    } else {
        posterConfig.copy(
            colorTheme = PosterColorTheme.CUSTOM,
            customBackgroundColor = backgroundColor,
            customContentColor = contentColor
        )
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
                    // Save button - 保存到本地相册
                    IconButton(
                        onClick = {
                            if (!isSaving && !isGenerating && selectedLyrics.isNotEmpty() && posterCapture.isReady()) {
                                isSaving = true
                                posterCapture.captureAsync { bitmap ->
                                    bitmap?.let {
                                        LyricsPosterShare.savePosterToGallery(context, it, title) { uri ->
                                            if (uri != null) {
                                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                            }
                                            isSaving = false
                                        }
                                    } ?: run {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = selectedLyrics.isNotEmpty() && !isGenerating && !isSaving && posterCapture.isReady()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "保存到本地"
                            )
                        }
                    }

                    // Share button in top bar - 使用 GraphicsLayer 捕获 Compose UI
                    IconButton(
                        onClick = {
                            if (!isGenerating && !isSaving && selectedLyrics.isNotEmpty() && posterCapture.isReady()) {
                                isGenerating = true
                                posterCapture.captureAsync { bitmap ->
                                    bitmap?.let {
                                        LyricsPosterShare.sharePoster(context, it, title)
                                    }
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = selectedLyrics.isNotEmpty() && !isGenerating && !isSaving && posterCapture.isReady()
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
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
            // Preview with Compose UI + GraphicsLayer capture
            // 使用 PosterCaptureBox 包裹海报，确保预览和导出像素级一致
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraLarge)
                ) {
                    PosterCaptureBox(
                        capture = posterCapture,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LyricsPosterCardWithBlurBackground(
                            title = title,
                            artist = artist,
                            albumArt = albumArtBitmap,
                            lyrics = selectedLyrics,
                            config = unifiedConfig,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
                    selected = selectedTheme == PosterColorTheme.BLURRED_COVER,
                    onClick = { onThemeSelected(PosterColorTheme.BLURRED_COVER) },
                    label = { Text("模糊封面") }
                )

                FilterChip(
                    selected = selectedTheme == PosterColorTheme.GRADIENT,
                    onClick = { onThemeSelected(PosterColorTheme.GRADIENT) },
                    label = { Text("渐变色") }
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
                                    imageVector = Icons.Default.Check,
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
