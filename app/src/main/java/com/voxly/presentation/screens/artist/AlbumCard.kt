package com.voxly.presentation.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder


/**
 * 专辑卡片
 *
 * 使用 ContentScale.Crop 填充 + 底部 scrim 渐变 + 文字叠加
 * 圆角裁剪由 Carousel 的 maskClip 在外部处理
 */
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
    Box(
        modifier = modifier
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        // 封面图填满
        if (!albumArtPath.isNullOrBlank() || albumId != null) {
            AlbumArtImage(
                filePath = albumArtPath,
                albumId = albumId,
                contentDescription = albumName,
                modifier = Modifier.fillMaxSize(),
                size = 200.dp,
                contentScale = ContentScale.Crop
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
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0f),
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Text(
                text = albumName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
