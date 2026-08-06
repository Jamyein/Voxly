package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.repository.OnlineRelease
import com.voxly.domain.repository.OnlineSource
import com.voxly.presentation.components.NetworkCoverImage
import com.voxly.presentation.components.SourceTag
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyScaffold
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.theme.MaterialShapes
import com.voxly.presentation.theme.emphasizedTitleMedium
import androidx.compose.material3.toShape
import com.voxly.presentation.viewmodel.OnlineMetadataUiState
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlineMetadataScreen(
    filePath: String,
    viewModel: OnlineMetadataViewModel,
    onNavigateBack: () -> Unit,
    onApplyMetadata: (AudioMetadata) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val selectedRelease by viewModel.selectedRelease.collectAsStateWithLifecycle()
    val selectedReleaseCandidate by viewModel.selectedReleaseCandidate.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle(initialValue = null)
    val downloadedAlbumArt by viewModel.downloadedAlbumArt.collectAsStateWithLifecycle()
    val isCoverArtTimeout by viewModel.isCoverArtTimeout.collectAsStateWithLifecycle()

    // Auto-apply metadata when release or candidate is selected, and cover art is downloaded
    // Wait for cover art to be downloaded before applying (if cover is available)
    // If cover download times out, apply metadata without cover
    // Note: This LaunchedEffect runs once per selection because selectedReleaseCandidate 
    // changes only when user clicks a different item
    LaunchedEffect(selectedRelease, selectedReleaseCandidate, downloadedAlbumArt, isCoverArtTimeout) {
        val release = selectedRelease
        val candidate = selectedReleaseCandidate
        val albumArt = downloadedAlbumArt
        val isTimeout = isCoverArtTimeout

        // Apply when we have release details OR candidate selected
        // If candidate has cover art URL, wait until it's downloaded OR timeout
        val candidateHasCover = candidate?.coverArtUrl != null
        val isCoverDownloaded = !candidateHasCover || albumArt != null || isTimeout
        if ((release != null || candidate != null) && isCoverDownloaded) {
            viewModel.applyMetadata()?.let { metadata ->
                Timber.d("OnlineMetadataScreen: auto applying metadata for ${metadata.title}, hasCover=${metadata.albumArt != null}")
                onApplyMetadata(metadata)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    VoxlyScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            VoxlyTopAppBar(
                theme = TopBarTheme.Library,
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
                onBack = onNavigateBack,
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
        // Edge-to-edge convention: top inset only from the Scaffold; the bottom nav-bar
        // space is reserved explicitly at the end of the column.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is OnlineMetadataUiState.Searching -> LoadingBox()
                is OnlineMetadataUiState.PartialResults -> {
                    OnlineReleaseList(
                        releases = state.releases,
                        onSelect = { release ->
                            viewModel.selectRelease(release)
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
                            viewModel.selectRelease(release)
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                else -> {
                    OnlineReleaseList(
                        releases = searchResults,
                        onSelect = { release ->
                            viewModel.selectRelease(release)
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // Trailing space equal to the navigation-bar inset so the last release card
            // clears the gesture / 3-button nav area (edge-to-edge convention).
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}


@Composable
private fun OnlineReleaseList(
    releases: List<OnlineRelease>,
    onSelect: (OnlineRelease) -> Unit,
    modifier: Modifier = Modifier
) {
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
            items(releases, key = { it.id }) { release ->
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
                            modifier = Modifier.size(140.dp)
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            val songTitle = release.songTitle?.ifBlank { release.title.ifBlank { "-" } }
                                ?: release.title.ifBlank { "-" }
                            // 只在有真实专辑名时显示，不 fallback 到 title（title 可能是歌名，与 songTitle 混淆会误隐藏）
                            val albumTitle = release.albumTitle?.takeIf { it.isNotBlank() }
                            Text(
                                text = songTitle,
                                style = emphasizedTitleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildString {
                                    release.artist.ifBlank { null }?.let { append(it) }
                                    if (albumTitle != null && albumTitle != songTitle) {
                                        if (isNotEmpty()) append(" · ")
                                        append(albumTitle)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SourceTag(text = displaySource(release))
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
private fun ReleaseCover(
    coverArtUrl: String?,
    modifier: Modifier = Modifier
) {
    NetworkCoverImage(
        url = coverArtUrl,
        contentDescription = "Album cover",
        modifier = modifier.clip(MaterialShapes.Cookie9Sided.toShape())
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
