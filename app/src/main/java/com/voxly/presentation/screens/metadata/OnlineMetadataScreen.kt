package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.domain.repository.OnlineRelease
import com.voxly.presentation.ui.loadImageBitmapFromUrl
import com.voxly.presentation.viewmodel.OnlineMetadataUiState
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import com.voxly.presentation.ui.loadImageBitmapFromUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMetadataScreen(
    filePath: String,
    viewModel: OnlineMetadataViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedRelease by viewModel.selectedRelease.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

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
                else -> {
                    OnlineReleaseList(
                        releases = searchResults,
                        onSelect = { viewModel.selectRelease(it) }
                    )
                }
            }

            if (selectedRelease != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Album: ${selectedRelease?.title.orEmpty()}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = selectedRelease?.artist.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Tracks: ${selectedRelease?.trackCount ?: 0}  File: ${filePath.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
    onSelect: (OnlineRelease) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(releases) { release ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelect(release) }
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    ReleaseCover(
                        coverArtUrl = release.coverArtUrl,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Song: ${(release.songTitle ?: release.title).ifBlank { "-" }}",
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
                            text = "Album: ${(release.albumTitle ?: release.title).ifBlank { "-" }}",
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
