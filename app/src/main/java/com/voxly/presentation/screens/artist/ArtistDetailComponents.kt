package com.voxly.presentation.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.theme.ExpressiveTypography

/**
 * Hero 区域：杂志大字报风格
 *
 * - 超大艺术家名字（57sp+ ExtraBold）占据主导
 * - 小头像（64dp）+ 横向统计标签
 * - 大量留白，排版驱动
 */
@Composable
fun HeroSection(
    artistName: String,
    coverPath: String?,
    coverAlbumId: Long?,
    songCount: Int,
    albumCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // 超大艺术家名字 — 杂志封面级排版
        Text(
            text = artistName,
            style = ExpressiveTypography.EmphasizedDisplay.copy(
                fontSize = 64.sp,
                lineHeight = 68.sp,
                letterSpacing = (-1.5).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 头像 + 统计信息横排（杂志副标题风格）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 精致小头像
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(4.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!coverPath.isNullOrBlank() || coverAlbumId != null) {
                    AlbumArtImage(
                        filePath = coverPath,
                        albumId = coverAlbumId,
                        contentDescription = artistName,
                        size = 64.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 统计信息 — 杂志风格数字标签
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StatItem(number = songCount, label = "歌曲")
                StatItem(number = albumCount, label = "专辑")
            }
        }
    }
}

/**
 * 统计项 — 杂志风格（大数字 + 小标签）
 */
@Composable
private fun StatItem(
    number: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 分区标题 — 杂志风格标签（大写、宽字距、小字号）
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 2.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

/**
 * 歌曲列表项（保留左侧小封面）
 *
 * 使用 M3E ListItem，高度 64dp
 */
@Composable
fun SongListItem(
    audioFile: AudioFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AlbumArtImage(
                    filePath = audioFile.path,
                    albumId = audioFile.mediaStoreAlbumId,
                    contentDescription = audioFile.metadata.title,
                    size = 48.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        headlineContent = {
            Text(
                text = audioFile.metadata.getDisplayTitle(audioFile.name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${audioFile.metadata.artist ?: ""} · ${audioFile.metadata.album ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(
                text = audioFile.getFormattedDuration(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
