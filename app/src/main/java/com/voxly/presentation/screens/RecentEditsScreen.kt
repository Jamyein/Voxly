package com.voxly.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.repository.RecentEdit
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.viewmodel.RecentEditsUiState
import com.voxly.presentation.viewmodel.RecentEditsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen showing recent edit history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentEditsScreen(
    outerPadding: PaddingValues = PaddingValues(),
    onNavigateToMetadata: (String, String?) -> Unit,
    viewModel: RecentEditsViewModel = hiltViewModel(),
    bottomNavScrollProgress: Float = 0f,
    onBottomBarScrollProgressChange: (Float) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_edits_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                )
        ) {
            when (val state = uiState) {
                is RecentEditsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is RecentEditsUiState.Empty -> {
                    EmptyRecentEditsContent()
                }

                is RecentEditsUiState.Success -> {
                    RecentEditsList(
                        edits = state.edits,
                        onItemClick = onNavigateToMetadata,
                        bottomPadding = outerPadding.calculateBottomPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentEditsList(
    edits: List<RecentEdit>,
    onItemClick: (String, String?) -> Unit,
    bottomPadding: Dp = 0.dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 8.dp + bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(edits, key = { it.filePath + it.timestamp }) { edit ->
            RecentEditItem(
                edit = edit,
                onClick = { onItemClick(edit.filePath, "cover_${edit.filePath.hashCode()}") }
            )
        }
    }
}

@Composable
private fun RecentEditItem(
    edit: RecentEdit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Title / File name
                Text(
                    text = edit.newMetadata.title ?: edit.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Artist - Album
                val subtitle = buildString {
                    edit.newMetadata.artist?.let { append(it) }
                    edit.newMetadata.album?.let {
                        if (isNotEmpty()) append(" - ")
                        append(it)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Timestamp
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(edit.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Compare icon (showing changes)
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EmptyRecentEditsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = appIconPainter(AppIcon.History),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.recent_edits_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.recent_edits_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formats timestamp to human-readable format.
 * Shows "Today", "Yesterday", or actual date.
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val editTime = Calendar.getInstance().apply { timeInMillis = timestamp }

    return when {
        isSameDay(now, editTime) -> {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            "Today ${timeFormat.format(Date(timestamp))}"
        }
        isYesterday(now, editTime) -> {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            "Yesterday ${timeFormat.format(Date(timestamp))}"
        }
        else -> {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(now: Calendar, other: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, other)
}
