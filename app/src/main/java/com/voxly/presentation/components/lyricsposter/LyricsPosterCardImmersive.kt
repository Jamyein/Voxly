@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全屏沉浸式歌词海报模板
 * 
 * 待实现（Task 4）
 */
@Composable
fun LyricsPosterCardImmersive(
    title: String,
    artist: String,
    albumArt: Bitmap?,
    lyrics: List<String>,
    config: PosterConfig,
    modifier: Modifier = Modifier
) {
    // 占位实现：使用经典卡片作为回退
    LyricsPosterCardClassic(
        title = title,
        artist = artist,
        albumArt = albumArt,
        lyrics = lyrics,
        config = config,
        modifier = modifier
    )
}
