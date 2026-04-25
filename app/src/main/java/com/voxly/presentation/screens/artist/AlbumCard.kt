package com.voxly.presentation.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder

/**
 * 竖版叠加专辑卡片（Spotify 艺人精选风格）
 *
 * 尺寸：140.dp × 200.dp
 * 底部 scrim 渐变 + 文字叠加
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumCard(
    albumName: String,
    albumArtist: String?,
    trackCount: Int,
    albumYear: Int?,
    albumArtPath: String?,
    albumId: Long? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .height(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 封面图填满
            if (!albumArtPath.isNullOrBlank() || albumId != null) {
                AlbumArtImage(
                    filePath = albumArtPath,
                    albumId = albumId,
                    contentDescription = albumName,
                    modifier = Modifier.fillMaxSize(),
                    size = 200.dp,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                DefaultAlbumArtPlaceholder(size = 200.dp)
            }

            // 底部 scrim 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            // 文字叠加层
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                if (albumYear != null) {
                    Text(
                        text = albumYear.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
