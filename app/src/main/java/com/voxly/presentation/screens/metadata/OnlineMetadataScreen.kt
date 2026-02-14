package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.domain.repository.OnlineRelease
import com.voxly.presentation.viewmodel.OnlineMetadataUiState
import com.voxly.presentation.viewmodel.OnlineMetadataViewModel

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

    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Online Metadata") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
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
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Artist") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Album") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { viewModel.searchByArtistAlbum(artist, album) },
                    enabled = artist.isNotBlank() && album.isNotBlank() && !isLoading
                ) {
                    Text("Search Artist/Album")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Track Title") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        viewModel.searchByTrack(
                            title = title,
                            artist = artist.takeIf { it.isNotBlank() }
                        )
                    },
                    enabled = title.isNotBlank() && !isLoading
                ) {
                    Text("Search Track")
                }
            }

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
                        onSelect = { viewModel.getReleaseDetails(it.id) }
                    )
                }
            }

            if (selectedRelease != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = selectedRelease?.title.orEmpty(),
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
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = release.artist,
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
