package com.voxly.presentation.screens.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val title = if (uiState.selectedLogFile == null) {
        stringResource(R.string.log_viewer_title)
    } else {
        uiState.selectedLogFile!!.name
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val fabVisible = uiState.selectedLogFile == null && uiState.logFiles.isNotEmpty()

    PredictiveBackHandler(enabled = uiState.selectedLogFile != null) { progress ->
        try {
            progress.collect { }
            viewModel.clearSelectedLog()
        } catch (e: CancellationException) {
            // Gesture cancelled - no action
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedLogFile != null) {
                        IconButton(onClick = {
                            val content = viewModel.getCopyableContent()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Voxly Log", content)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.log_viewer_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.log_viewer_copy)
                            )
                        }
                        IconButton(onClick = {
                            viewModel.shareLog(context, viewModel.getFilteredLogs().joinToString("\n"))
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.log_viewer_share)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.log_viewer_delete)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.animateFloatingActionButton(
                    visible = fabVisible,
                    alignment = Alignment.BottomEnd
                ),
                onClick = {
                    viewModel.exportLogs(context) { uri ->
                        if (uri != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Logs"))
                        } else {
                            Toast.makeText(context, "Failed to export logs", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Archive, contentDescription = null)
            }
        }
    ) { innerPadding ->
        // Content with innerPadding from Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            if (uiState.selectedLogFile == null) {
                LogFileList(
                    logFiles = uiState.logFiles,
                    isLoading = uiState.isLoading,
                    onFileClick = { viewModel.selectLogFile(it) }
                )
            } else {
                LogContent(
                    viewModel = viewModel,
                    listState = listState
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(R.string.log_viewer_delete)) },
            text = { Text(stringResource(R.string.log_viewer_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    uiState.selectedLogFile?.let { viewModel.deleteLogFile(it) }
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogFileList(
    logFiles: List<LogFileItem>,
    isLoading: Boolean,
    onFileClick: (LogFileItem) -> Unit
) {
    if (isLoading && logFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else if (logFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.log_viewer_no_logs),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logFiles, key = { it.name }) { item ->
                LogFileCard(item = item, onClick = { onFileClick(item) })
            }
        }
    }
}

@Composable
private fun LogFileCard(
    item: LogFileItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.lastModified,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogContent(
    viewModel: LogViewerViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = viewModel::setSearchQuery,
            selectedFilter = uiState.selectedFilter,
            onFilterChange = viewModel::setFilter
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            val filteredLogs = viewModel.getFilteredLogs()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredLogs, key = { idx, line -> idx to line }) { _, line ->
                    LogLine(line = line)
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: LogFilter,
    onFilterChange: (LogFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.log_viewer_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = {
                        Text(
                            text = when (filter) {
                                LogFilter.ALL -> stringResource(R.string.log_viewer_filter_all)
                                LogFilter.ERROR -> "ERROR"
                                LogFilter.WARN -> "WARN"
                                LogFilter.INFO -> stringResource(R.string.log_viewer_filter_info)
                                LogFilter.DEBUG -> stringResource(R.string.log_viewer_filter_debug)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val levelToken = lineLevelToken(line)
    val backgroundColor = when {
        levelToken == "E" || levelToken == "A" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        levelToken == "W" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        levelToken == "D" || levelToken == "V" -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }

    val textColor = when {
        levelToken == "E" || levelToken == "A" -> MaterialTheme.colorScheme.error
        levelToken == "W" -> MaterialTheme.colorScheme.tertiary
        levelToken == "I" -> MaterialTheme.colorScheme.primary
        levelToken == "D" || levelToken == "V" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = line,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = textColor,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace
        )
    )
}

private fun lineLevelToken(line: String): String? {
    // Match new format: [VERBOSE], [DEBUG], [INFO], [WARN], [ERROR], [ASSERT]
    val newFormat = Regex("""\[(VERBOSE|DEBUG|INFO|WARN|ERROR|ASSERT)\]""").find(line)
    if (newFormat != null) {
        return when (newFormat.groupValues[1]) {
            "VERBOSE", "DEBUG" -> "D"
            "INFO" -> "I"
            "WARN" -> "W"
            "ERROR", "ASSERT" -> "E"
            else -> null
        }
    }
    // Fallback to old format: [V], [D], [I], [W], [E], [A]
    val oldFormat = Regex("""\[[VDIWEA]\s*\]""").find(line)?.value ?: return null
    return oldFormat.getOrNull(1)?.toString()
}
