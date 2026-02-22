package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.OnlineRelease
import com.voxly.presentation.ui.clearSearchResultImageCache
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.viewmodel.OnlineMetadataUiState
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.viewmodel.SearchProgressState
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMetadataScreen(
    filePath: String,
    viewModel: OnlineMetadataViewModel = hiltViewModel(),
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

    // Track if we've already triggered apply for the current selection
    var hasAppliedForCurrentSelection by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Auto-apply metadata when album art is downloaded
    LaunchedEffect(downloadedAlbumArt, selectedRelease, selectedReleaseCandidate) {
        val albumArt = downloadedAlbumArt
        val release = selectedRelease
        val candidate = selectedReleaseCandidate
        // Only apply when we have release details OR candidate with album art ready
        if ((release != null || candidate != null) && albumArt != null && !hasAppliedForCurrentSelection) {
            hasAppliedForCurrentSelection = true
            viewModel.applyMetadata()?.let { metadata ->
                Timber.d("OnlineMetadataScreen: auto applying metadata with cover for ${metadata.title}")
                onApplyMetadata(metadata)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online Metadata") },
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
                        onClick = { viewModel.autoSearch() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Search Again")
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            QuerySummaryCard(
                title = query.title,
                artist = query.artist,
                album = query.album,
                fromTags = query.fromTags
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is OnlineMetadataUiState.Searching -> LoadingBox()
                is OnlineMetadataUiState.PartialResults -> {
                    SearchProgressIndicator(searchState = searchState)
                    Spacer(modifier = Modifier.height(8.dp))
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
                        text = "No results found.",
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
private fun QuerySummaryCard(
    title: String,
    artist: String?,
    album: String?,
    fromTags: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (fromTags) "Auto query source: tags (priority)" else "Auto query source: file name fallback",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Title: ${title.ifBlank { "-" }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Artist: ${artist?.ifBlank { "-" } ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Album: ${album?.ifBlank { "-" } ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun OnlineReleaseList(
    releases: List<OnlineRelease>,
    onSelect: (OnlineRelease) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(releases) { release ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(release) },
                shape = MaterialTheme.shapes.medium
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    ReleaseCover(
                        coverArtUrl = release.coverArtUrl,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Song: ${release.songTitle?.ifBlank { release.title?.ifBlank { "-" } } ?: release.title?.ifBlank { "-" } ?: "-"}",
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
                            text = "Album: ${release.albumTitle?.ifBlank { "-" } ?: release.title?.ifBlank { "-" } ?: "-"}",
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
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun SearchProgressIndicator(
    searchState: SearchProgressState,
    modifier: Modifier = Modifier
) {
    val allSources = listOf("iTunes", "QQ Music", "NetEase", "MusicBrainz")
    val completedSources = searchState.completedSources
    val errorSources = searchState.errorSources
    val isSearching = searchState.isSearching
    val isLyricsSearching = searchState.isLyricsSearching
    val resultCount = searchState.results.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
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
                        name = source,
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

            // Progress text
            val statusText = buildString {
                append("已找到 $resultCount 个结果")
                if (isSearching) {
                    append("，正在搜索更多...")
                } else if (isLyricsSearching) {
                    append("，正在加载歌词...")
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = coverArtUrl) {
        value = loadImageBitmapFromUrl(coverArtUrl)
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!,
            contentDescription = "Album cover",
            modifier = modifier
        )
    } else {
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

private fun displaySource(release: OnlineRelease): String {
    if (release.source != "Unknown") return release.source
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
