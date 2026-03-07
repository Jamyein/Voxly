package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import com.voxly.presentation.theme.ExpressiveMotionTokens
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricsSearchScreen(
    filePath: String,
    viewModel: OnlineLyricsSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onLyricsSelected: (String) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lyricsResults by viewModel.lyricsResults.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    var isFetchingLyrics by remember { mutableStateOf(false) }
    var fetchingItemId by remember { mutableStateOf<Long?>(null) }
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
                title = { Text(stringResource(R.string.search_online_lyrics)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { }, // Prevent touch events during exit animation
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding(),
                start = 0.dp,
                end = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search info card - 使用 Surface 容器 + tertiary 颜色点缀
            item {
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
                        color = MaterialTheme.colorScheme.secondaryContainer,
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
                                    text = "🎵",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = stringResource(R.string.search_query),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = viewModel.searchTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            viewModel.searchArtist?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            viewModel.searchAlbum?.let { album ->
                                Text(
                                    text = album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Search progress - 使用弹性缩放动画
            if (searchState.isSearching || isLoading) {
                item {
                    AnimatedVisibility(
                        visible = true,
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
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Error message - 使用 Surface 容器
            item {
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
            }

            // Results - 无结果提示
            if (lyricsResults.isEmpty() && !isLoading && errorMessage == null) {
                item {
                    AnimatedVisibility(
                        visible = true,
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
                }
            }

            if (lyricsResults.isNotEmpty()) {
                items(lyricsResults, key = { it.id }) { item ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness)) + slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness)
                        )
                    ) {
                        LyricsResultItem(
                            item = item,
                            isLoading = isFetchingLyrics && fetchingItemId == item.id,
                            onClick = {
                                isFetchingLyrics = true
                                fetchingItemId = item.id
                                coroutineScope.launch {
                                    val lyricsText = viewModel.getLyricsContent(item)
                                    isFetchingLyrics = false
                                    fetchingItemId = null
                                    if (lyricsText != null) {
                                        onLyricsSelected(lyricsText)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsResultItem(
    item: OnlineLyricsResult,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.trackName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Loading indicator
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).padding(start = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // LRC 标签使用 tertiary 颜色
                if (item.hasSyncedLyrics && !isLoading) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "LRC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = "${item.artistName} • ${item.albumName ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 来源标签
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
