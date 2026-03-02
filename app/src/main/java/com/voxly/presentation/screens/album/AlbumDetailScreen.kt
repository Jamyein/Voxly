package com.voxly.presentation.screens.album

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.screens.filebrowser.SimpleAudioFileItem
import com.voxly.presentation.viewmodel.AlbumDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Album detail screen showing album info and track list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    albumArtist: String?,
    onNavigateBack: () -> Unit,
    onNavigateToMetadata: (String) -> Unit
) {
    val viewModel: AlbumDetailViewModel = hiltViewModel()

    // Load album from cache
    LaunchedEffect(albumName, albumArtist) {
        viewModel.loadAlbum(albumName, albumArtist)
    }

    val albumNameState by viewModel.albumName.collectAsState()
    val albumArtistState by viewModel.albumArtist.collectAsState()
    val albumYear by viewModel.albumYear.collectAsState()
    val albumBitrate by viewModel.albumBitrate.collectAsState()
    val albumSampleRate by viewModel.albumSampleRate.collectAsState()
    val files by viewModel.files.collectAsState()
    val coverPath by viewModel.coverPath.collectAsState()

    val context = LocalContext.current
    val albumArtCache = remember { mutableMapOf<String, Bitmap?>() }

    // Calculate total duration
    val totalDuration = remember(files) {
        files.sumOf { it.duration }
    }
    val formattedTotalDuration = remember(totalDuration) {
        val hours = totalDuration / 3600000
        val minutes = (totalDuration % 3600000) / 60000
        val seconds = (totalDuration % 60000) / 1000
        if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    // Sort by disc number and track number
    val sortedFiles = remember(files) {
        files.sortedWith(
            compareBy({ it.metadata.discNumber ?: 1 }, { it.metadata.trackNumber ?: 0 })
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Large Card: Cover + Album Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Cover image
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstFile = files.firstOrNull()
                            val bitmap = remember(firstFile, coverPath) {
                                firstFile?.let { loadAlbumArtFromPath(context, it.path, coverPath) }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = stringResource(R.string.album_cover),
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .fillMaxSize(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Right: Album info
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = albumNameState,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = albumArtistState ?: stringResource(R.string.unknown_album_artist),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$albumBitrate kbps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "$albumSampleRate Hz",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Small Card: Statistics
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = stringResource(R.string.track_count, files.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = formattedTotalDuration,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = albumYear ?: "N/A",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Song list
            items(sortedFiles, key = { it.path }) { audioFile ->
                SimpleAudioFileItem(
                    audioFile = audioFile,
                    albumArtCache = albumArtCache,
                    isSelected = false,
                    onClick = { onNavigateToMetadata(audioFile.path) },
                    onLongClick = {}
                )
            }
        }
    }
}

private fun loadAlbumArtFromPath(context: android.content.Context, filePath: String, coverPath: String?): Bitmap? {
    return try {
        // First try embedded album art from the file using MediaMetadataRetriever
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                // Decode with sample size for large images
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)

                val targetSize = 300
                var sampleSize = 1
                while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
                    sampleSize *= 2
                }

                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                return android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
            }
        } catch (e: Exception) {
            // Ignore and try other methods
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Then try coverPath if available
        if (coverPath != null) {
            try {
                val coverFile = java.io.File(coverPath)
                if (coverFile.exists()) {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(coverPath, options)

                    val targetSize = 300
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
                        sampleSize *= 2
                    }

                    val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    return android.graphics.BitmapFactory.decodeFile(coverPath, decodeOptions)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        null
    } catch (e: Exception) {
        null
    }
}
