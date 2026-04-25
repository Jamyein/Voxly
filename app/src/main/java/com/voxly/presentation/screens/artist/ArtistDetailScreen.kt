package com.voxly.presentation.screens.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.voxly.R
import com.voxly.presentation.components.createAlbumArtSharedElementKey
import com.voxly.presentation.viewmodel.ArtistDetailViewModel

/**
 * 艺术家详情页：杂志质感大字报风格
 *
 * 采用 M3E 规范：
 * - 超大艺术家名字主导排版（杂志封面级）
 * - 小头像 + 横向统计标签（杂志副标题风格）
 * - 固定返回按钮（不随滚动隐藏）
 * - 竖版叠加专辑卡片
 * - stickyHeader 分区标题（大写标签风格）
 * - 歌曲直接平铺，透明背景
 * - spring() 动画
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String, String?) -> Unit,
    onNavigateToAlbumDetail: (String, String?) -> Unit,
    viewModel: ArtistDetailViewModel
) {
    // 加载艺术家数据
    LaunchedEffect(artistName) {
        viewModel.loadArtist(artistName)
    }

    val artistNameState by viewModel.artistName.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val coverPath by viewModel.coverPath.collectAsStateWithLifecycle()
    val coverAlbumId by viewModel.coverAlbumId.collectAsStateWithLifecycle()
    val albumCovers by viewModel.albumCovers.collectAsStateWithLifecycle()
    val albumYears by viewModel.albumYears.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val onRefresh: () -> Unit = {
        viewModel.refresh(forceRefresh = false)
    }

    // 分离单曲和专辑歌曲
    val singles = remember(files) {
        files.filter { it.metadata.album.isNullOrBlank() }
    }

    // 专辑按年份排序（最新的在前）
    val albumsSorted = remember(files, albumYears) {
        files.filter { !it.metadata.album.isNullOrBlank() }
            .groupBy { it.metadata.album!! }
            .map { (albumName, albumFiles) ->
                val yearStr = albumYears[albumName]
                val year = yearStr?.let { extractYear(it) }
                AlbumInfo(
                    name = albumName,
                    files = albumFiles,
                    year = year
                )
            }
            .sortedByDescending { it.year ?: 0 }
    }

    val songCount = files.size
    val albumCount = albumsSorted.size

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    // 滚动过 Hero 区域后显示标题
    val showTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
            (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        AnimatedVisibility(
                            visible = showTitle,
                            enter = fadeIn(
                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                            )
                        ) {
                            Text(
                                text = artistNameState,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 24.dp,
                        bottom = 12.dp + innerPadding.calculateBottomPadding(),
                        start = 0.dp,
                        end = 0.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Hero 区域：杂志大字报风格
                    item {
                        val enterAnimation = fadeIn(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        ) + slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            initialOffsetY = { it / 4 }
                        )

                        AnimatedVisibility(
                            visible = true,
                            enter = enterAnimation
                        ) {
                            HeroSection(
                                artistName = artistNameState,
                                coverPath = coverPath,
                                coverAlbumId = coverAlbumId,
                                songCount = songCount,
                                albumCount = albumCount,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }

                    // 单曲区域
                    if (singles.isNotEmpty()) {
                        stickyHeader {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                SectionHeader(title = stringResource(R.string.singles))
                            }
                        }

                        items(singles, key = { "single_${it.path}" }) { audioFile ->
                            SongListItem(
                                audioFile = audioFile,
                                onClick = {
                                    onNavigateToMetadata(
                                        audioFile.path,
                                        createAlbumArtSharedElementKey(audioFile.path)
                                    )
                                }
                            )
                        }
                    }

                    // 专辑区域：竖版轮播
                    if (albumsSorted.isNotEmpty()) {
                        stickyHeader {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                SectionHeader(title = stringResource(R.string.albums))
                            }
                        }

                        item {
                            val carouselState = rememberCarouselState { albumsSorted.size }
                            val autoScrollEnabled = albumsSorted.size > 1

                            LaunchedEffect(carouselState) {
                                snapshotFlow { carouselState.currentItem }
                                    .collect { currentIndex ->
                                        viewModel.preloadAdjacentAlbumCovers(currentIndex)
                                        if (autoScrollEnabled) {
                                            delay(4000)
                                            if (carouselState.currentItem == currentIndex) {
                                                val nextIndex = (currentIndex + 1) % albumsSorted.size
                                                try {
                                                    carouselState.animateScrollToItem(nextIndex)
                                                } catch (_: Exception) {
                                                    // 动画可能被用户交互中断，静默处理
                                                }
                                            }
                                        }
                                    }
                            }

                            HorizontalMultiBrowseCarousel(
                                state = carouselState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(216.dp)
                                    .padding(vertical = 8.dp),
                                preferredItemWidth = 140.dp,
                                itemSpacing = 16.dp,
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) { page ->
                                val albumInfo = albumsSorted[page]
                                val albumArtPath = albumCovers[albumInfo.name]
                                val albumId = albumInfo.files.firstOrNull {
                                    it.mediaStoreAlbumId != null && it.mediaStoreAlbumId > 0
                                }?.mediaStoreAlbumId

                                // 当前项缩放效果
                                val isCurrentItem = carouselState.currentItem == page
                                val scale by animateFloatAsState(
                                    targetValue = if (isCurrentItem) 1.0f else 0.92f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                    label = "carousel_scale"
                                )

                                AlbumCard(
                                    albumName = albumInfo.name,
                                    albumArtist = artistName,
                                    trackCount = albumInfo.files.size,
                                    albumYear = albumInfo.year,
                                    albumArtPath = albumArtPath,
                                    albumId = albumId,
                                    onClick = { onNavigateToAlbumDetail(albumInfo.name, artistName) },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    // Songs 区域：直接平铺所有歌曲
                    if (files.isNotEmpty()) {
                        stickyHeader {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                SectionHeader(title = "Songs")
                            }
                        }

                        items(files, key = { "song_${it.path}" }) { audioFile ->
                            SongListItem(
                                audioFile = audioFile,
                                onClick = {
                                    onNavigateToMetadata(
                                        audioFile.path,
                                        createAlbumArtSharedElementKey(audioFile.path)
                                    )
                                }
                            )
                        }
                    }

                    // 空状态
                    if (files.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.no_tracks_for_artist),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 专辑信息数据类
 */
private data class AlbumInfo(
    val name: String,
    val files: List<com.voxly.domain.model.AudioFile>,
    val year: Int?
)

/**
 * 从字符串提取 4 位年份
 */
private fun extractYear(rawYear: String?): Int? {
    val normalized = rawYear?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return Regex("""\d{4}""").find(normalized)?.value?.toIntOrNull()
}
