package com.voxly.presentation.screens.metadata

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Refresh
import com.voxly.presentation.theme.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.OnlineRecording
import com.voxly.domain.repository.OnlineSource
import com.voxly.presentation.components.NetworkCoverImage
import com.voxly.presentation.theme.ExpressiveAnimations
import com.voxly.presentation.viewmodel.CoverSearchProgressState
import com.voxly.presentation.viewmodel.OnlineCoverSearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineCoverSearchScreen(
    filePath: String,
    viewModel: OnlineCoverSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onCoverSelected: (ByteArray) -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle(initialValue = null)
    val searchProgress by viewModel.searchProgressState.collectAsStateWithLifecycle()
    val coverResults = searchProgress.results
    val searchTitle by viewModel.searchTitle.collectAsStateWithLifecycle()
    val searchArtist by viewModel.searchArtist.collectAsStateWithLifecycle()
    var selectingCoverId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(filePath) {
        viewModel.search(filePath)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = searchTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        searchArtist?.takeIf { it.isNotBlank() }?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(16.dp)
        ) {
                    AnimatedVisibility(
                        visible = isLoading && coverResults.isEmpty(),
                        enter = ExpressiveAnimations.FadeEnter,
                        exit = ExpressiveAnimations.FadeExit
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
         }

         AnimatedVisibility(
             visible = coverResults.isNotEmpty(),
             enter = ExpressiveAnimations.ListItemEnter,
             exit = ExpressiveAnimations.FadeExit
         ) {
             SearchProgressIndicatorForCover(
                 searchState = searchProgress,
                 modifier = Modifier.padding(bottom = 8.dp)
             )
         }

                      AnimatedVisibility(
                          visible = coverResults.isNotEmpty(),
                          enter = ExpressiveAnimations.ListItemEnter,
                          exit = ExpressiveAnimations.FadeExit
                 ) {
             LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = coverResults,
                        key = { it.id }
                    ) { item ->
                        CoverResultItem(
                            item = item,
                            isLoading = selectingCoverId == item.id,
                            onClick = {
                                selectingCoverId = item.id
                                coroutineScope.launch {
                                    viewModel.getCoverBytes(item)?.let { bytes ->
                                        onCoverSelected(bytes)
                                    }
                                    selectingCoverId = null
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
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                // Source tag - unified with tertiary color scheme
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
    NetworkCoverImage(
        url = coverArtUrl,
        contentDescription = "Album cover",
        modifier = modifier.clip(MaterialShapes.SoftBurst.toShape())
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchProgressIndicatorForCover(
    searchState: CoverSearchProgressState,
    modifier: Modifier = Modifier
) {
    val allSources = listOf(OnlineSource.ITUNES, OnlineSource.QQ_MUSIC, OnlineSource.NETEASE, OnlineSource.MUSICBRAINZ)
    val startedSources = searchState.startedSources
    val completedSources = searchState.completedSources
    val errorSources = searchState.errorSources
    val isSearching = searchState.isSearching
    val resultCount = searchState.results.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                allSources.forEach { source ->
                    val isCompleted = source in completedSources
                    val hasError = source in errorSources

                    SourceStatusChipForCover(
                        name = source.toDisplayString(),
                        isCompleted = isCompleted,
                        hasError = hasError
                    )

                    if (source != allSources.last()) {
                        Text(
                            text = " ",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val statusText = if (isSearching) {
                stringResource(R.string.search_results_count_with_more, resultCount)
            } else {
                stringResource(R.string.search_results_count, resultCount)
            }

            val hasKnownProgress = completedSources.isNotEmpty() || errorSources.isNotEmpty()
            val linearProgress = if (hasKnownProgress && startedSources.isNotEmpty()) {
                (completedSources.size + errorSources.size).toFloat() / startedSources.size.coerceAtLeast(1)
            } else {
                0f
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hasKnownProgress) {
                    LinearProgressIndicator(
                        progress = { linearProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(MaterialTheme.shapes.extraSmall),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        wavelength = 20.dp
                    )
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 150.dp)
                )
            }

            if (errorSources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                errorSources.forEach { (source, error) ->
                    Text(
                        text = "$source: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceStatusChipForCover(
    name: String,
    isCompleted: Boolean,
    hasError: Boolean
) {
    val color = when {
        hasError -> MaterialTheme.colorScheme.error
        isCompleted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val statusSymbol = when {
        hasError -> stringResource(R.string.source_status_error)
        isCompleted -> stringResource(R.string.source_status_completed)
        else -> stringResource(R.string.source_status_searching)
    }

    Text(
        text = "$name $statusSymbol",
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
