package com.voxly.presentation.screens.metadata

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.OnlineRecording
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    // Start search on screen load
    LaunchedEffect(filePath) {
        viewModel.search(filePath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fetch_online_cover_art)) },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Search query:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Title: ${viewModel.searchTitle}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    viewModel.searchArtist?.let { artist ->
                        Text(
                            text = "Artist: $artist",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Search progress
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Results
            AnimatedVisibility(
                visible = coverResults.isEmpty() && !isLoading && errorMessage == null,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                        stiffness = ExpressiveMotionTokens.Emphasized.stiffness
                    )
                )
            ) {
                Text(
                    text = stringResource(R.string.error_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(coverResults) { item ->
                        CoverResultItem(
                            item = item,
                            onClick = {
                                // Use sync method to get cover bytes
                                kotlinx.coroutines.runBlocking {
                                    viewModel.getCoverBytes(item)?.let { bytes ->
                                        onCoverSelected(bytes)
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverResultItem(
    item: OnlineRecording,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverThumbnail(
                coverArtUrl = item.coverArtUrl,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${item.artist} • ${item.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoverThumbnail(
    coverArtUrl: String?,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = coverArtUrl) {
        value = loadImageBitmapFromUrl(coverArtUrl)
    }

    AnimatedVisibility(
        visible = bitmap != null,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                stiffness = ExpressiveMotionTokens.Emphasized.stiffness
            )
        )
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Album cover",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }
    }

    AnimatedVisibility(
        visible = bitmap == null,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
                stiffness = ExpressiveMotionTokens.Emphasized.stiffness
            )
        )
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
