package com.voxly.presentation.screens.metadata

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import com.voxly.presentation.theme.ExpressiveMotionTokens
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.presentation.components.NetworkAlbumArtImage
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineCoverSearchScreen(
    filePath: String,
    viewModel: OnlineCoverSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onCoverSelected: (ByteArray) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val coverResults by viewModel.coverResults.collectAsState()
    var isSelectingCover by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Start search on screen load
    LaunchedEffect(filePath) {
        viewModel.search(filePath)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fetch_online_cover_art)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.search(filePath) },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Search Again")
                    }
                }
            )
        }
    ) { innerPadding ->
        // Content with innerPadding from Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(16.dp)
                .pointerInput(Unit) { } // Prevent touch events during exit animation
        ) {
            // Search info card - 使用 Surface 容器 + tertiary 颜色点缀
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                ) + scaleIn(
                    initialScale = 0.95f,
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔍",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = stringResource(R.string.search_query),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Text(
                            text = viewModel.searchTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        viewModel.searchArtist?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Search progress - 使用弹性缩放动画
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                ) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            // Error message - 使用 Surface 容器
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                ) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                errorMessage?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 1.dp
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Results - 无结果提示
            AnimatedVisibility(
                visible = coverResults.isEmpty() && !isLoading && errorMessage == null,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                ) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(R.string.error_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Results - 封面列表
            AnimatedVisibility(
                visible = coverResults.isNotEmpty(),
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                ) + slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(coverResults) { item ->
                        CoverResultItem(
                            item = item,
                            isLoading = isSelectingCover,
                            onClick = {
                                isSelectingCover = true
                                coroutineScope.launch {
                                    viewModel.getCoverBytes(item)?.let { bytes ->
                                        onCoverSelected(bytes)
                                    }
                                    isSelectingCover = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoverResultItem(
    item: OnlineRecording,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    // 交替使用不同颜色容器
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CoverThumbnail(
                    coverArtUrl = item.coverArtUrl,
                    modifier = Modifier.size(100.dp)
                )
                if (isLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 来源标签使用 tertiary 颜色
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = item.source.toDisplayString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverThumbnail(
    coverArtUrl: String?,
    modifier: Modifier = Modifier
) {
    NetworkAlbumArtImage(
        url = coverArtUrl,
        contentDescription = "Album cover",
        modifier = modifier
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "No cover art",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
