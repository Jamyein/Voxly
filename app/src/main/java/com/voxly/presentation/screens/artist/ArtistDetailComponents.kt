package com.voxly.presentation.screens.artist

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
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
import com.voxly.presentation.components.DefaultAlbumArtPlaceholder
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.createArtistAvatarSharedElementKey
import com.voxly.presentation.components.createArtistNameSharedElementKey
import com.voxly.presentation.components.createAlbumCoverSharedElementKey

import timber.log.Timber
import com.voxly.presentation.theme.ExpressiveTypography
import com.voxly.presentation.theme.rememberSharedElementBoundsTransform
import com.voxly.presentation.theme.rememberSharedElementTextBoundsTransform
import com.voxly.presentation.theme.MaterialShapes

/**
 * Hero 区域：杂志大字报风格
 *
 * - 超大艺术家名字（57sp+ ExtraBold）占据主导
 * - 小头像（64dp）+ 横向统计标签
 * - 大量留白，排版驱动
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HeroSection(
    artistName: String,
    coverPath: String?,
    coverAlbumId: Long?,
    songCount: Int,
    albumCount: Int,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
    val artistNameKey = createArtistNameSharedElementKey(artistName)
    val avatarShape = MaterialShapes.Sunny.toShape()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = artistName,
            style = ExpressiveTypography.EmphasizedDisplay.copy(
                fontSize = 64.sp,
                lineHeight = 68.sp,
                letterSpacing = (-1.5).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (canUseSharedTransition) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(key = artistNameKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = rememberSharedElementTextBoundsTransform()
                    )
                }
            } else Modifier
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier
                            .size(64.dp)
                            .sharedElement(
                                rememberSharedContentState(key = createArtistAvatarSharedElementKey(artistName)),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .shadow(4.dp, shape = avatarShape)
                            .clip(avatarShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    }
                } else {
                    Modifier
                        .size(64.dp)
                        .shadow(4.dp, shape = avatarShape)
                        .clip(avatarShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                }
            ) {
                if (!coverPath.isNullOrBlank() || coverAlbumId != null) {
                    AlbumArtImage(
                        filePath = coverPath,
                        albumId = coverAlbumId,
                        contentDescription = artistName,
                        size = 64.dp,
                        modifier = Modifier.fillMaxSize(),
                        clipShape = avatarShape
                    )
                }
            }

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SongListItem(
    audioFile: AudioFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val coverKey = createAlbumArtSharedElementKey(audioFile.path)
    val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
    val coverShape = MaterialShapes.Cookie9Sided.toShape()

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        leadingContent = {
            Box(
                modifier = if (canUseSharedTransition) {
                    with(sharedTransitionScope) {
                        Modifier
                            .size(48.dp)
                            .sharedElement(
                                rememberSharedContentState(key = coverKey),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(coverShape)
                    }
                } else {
                    Modifier
                        .size(48.dp)
                        .clip(coverShape)
                }
            ) {
                AlbumArtImage(
                    filePath = audioFile.path,
                    albumId = audioFile.mediaStoreAlbumId,
                    contentDescription = audioFile.metadata.title,
                    size = 48.dp,
                    modifier = Modifier.fillMaxSize(),
                    clipShape = coverShape
                )
            }
        },
        content = {
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
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = audioFile.getFormattedDuration(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    )
}
