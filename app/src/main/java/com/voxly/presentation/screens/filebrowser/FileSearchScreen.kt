package com.voxly.presentation.screens.filebrowser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.ui.loadLocalAlbumArt
import com.voxly.presentation.viewmodel.FileSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Collator for Chinese pinyin sorting */
private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
    strength = Collator.PRIMARY
}

enum class SearchSortOption {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    DURATION_DESC
}

private fun SearchSortOption.labelResId(): Int = when (this) {
    SearchSortOption.NAME_ASC -> R.string.file_sort_name_asc
    SearchSortOption.NAME_DESC -> R.string.file_sort_name_desc
    SearchSortOption.SIZE_DESC -> R.string.file_sort_size_desc
    SearchSortOption.DURATION_DESC -> R.string.file_sort_duration_desc
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSearchScreen(
    filePaths: List<String>,
    onNavigateBack: () -> Unit,
    onFileSelected: (String) -> Unit,
    viewModel: FileSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by rememberSaveable { mutableStateOf(SearchSortOption.NAME_ASC.name) }
    var isSortExpanded by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Load audio files from the cache based on paths
    val audioFiles by viewModel.getAudioFilesForPaths(filePaths).collectAsState(initial = emptyList())

    val filteredFiles = remember(audioFiles, searchQuery, sortOption) {
        applySearchAndSort(
            files = audioFiles,
            query = searchQuery,
            sortOption = SearchSortOption.valueOf(sortOption)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.file_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.file_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_selection)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sort button and menu
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = { isSortExpanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.file_sort_label),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(SearchSortOption.valueOf(sortOption).labelResId()))
                }

                DropdownMenu(
                    expanded = isSortExpanded,
                    onDismissRequest = { isSortExpanded = false }
                ) {
                    SearchSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelResId())) },
                            leadingIcon = if (option.name == sortOption) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = stringResource(R.string.cd_selected),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                sortOption = option.name
                                isSortExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results count
            Text(
                text = stringResource(R.string.file_search_results_count, filteredFiles.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // File list or empty state
            if (filteredFiles.isEmpty()) {
                EmptySearchState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredFiles, key = { it.path }) { audioFile ->
                        SearchResultItem(
                            audioFile = audioFile,
                            onClick = { onFileSelected(audioFile.path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.file_search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    audioFile: AudioFile,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                val albumArtBitmap by produceState<Bitmap?>(
                    initialValue = null,
                    key1 = audioFile.path
                ) {
                    value = withContext(Dispatchers.IO) {
                        loadLocalAlbumArt(audioFile.path)
                    }
                }

                val bitmap = albumArtBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = appIconPainter(AppIcon.MusicNote),
                        contentDescription = stringResource(R.string.cd_no_cover),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(audioFile.metadata.artist ?: "")
                        audioFile.metadata.album?.let { append(" - $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(audioFile.format)
                        append(" • ")
                        append(audioFile.getFormattedDuration())
                        append(" • ")
                        append(audioFile.getFormattedSize())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Loads album art from the audio file.
 * Returns null if no album art is found.
 */
private fun loadAlbumArt(
    context: Context,
    audioFile: AudioFile
): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(audioFile.path)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                decodeThumbnailBitmap(artBytes)
            } else {
                null
            }
        } finally {
            retriever.release()
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Decodes and scales down the bitmap to prevent OOM.
 */
private fun decodeThumbnailBitmap(artBytes: ByteArray): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)

        // Calculate sample size (target 96x96)
        val targetSize = 96
        var sampleSize = 1
        while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
    } catch (e: Exception) {
        null
    }
}

private fun applySearchAndSort(
    files: List<AudioFile>,
    query: String,
    sortOption: SearchSortOption
): List<AudioFile> {
    val normalizedQuery = query.trim().lowercase()
    val filtered = if (normalizedQuery.isBlank()) {
        files
    } else {
        files.filter { audioFile ->
            val title = audioFile.metadata.title.orEmpty()
            val artist = audioFile.metadata.artist.orEmpty()
            val album = audioFile.metadata.album.orEmpty()
            listOf(audioFile.name, title, artist, album).any { text ->
                text.lowercase().contains(normalizedQuery)
            }
        }
    }

    return when (sortOption) {
        SearchSortOption.NAME_ASC -> filtered.sortedWith(compareBy(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        SearchSortOption.NAME_DESC -> filtered.sortedWith(compareByDescending(chineseCollator) { it.metadata.getDisplayTitle(it.name).lowercase() })
        SearchSortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        SearchSortOption.DURATION_DESC -> filtered.sortedByDescending { it.duration }
    }
}
