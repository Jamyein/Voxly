package com.voxly.presentation.screens.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyScaffold
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.components.libraryContentPadding
import kotlinx.coroutines.CancellationException

// One parsed log line: [2026-01-01 12:34:56.789] [INFO] [TAG         ] message
private val LOG_LINE_REGEX = Regex("""^\[(.*?)\] \[(VERBOSE|DEBUG|INFO|WARN|ERROR|ASSERT)\] \[(.*?)\] (.*)$""")

private data class ParsedLogLine(
    val time: String,
    val level: String,
    val tag: String,
    val message: String,
    val isStackLine: Boolean
)

private fun parseLogLine(line: String): ParsedLogLine? {
    // Stack-trace continuation lines are indented by 4 spaces
    if (line.startsWith("    ")) {
        return ParsedLogLine(time = "", level = "", tag = "", message = line.trimStart(), isStackLine = true)
    }
    if (line.isBlank()) return null
    val match = LOG_LINE_REGEX.matchEntire(line) ?: return null
    return ParsedLogLine(
        time = match.groupValues[1].substringAfterLast(' '),
        level = match.groupValues[2],
        tag = match.groupValues[3].trim(),
        message = match.groupValues[4],
        isStackLine = false
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    viewModel: LogViewerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    val title = if (uiState.selectedLogFile == null) {
        stringResource(R.string.log_viewer_title)
    } else {
        uiState.selectedLogFile!!.name
    }

    val selectedLogState = remember { SeekableTransitionState(initialState = false) }

    PredictiveBackHandler(enabled = uiState.selectedLogFile != null) { progress ->
        try {
            progress.collect { backEvent ->
                selectedLogState.seekTo(fraction = backEvent.progress)
            }
            selectedLogState.animateTo(targetState = true)
            viewModel.clearSelectedLog()
        } catch (e: CancellationException) {
            selectedLogState.snapTo(targetState = false)
        }
    }

    VoxlyScaffold(
        topBar = {
            VoxlyTopAppBar(
                theme = TopBarTheme.Library,
                title = { Text(text = title) },
                onBack = {
                    if (uiState.selectedLogFile != null) {
                        viewModel.clearSelectedLog()
                    } else {
                        onBack()
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
        ) {
            AnimatedContent(
                targetState = uiState.selectedLogFile != null,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 }) togetherWith
                            (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 6 })
                    } else {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 6 }) togetherWith
                            (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 6 })
                    }
                },
                label = "logViewerContent"
            ) { isDetail ->
                if (isDetail) {
                    LogContent(
                        viewModel = viewModel,
                        listState = listState
                    )
                } else {
                    LogFileList(
                        logFiles = uiState.logFiles,
                        isLoading = uiState.isLoading,
                        onFileClick = { viewModel.selectLogFile(it) },
                        onExportLogs = {
                            viewModel.exportLogs(context) { uri ->
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Export Logs"))
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_logging_no_logs,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onCleanupLogs = { showCleanupDialog = true }
                    )
                }
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

    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(R.string.settings_logging_cleanup)) },
            text = { Text(stringResource(R.string.log_viewer_cleanup_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showCleanupDialog = false
                    viewModel.clearAllLogs { count ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_logging_cleanup_complete, count),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

// ===================== Log file list =====================

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogFileList(
    logFiles: List<LogFileItem>,
    isLoading: Boolean,
    onFileClick: (LogFileItem) -> Unit,
    onExportLogs: () -> Unit,
    onCleanupLogs: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Actions: export all as zip / clean up all logs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onExportLogs,
                enabled = logFiles.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_logging_export))
            }
            FilledTonalButton(
                onClick = onCleanupLogs,
                enabled = logFiles.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_logging_cleanup))
            }
        }

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.log_viewer_no_logs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Group by type: regular logs and crash reports, each with a sticky header
            val (regularLogs, crashLogs) = logFiles.partition { !it.isCrash }
            LazyColumn(
                contentPadding = libraryContentPadding(start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (regularLogs.isNotEmpty()) {
                    stickyHeader {
                        LogSectionHeader(
                            title = stringResource(R.string.log_viewer_section_logs),
                            count = regularLogs.size
                        )
                    }
                    items(regularLogs, key = { it.name }) { item ->
                        LogFileCard(
                            item = item,
                            onClick = { onFileClick(item) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            )
                        )
                    }
                }
                if (crashLogs.isNotEmpty()) {
                    stickyHeader {
                        LogSectionHeader(
                            title = stringResource(R.string.log_viewer_section_crash),
                            count = crashLogs.size,
                            isError = true
                        )
                    }
                    items(crashLogs, key = { it.name }) { item ->
                        LogFileCard(
                            item = item,
                            onClick = { onFileClick(item) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            )
                        )
                    }
                }
            }
        }
    }
}

