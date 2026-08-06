package com.voxly.presentation.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import timber.log.Timber
import com.voxly.R
import com.voxly.core.util.Constants
import com.voxly.presentation.components.AlbumArtImage
import com.voxly.presentation.components.AlbumCoverDecodeSize
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyScaffold
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.components.createAlbumCoverSharedElementKey
import com.voxly.presentation.components.createAlbumTitleSharedElementKey
import com.voxly.presentation.components.createAlbumArtistTextSharedElementKey
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.components.navBarsBottomInset
import com.voxly.presentation.components.openMetadataFor
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import com.voxly.presentation.theme.rememberSharedElementBoundsTransform
import com.voxly.presentation.theme.rememberSharedElementTextBoundsTransform
import com.voxly.presentation.theme.rememberSharedElementTitleBoundsTransform
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.screens.album.formatBitrate
import com.voxly.presentation.screens.album.formatSampleRate
import com.voxly.presentation.screens.metadata.SectionTitle

/**
 * Album detail screen showing album info and track list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    albumArtist: String?,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    viewModel: AlbumDetailViewModel,
    initialCoverPath: String? = null,
    initialCoverAlbumId: Long? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dominantColor by viewModel.dominantColor.collectAsStateWithLifecycle()
    val albumNameState = state.albumName
    val albumArtistState = state.albumArtist
    val albumYear = state.albumYear
    val albumBitrate = state.albumBitrate
    val albumSampleRate = state.albumSampleRate
    val files = state.files
    val coverPath = state.coverPath
    val coverUri = state.coverUri
    // Calculate total duration
    val totalDuration = remember(files) {
        files.sumOf { it.duration }
    }
    val formattedTotalDuration = remember(totalDuration) {
        val hours = totalDuration / Constants.MS_PER_HOUR
        val minutes = (totalDuration % Constants.MS_PER_HOUR) / Constants.MS_PER_MINUTE
        val seconds = (totalDuration % Constants.MS_PER_MINUTE) / Constants.MS_PER_SECOND
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    // Sort by disc number and track number
    val sortedFiles = remember(files) {
        files.toList().sortedWith(
            compareBy({ it.metadata.discNumber ?: 1 }, { it.metadata.trackNumber ?: 0 })
        )
    }

    // Group by disc in composition scope (LazyColumn content can't remember)
    val (sortedDiscNumbers, groupedFiles) = remember(sortedFiles) {
        val g = sortedFiles.groupBy { it.metadata.discNumber ?: 1 }
        g.keys.sorted() to g
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    VoxlyScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            VoxlyTopAppBar(
                theme = TopBarTheme.Immersive,
                title = { },
                scrollBehavior = scrollBehavior,
                onBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            dominantColor?.copy(alpha = 0.25f)
                                ?: MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 12.dp + navBarsBottomInset(),
                    start = 12.dp,
                    end = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Album Hero Section: Cover + Info
                item {
                    // Reverse parallax: cover moves UP slower than scroll (immersive effect)
                    val parallaxOffset by remember {
                        derivedStateOf {
                            if (listState.firstVisibleItemIndex == 0) {
                                -listState.firstVisibleItemScrollOffset * 0.3f
                            } else {
                                0f
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = parallaxOffset
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Push cover down to ~30% of screen height
                        Spacer(modifier = Modifier.height(statusBarHeight + 120.dp))
                        
                        // Cover image - 1:1 square with large rounded corners
                        val firstFile = files.firstOrNull()
                        val albumCoverKey = createAlbumCoverSharedElementKey(albumName, albumArtist)
                        val canUseSharedTransition = sharedTransitionScope != null && animatedVisibilityScope != null
                        Timber.d("AlbumDetailScreen: canUseSharedTransition=$canUseSharedTransition, key=$albumCoverKey, albumName=$albumName")
                        // Pre-resolve MediaStore URI from navigation key for immediate display.
                        // This ensures AlbumArtImage has a non-null model on the first frame,
                        // enabling Coil to find the list page's cached bitmap via memoryCacheKey.
                        val quickCoverUri = remember(initialCoverAlbumId, initialCoverPath, coverUri) {
                            coverUri ?: if (initialCoverAlbumId != null && initialCoverAlbumId > 0) {
                                android.content.ContentUris.withAppendedId(
                                    android.net.Uri.parse("content://media/external/audio/albumart"),
                                    initialCoverAlbumId
                                )
                            } else null
                        }
                        // 封面圆角随共享过渡的 bounds 弹簧同步渐变（Level 2 目标端）：
                        // push 时从列表端 12dp 圆角渐变到详情端 28dp。transition.animateFloat 与
                        // bounds 动画挂在同一个 AnimatedVisibilityScope 过渡上（bounds 是它的子过渡），
                        // 同源同 spec → 帧同步，无相位误差。
                        val coverShape = if (canUseSharedTransition) {
                            val corner by animatedVisibilityScope.transition.animateFloat(
                                transitionSpec = { MaterialTheme.motionScheme.defaultSpatialSpec() },
                                label = "albumDetailCoverCornerMorph",
                                targetValueByState = { state ->
                                    if (state == EnterExitState.Visible) 28f else 12f
                                }
                            )
                            RoundedCornerShape(corner.dp)
                        } else {
                            MaterialTheme.shapes.extraLarge
                        }
                        Box(
                            modifier = if (canUseSharedTransition) {
                                with(sharedTransitionScope) {
                                    Modifier
                                        .size(AlbumCoverDecodeSize)
                                        .aspectRatio(1f)
                                        .sharedElement(
                                            rememberSharedContentState(key = albumCoverKey),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = rememberSharedElementBoundsTransform()
                                        )
                                }
                            } else {
                                Modifier
                            }
                                .size(AlbumCoverDecodeSize)
                                .aspectRatio(1f)
                                .shadow(8.dp, shape = coverShape)
                                .clip(coverShape)
                        ) {
                            AlbumArtImage(
                                filePath = coverPath ?: firstFile?.path ?: initialCoverPath,
                                albumId = (firstFile?.mediaStoreAlbumId).takeIf { it != null && it > 0 }
                                    ?: initialCoverAlbumId,
                                contentDescription = stringResource(R.string.album_cover),
                                size = AlbumCoverDecodeSize,
                                modifier = Modifier.fillMaxSize(),
                                preResolvedUri = quickCoverUri,
                                clipShape = coverShape,
                                // The shared memory cache already hands back the list page's decoded
                                // bitmap on the first frame — a crossfade on top of the shared-element
                                // bounds morph is just a second simultaneous animation (frame cost).
                                crossfade = false
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Album name
                        val albumTitleKey = createAlbumTitleSharedElementKey(albumName, albumArtist)
                        val albumArtistKey = albumArtist?.let { createAlbumArtistTextSharedElementKey(albumName, albumArtist) }
                        Text(
                            text = albumNameState,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = if (canUseSharedTransition) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = albumTitleKey),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = rememberSharedElementTitleBoundsTransform(),
                                        // Text: scale instead of re-measure so multi-line titles don't reflow mid-morph.
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else Modifier
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Artist
                        Text(
                            text = albumArtistState ?: stringResource(R.string.unknown_album_artist),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = if (canUseSharedTransition && albumArtistKey != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = albumArtistKey),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = rememberSharedElementTextBoundsTransform(),
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else Modifier
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Inline metadata
                        val metadataLine = remember(files, formattedTotalDuration, albumYear) {
                            buildString {
                                append("${files.size}首")
                                append(" · ")
                                append(formattedTotalDuration)
                                albumYear?.let {
                                    append(" · ")
                                    append(it)
                                }
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = metadataLine,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bitrate & Sample Rate as subtle pills
                        val firstFileBitrate = remember(files) { files.firstOrNull()?.bitrate ?: 0 }
                        val firstFileSampleRate = remember(files) { files.firstOrNull()?.sampleRate ?: 0 }
                        val displayBitrate = if (albumBitrate > 0) albumBitrate else firstFileBitrate
                        val displaySampleRate = if (albumSampleRate > 0) albumSampleRate else firstFileSampleRate
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            if (displayBitrate > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    Text(
                                        text = formatBitrate(displayBitrate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (displaySampleRate > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    Text(
                                        text = formatSampleRate(displaySampleRate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Song list grouped by disc — each song is a lazy item for
                // efficient scrolling on multi-disc albums.
                sortedDiscNumbers.forEach { discNumber ->
                    val discFiles = groupedFiles[discNumber] ?: return@forEach

                    // Disc title（Cookie 徽章 + 大字，同元数据编辑页节标题）
                    item(key = "disc_header_$discNumber") {
                        SectionTitle(
                            title = "Disc $discNumber",
                            icon = AppIcon.PlaylistAdd,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Songs as individual lazy items with stable keys
                    itemsIndexed(
                        items = discFiles,
                        key = { _, audioFile -> "song_${audioFile.path}" },
                        contentType = { _, _ -> "song" }
                    ) { index, audioFile ->
                        // 首尾卡片加大圆角：顶端卡上角 / 末端卡下角 28dp，中间卡小圆角相连
                        val segmentShape = when {
                            discFiles.size == 1 -> MaterialTheme.shapes.extraLarge
                            index == 0 -> RoundedCornerShape(
                                topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp
                            )
                            index == discFiles.size - 1 -> RoundedCornerShape(
                                topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp
                            )
                            else -> RoundedCornerShape(8.dp)
                        }
                        SegmentedListItem(
                            onClick = { openMetadataFor(onNavigateToMetadata, audioFile) },
                            shapes = ListItemShapes(
                                shape = segmentShape,
                                selectedShape = segmentShape,
                                pressedShape = segmentShape,
                                focusedShape = segmentShape,
                                hoveredShape = segmentShape,
                                draggedShape = segmentShape
                            ),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            ),
                            leadingContent = {
                                Text(
                                    text = audioFile.metadata.trackNumber?.toString() ?: "-",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .width(28.dp),
                                    textAlign = TextAlign.Start
                                )
                            },
                            content = {
                                Text(
                                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    maxLines = 1
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${audioFile.metadata.artist ?: ""} • ${audioFile.getFormattedDuration()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(key = "disc_spacer_$discNumber") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}