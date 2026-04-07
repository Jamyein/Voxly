package com.voxly.presentation.screens.metadata

import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource
import com.voxly.presentation.components.NetworkCoverImage
import com.voxly.presentation.theme.MaterialShapes
import androidx.compose.material3.toShape
import com.voxly.presentation.ui.clearSearchResultImageCache
import com.voxly.presentation.viewmodel.OnlineMetadataUiState
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.SearchProgressState
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineMetadataScreen(
    filePath: String,
    viewModel: OnlineMetadataViewModel,
    onNavigateBack: () -> Unit,
    onApplyMetadata: (AudioMetadata) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val selectedRelease by viewModel.selectedRelease.collectAsState()
    val selectedReleaseCandidate by viewModel.selectedReleaseCandidate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val downloadedAlbumArt by viewModel.downloadedAlbumArt.collectAsState()
    val isCoverArtTimeout by viewModel.isCoverArtTimeout.collectAsState()

    // Track if we've already triggered apply for the current selection
    var hasAppliedForCurrentSelection by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Track the selection ID to detect when selection changes
    var lastAppliedSelectionId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Auto-apply metadata when release or candidate is selected, and cover art is downloaded
    // Wait for cover art to be downloaded before applying (if cover is available)
    // If cover download times out, apply metadata without cover
    LaunchedEffect(selectedRelease, selectedReleaseCandidate, downloadedAlbumArt, isCoverArtTimeout) {
        val release = selectedRelease
        val candidate = selectedReleaseCandidate
        val albumArt = downloadedAlbumArt
        val isTimeout = isCoverArtTimeout
        val currentSelectionId = candidate?.id

        // Reset flag when selection changes
        if (currentSelectionId != null && currentSelectionId != lastAppliedSelectionId) {
            hasAppliedForCurrentSelection = false
        }

        // Apply when we have release details OR candidate selected
        // If candidate has cover art URL, wait until it's downloaded OR timeout
        val candidateHasCover = candidate?.coverArtUrl != null
        val isCoverDownloaded = !candidateHasCover || albumArt != null || isTimeout
        if ((release != null || candidate != null) && !hasAppliedForCurrentSelection && isCoverDownloaded) {
            hasAppliedForCurrentSelection = true
            lastAppliedSelectionId = currentSelectionId
            viewModel.applyMetadata()?.let { metadata ->
                Timber.d("OnlineMetadataScreen: auto applying metadata for ${metadata.title}, hasCover=${metadata.albumArt != null}")
                onApplyMetadata(metadata)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = query.title.ifBlank { stringResource(R.string.fetch_online_metadata) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!query.title.isBlank() && (!query.artist.isNullOrBlank() || !query.album.isNullOrBlank())) {
                            Text(
                                text = listOfNotNull(query.artist?.takeIf { it.isNotBlank() }, query.album?.takeIf { it.isNotBlank() }).joinToString(" • "),
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.autoSearch() },
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
                .padding(innerPadding)
                .padding(16.dp)
                .pointerInput(Unit) { } // Prevent touch events during exit animation
        ) {
            when (val state = uiState) {
                is OnlineMetadataUiState.Searching -> LoadingBox()
                is OnlineMetadataUiState.PartialResults -> {
                    OnlineReleaseList(
                        releases = state.releases,
                        onSelect = { release ->
                            hasAppliedForCurrentSelection = false
                            viewModel.selectRelease(release)
                            clearSearchResultImageCache()
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                is OnlineMetadataUiState.NoResults -> {
                    Text(
                        text = stringResource(R.string.error_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is OnlineMetadataUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is OnlineMetadataUiState.Results -> {
                    OnlineReleaseList(
                        releases = state.releases,
                        onSelect = { release ->
                            hasAppliedForCurrentSelection = false
                            viewModel.selectRelease(release)
                            clearSearchResultImageCache()
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                else -> {
                    OnlineReleaseList(
                        releases = searchResults,
                        onSelect = { release ->
                            hasAppliedForCurrentSelection = false
                            viewModel.selectRelease(release)
                            clearSearchResultImageCache()
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // 移除选择卡片UI - 点击直接应用
        }
    }
}


@Composable
private fun OnlineReleaseList(
    releases: List<OnlineRelease>,
    onSelect: (OnlineRelease) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverDimensions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    if (releases.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.error_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(releases) { release ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    onClick = { onSelect(release) }
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        ReleaseCover(
                            coverArtUrl = release.coverArtUrl,
                            modifier = Modifier.size(140.dp),
                            onDimensionsLoaded = { w, h ->
                                coverDimensions[release.id] = w to h
                            }
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Song: ${release.songTitle?.ifBlank { release.title.ifBlank { "-" } } ?: release.title.ifBlank { "-" } }",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Artist: ${release.artist.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Album: ${release.albumTitle?.ifBlank { "-" } ?: release.title.ifBlank { "-" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Source: ${displaySource(release)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val dims = coverDimensions[release.id]
                            if (dims != null) {
                                Text(
                                    text = "Cover: ${dims.first}×${dims.second}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchProgressIndicator(
    searchState: SearchProgressState,
    modifier: Modifier = Modifier
) {
    val allSources = listOf(OnlineSource.ITUNES, OnlineSource.QQ_MUSIC, OnlineSource.NETEASE, OnlineSource.MUSICBRAINZ)
    val startedSources = searchState.startedSources
    val completedSources = searchState.completedSources
    val errorSources = searchState.errorSources
    val isSearching = searchState.isSearching
    val isLyricsSearching = searchState.isLyricsSearching
    val resultCount = searchState.results.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Status line with source indicators
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                allSources.forEach { source ->
                    val isCompleted = source in completedSources
                    val hasError = source in errorSources

                    SourceStatusChip(
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

            // Progress text with wavy Linear Progress Indicator
            val statusText = buildString {
                append("已找到 $resultCount 个结果")
                if (isSearching) {
                    append("，正在搜索更多...")
                } else if (isLyricsSearching) {
                    append("，正在加载歌词...")
                }
            }

            // Indeterminate: unknown progress and wait time - wavy style
            // Determinate: known progress - fill from 0% to 100%
            // 使用实际搜索的源数量计算进度，而不是固定的4个源
            val hasKnownProgress = completedSources.isNotEmpty() || errorSources.isNotEmpty()
            val linearProgress = if (hasKnownProgress && startedSources.isNotEmpty()) {
                (completedSources.size + errorSources.size).toFloat() / startedSources.size.coerceAtLeast(1)
            } else {
                0f
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (hasKnownProgress) {
                    // Determinate: known progress - show exact progress (0% to 100%)
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
                    // Indeterminate: unknown progress and wait time - wavy style
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

            // Show errors if any
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
private fun SourceStatusChip(
    name: String,
    isCompleted: Boolean,
    hasError: Boolean
) {
    val color = when {
        hasError -> MaterialTheme.colorScheme.error
        isCompleted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = if (hasError) "$name ✗" else if (isCompleted) "$name ✓" else "$name...",
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun ReleaseCover(
    coverArtUrl: String?,
    modifier: Modifier = Modifier,
    onDimensionsLoaded: ((width: Int, height: Int) -> Unit)? = null
) {
    NetworkCoverImage(
        url = coverArtUrl,
        contentDescription = "Album cover",
        modifier = modifier.clip(MaterialShapes.Cookie9Sided.toShape()),
        onDimensionsLoaded = onDimensionsLoaded
    )
}

private fun displaySource(release: OnlineRelease): String {
    if (release.source != OnlineSource.UNKNOWN) return release.source.toDisplayString()
    val format = release.format.orEmpty().lowercase()
    val cover = release.coverArtUrl.orEmpty().lowercase()
    return when {
        format.contains("itunes") || format.contains("apple") -> "iTunes"
        cover.contains("y.gtimg.cn") -> "QQ Music"
        cover.contains("music.126.net") || cover.contains("netease") -> "NetEase"
        release.id.contains("-") -> "MusicBrainz"
        else -> "Unknown"
    }
}
