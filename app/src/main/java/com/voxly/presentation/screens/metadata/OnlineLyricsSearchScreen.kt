package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.voxly.presentation.components.ExpressiveCard
import com.voxly.presentation.components.ExpressiveScrollableContentContainer
import com.voxly.presentation.components.ExpressiveScaffoldWithBack
import com.voxly.presentation.theme.ContainerLevel
import com.voxly.presentation.theme.ExpressiveMotionTokens
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.OnlineLyricsResult
import com.voxly.presentation.viewmodel.OnlineLyricsSearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLyricsSearchScreen(
    filePath: String,
    viewModel: OnlineLyricsSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onLyricsSelected: (OnlineLyricsResult) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val lyricsResults by viewModel.lyricsResults.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    // Start search on screen load
    LaunchedEffect(filePath) {
        viewModel.search(filePath)
    }

    ExpressiveScaffoldWithBack(
        title = stringResource(R.string.search_online_lyrics),
        onBackClick = onNavigateBack,
        actions = {
            IconButton(
                onClick = { viewModel.search(filePath) },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Search Again")
            }
        }
    ) {
        ExpressiveScrollableContentContainer(
            modifier = Modifier.fillMaxSize(),
            containerLevel = ContainerLevel.Medium,
            contentPadding = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search info card
                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerLevel = ContainerLevel.Low
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
                        viewModel.searchAlbum?.let { album ->
                            Text(
                                text = "Album: $album",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Search progress
                if (searchState.isSearching || isLoading) {
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
                    enter = fadeIn(animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness))
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
                    visible = lyricsResults.isEmpty() && !isLoading && errorMessage == null,
                    enter = fadeIn(animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness))
                ) {
                    Text(
                        text = stringResource(R.string.error_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = lyricsResults.isNotEmpty(),
                    enter = fadeIn(animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness)) + slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio, stiffness = ExpressiveMotionTokens.Emphasized.stiffness)
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(lyricsResults, key = { it.id }) { item ->
                            LyricsResultItem(
                                item = item,
                                onClick = {
                                    onLyricsSelected(item)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsResultItem(
    item: OnlineLyricsResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier.then(Modifier.fillMaxWidth().padding(vertical = 4.dp)),
        containerLevel = ContainerLevel.Low,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.trackName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${item.artistName} • ${item.albumName ?: "-"} • ${item.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.hasSyncedLyrics) {
                Text(
                    text = "LRC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
