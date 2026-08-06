package com.voxly.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.presentation.viewmodel.LibraryScanViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

/**
 * Inline library search: a panel that slides in directly UNDER the screen's top
 * bar and overlays the page content (replaces the old ModalBottomSheet search).
 *
 * Single search path — self-wires [LibraryScanViewModel.searchFiles] (the
 * unified, filter-respecting, async library search), same as every other entry
 * point. The caller places this in the screen's content area below the top bar
 * (e.g. `Modifier.matchParentSize()` inside the content Box); when [visible] it
 * covers that area with a search field + results list. The top bar stays
 * visible and untouched (the caller may force it expanded via
 * `scrollBehavior.state.heightOffset = 0f` so the panel position is stable).
 *
 * Closing: [onDismiss] is also triggered by the system back button while
 * [visible]. State (query/results) resets on every open; the field auto-focuses
 * and brings up the IME.
 *
 * The search field follows ReadYou's flow-page search bar style: a lifted pill
 * (CircleShape + tonal elevation) on the `surface` color hosting a transparent
 * TextField (no outline/container/indicator) with a Search icon left and a
 * Close (dismiss) button right, bodyLarge text, IME action Done.
 */
@Composable
fun InlineLibrarySearchOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onFileClick: (AudioFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanViewModel: LibraryScanViewModel = hiltViewModel()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioFile>>(emptyList()) }
    var searchCompleted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Debounced query — 250ms after the last keystroke. A single character is
    // enough to search (CJK single-char titles/artists must be findable).
    val debouncedQuery by produceState(
        initialValue = "",
        key1 = query
    ) {
        if (query.isEmpty()) {
            value = ""
            return@produceState
        }
        delay(250)
        value = query
    }

    LaunchedEffect(debouncedQuery) {
        if (debouncedQuery.isEmpty()) {
            results = emptyList()
            searchCompleted = false
        } else {
            searchCompleted = false
            scanViewModel.searchFiles(debouncedQuery).collect { found ->
                results = found
                searchCompleted = true
            }
        }
    }

    // Fresh search + auto-focus + keyboard on every open.
    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            results = emptyList()
            searchCompleted = false
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    val focusManager = LocalFocusManager.current

    // System back closes the search first, before navigating.
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(220)) + fadeIn(tween(220)),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(180)) + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // ReadYou-style search bar: a lifted pill (CircleShape + tonal
                // elevation) containing a transparent TextField — no outline, no
                // container color, no indicator — with a Search icon on the left
                // and a Close (dismiss) button on the right.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(56.dp),
                    shape = CircleShape,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                modifier = Modifier.padding(start = 16.dp),
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextField(
                                modifier = Modifier
                                    .height(56.dp)
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                colors = TextFieldDefaults.colors(
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                value = query,
                                onValueChange = { query = it },
                                placeholder = {
                                    Text(
                                        modifier = Modifier.alpha(0.7f),
                                        text = stringResource(R.string.file_search_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyLarge,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                )
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Search results / states
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        results.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = libraryContentPadding(bottomGap = 24.dp)
                            ) {
                                items(results, key = { it.path }) { audioFile ->
                                    SearchResultItem(
                                        audioFile = audioFile,
                                        onClick = { onFileClick(audioFile) }
                                    )
                                }
                            }
                        }

                        debouncedQuery.isNotEmpty() && searchCompleted -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.file_search_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        query.isNotEmpty() -> {
                            // Debounce in progress (or first query still running).
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.file_search_searching),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.search_tip),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search result item composable for displaying audio file in search results.
 * Supports Container Transform transition to metadata editor.
 *
 * @param audioFile The audio file to display
 * @param onClick Callback when the item is clicked
 * @param modifier Modifier for the item
 */
@Composable
fun SearchResultItem(
    audioFile: AudioFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album artwork with shared container transition
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    filePath = audioFile.path,
                    albumId = audioFile.mediaStoreAlbumId,
                    contentDescription = stringResource(R.string.cd_album_art),
                    size = 48.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audioFile.metadata.getDisplayTitle(audioFile.name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOfNotNull(
                    audioFile.metadata.artist,
                    audioFile.metadata.album
                ).joinToString(" - ")

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
