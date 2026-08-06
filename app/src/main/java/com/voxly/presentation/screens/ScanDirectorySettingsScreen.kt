package com.voxly.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxly.R
import com.voxly.domain.model.WhitelistDirectory
import com.voxly.domain.usecase.RebuildDatabaseState
import com.voxly.presentation.components.SegmentedSwitchRow
import com.voxly.presentation.components.SettingsSection
import com.voxly.presentation.components.TopBarTheme
import com.voxly.presentation.components.VoxlyScaffold
import com.voxly.presentation.components.VoxlyTopAppBar
import com.voxly.presentation.components.rememberRoleAccent
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.theme.scaleOnPress
import com.voxly.presentation.viewmodel.DirectoryManagementViewModel

// Layout constants
private val HorizontalPadding = 16.dp
private val SectionSpacing = 28.dp
private val CardSpacing = 8.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanDirectorySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DirectoryManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val whitelistEnabled by viewModel.whitelistEnabled.collectAsStateWithLifecycle()
    val blacklistEnabled by viewModel.blacklistEnabled.collectAsStateWithLifecycle()
    val directories by viewModel.directories.collectAsStateWithLifecycle()
    val blacklistDirectories by viewModel.blacklistDirectories.collectAsStateWithLifecycle()
    val rebuildState by viewModel.rebuildState.collectAsStateWithLifecycle()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(directories) {
        directories.forEach { directory ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    android.net.Uri.parse(directory.uri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    LaunchedEffect(rebuildState) {
        when (val state = rebuildState) {
            is RebuildDatabaseState.InProgress -> {
                showProgressDialog = true
            }
            is RebuildDatabaseState.Completed -> {
                showProgressDialog = false
                val durationSeconds = state.durationMs / 1000.0
                snackbarHostState.showSnackbar(
                    context.getString(R.string.rebuild_completed_message, state.totalScanned, durationSeconds)
                )
            }
            is RebuildDatabaseState.Error -> {
                showProgressDialog = false
                snackbarHostState.showSnackbar(
                    context.getString(R.string.rebuild_error_message, state.message)
                )
            }
            RebuildDatabaseState.Idle -> {
                showProgressDialog = false
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.addDirectory(it)
        }
    }

    val blacklistFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.addBlacklistDirectory(it)
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.rebuild_database_title)) },
            text = { Text(stringResource(R.string.rebuild_database_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.rebuildDatabase()
                    }
                ) {
                    Text(stringResource(R.string.rebuild_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showProgressDialog) {
        val state = rebuildState
        if (state is RebuildDatabaseState.InProgress) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.rebuild_progress_title)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(
                                R.string.rebuild_progress_count,
                                state.scannedCount
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.rebuild_progress_percent,
                                (state.progress * 100).toInt()
                            )
                        )
                        if (state.currentFile != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.currentFile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                confirmButton = { }
            )
        }
    }

    VoxlyScaffold(
        topBar = {
            VoxlyTopAppBar(
                large = true,
                theme = TopBarTheme.Library,
                title = { Text(stringResource(R.string.settings_scan_directory_settings)) },
                onBack = onNavigateBack
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = HorizontalPadding),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding() + 24.dp
            )
        ) {
            // Whitelist section
            item {
                Column {
                    SettingsSection(title = stringResource(R.string.scan_directory_section_whitelist)) {
                        SegmentedSwitchRow(
                            title = stringResource(R.string.settings_whitelist_mode),
                            subtitle = stringResource(R.string.settings_whitelist_mode_subtitle),
                            checked = whitelistEnabled,
                            onCheckedChange = { viewModel.setWhitelistEnabled(it) }
                        )
                    }
                    if (whitelistEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AddDirectoryButton(
                            text = stringResource(R.string.add_whitelist_directory),
                            onClick = { folderPickerLauncher.launch(null) }
                        )
                        Spacer(modifier = Modifier.height(CardSpacing))
                        if (directories.isEmpty()) {
                            EmptyDirectoriesState(
                                text = stringResource(R.string.no_whitelist_directories)
                            )
                        } else {
                            directories.forEach { directory ->
                                DirectoryCard(
                                    directory = directory,
                                    onRemove = { viewModel.removeDirectory(directory.uri) }
                                )
                                Spacer(modifier = Modifier.height(CardSpacing))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(SectionSpacing)) }

            // Blacklist section
            item {
                Column {
                    SettingsSection(title = stringResource(R.string.scan_directory_section_blacklist)) {
                        SegmentedSwitchRow(
                            title = stringResource(R.string.settings_blacklist_mode),
                            subtitle = stringResource(R.string.settings_blacklist_mode_subtitle),
                            checked = blacklistEnabled,
                            onCheckedChange = { viewModel.setBlacklistEnabled(it) }
                        )
                    }
                    if (blacklistEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AddDirectoryButton(
                            text = stringResource(R.string.add_blacklist_directory),
                            onClick = { blacklistFolderPickerLauncher.launch(null) }
                        )
                        Spacer(modifier = Modifier.height(CardSpacing))
                        if (blacklistDirectories.isEmpty()) {
                            EmptyDirectoriesState(
                                text = stringResource(R.string.no_blacklist_directories)
                            )
                        } else {
                            blacklistDirectories.forEach { directory ->
                                DirectoryCard(
                                    directory = directory,
                                    onRemove = { viewModel.removeBlacklistDirectory(directory.uri) }
                                )
                                Spacer(modifier = Modifier.height(CardSpacing))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(SectionSpacing)) }

            // Maintenance section
            item {
                SettingsSection(title = stringResource(R.string.scan_directory_section_maintenance)) {
                    RebuildDatabaseRow(
                        onClick = { showConfirmDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDirectoryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun DirectoryCard(
    directory: WhitelistDirectory,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = rememberRoleAccent(directory.path)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Role-accent folder badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcon.FolderOpen.vector,
                    contentDescription = null,
                    tint = accent.onContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = directory.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalIconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_directory),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDirectoriesState(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = AppIcon.FolderOutlined.vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RebuildDatabaseRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scaleOnPress(interactionSource, label = "rebuildDatabaseRow"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        interactionSource = interactionSource,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.rebuild_database),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.rebuild_database_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
            )
        }
    }
}