/** Uppercase section header with a count pill, styled like other list sections. */
@Composable
private fun LogSectionHeader(
    title: String,
    count: Int,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .background(
                    color = if (isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogFileCard(
    item: LogFileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type icon: crash reports get an error-toned bug icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (item.isCrash) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isCrash) Icons.Default.BugReport else Icons.AutoMirrored.Filled.Article,
                    contentDescription = stringResource(
                        if (item.isCrash) R.string.log_viewer_crash_file else R.string.log_viewer_log_file
                    ),
                    tint = if (item.isCrash) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isCrash) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.log_viewer_crash_file),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.lastModified,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = item.size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ===================== Log content =====================

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
            val totalLines = uiState.logContent.lines().size
            val isFiltering = uiState.searchQuery.isNotBlank() || uiState.selectedFilter != LogFilter.ALL

            if (isFiltering) {
                Text(
                    text = stringResource(R.string.log_viewer_result_count, filteredLogs.size, totalLines),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = libraryContentPadding(start = 0.dp, end = 0.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(filteredLogs, key = { idx, line -> idx to line }) { _, line ->
                    LogLine(line = line, query = uiState.searchQuery)
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
                                LogFilter.ERROR -> stringResource(R.string.log_viewer_filter_error)
                                LogFilter.WARN -> stringResource(R.string.log_viewer_filter_warn)
                                LogFilter.INFO -> stringResource(R.string.log_viewer_filter_info)
                                LogFilter.DEBUG -> stringResource(R.string.log_viewer_filter_debug)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (filter) {
                                LogFilter.ALL -> Icons.AutoMirrored.Filled.FormatListBulleted
                                LogFilter.ERROR -> Icons.Default.ErrorOutline
                                LogFilter.WARN -> Icons.Default.WarningAmber
                                LogFilter.INFO -> Icons.Default.Info
                                LogFilter.DEBUG -> Icons.Default.BugReport
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogLine(
    line: String,
    query: String
) {
    val parsed = remember(line) { parseLogLine(line) }
    val context = LocalContext.current

    // A subtle full-row tint keeps error/warn lines scannable at a glance
    val rowBackground = when (parsed?.level) {
        "ERROR", "ASSERT" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        "WARN" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    val modifier = Modifier
        .fillMaxWidth()
        .background(rowBackground)
        .combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Voxly Log Line", line))
                Toast.makeText(context, R.string.log_viewer_copied_line, Toast.LENGTH_SHORT).show()
            }
        )
        .padding(horizontal = 12.dp, vertical = 2.dp)

    when {
        parsed == null -> {
            // Blank / unrecognized line — render as-is
            Text(
                text = line,
                modifier = modifier,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        parsed.isStackLine -> {
            // Stack-trace continuation: indented, muted monospace
            Text(
                text = parsed.message,
                modifier = modifier.padding(start = 11.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.outline
            )
        }

        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Level color bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            color = levelColor(parsed.level),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlightMatches(parsed.message, query),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = buildString {
                            append(parsed.tag)
                            if (parsed.time.isNotEmpty()) {
                                append("  ·  ")
                                append(parsed.time)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: String): Color = when (level) {
    "ERROR", "ASSERT" -> MaterialTheme.colorScheme.error
    "WARN" -> MaterialTheme.colorScheme.tertiary
    "INFO" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
}

/** Builds an annotated message where every case-insensitive match of [query] is highlighted. */
@Composable
private fun highlightMatches(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val highlightText = MaterialTheme.colorScheme.onTertiaryContainer
    return remember(text, query, highlight, highlightText) {
        buildAnnotatedString {
            var start = 0
            var found = text.indexOf(query, startIndex = start, ignoreCase = true)
            while (found >= 0) {
                append(text.substring(start, found))
                withStyle(
                    SpanStyle(
                        background = highlight,
                        color = highlightText
                    )
                ) {
                    append(text.substring(found, found + query.length))
                }
                start = found + query.length
                found = text.indexOf(query, startIndex = start, ignoreCase = true)
            }
            append(text.substring(start))
        }
    }
}
