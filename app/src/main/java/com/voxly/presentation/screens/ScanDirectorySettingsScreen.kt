package com.voxly.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.R
import com.voxly.domain.usecase.RebuildDatabaseState
import com.voxly.presentation.viewmodel.DirectoryManagementViewModel
import com.voxly.domain.model.WhitelistDirectory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDirectorySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DirectoryManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_scan_directory_settings)) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_whitelist_mode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = whitelistEnabled,
                                onCheckedChange = { viewModel.setWhitelistEnabled(it) }
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_whitelist_mode_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (whitelistEnabled) {
                item {
                    TextButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.add_whitelist_directory))
                    }
                }

                items(directories, key = { it.uri }) { directory ->
                    WhitelistDirectoryItem(
                        directory = directory,
                        onRemove = { viewModel.removeDirectory(directory.uri) }
                    )
                }

                if (directories.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_whitelist_directories),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_blacklist_mode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Switch(
                                checked = blacklistEnabled,
                                onCheckedChange = { viewModel.setBlacklistEnabled(it) }
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_blacklist_mode_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (blacklistEnabled) {
                item {
                    TextButton(
                        onClick = { blacklistFolderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.add_blacklist_directory))
                    }
                }

                items(blacklistDirectories, key = { it.uri }) { directory ->
                    BlacklistDirectoryItem(
                        directory = directory,
                        onRemove = { viewModel.removeBlacklistDirectory(directory.uri) }
                    )
                }

                if (blacklistDirectories.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_blacklist_directories),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.rebuild_database))
                }
            }
        }
    }
}

@Composable
private fun WhitelistDirectoryItem(
    directory: WhitelistDirectory,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                    style = MaterialTheme.typography.bodyLarge,
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
            IconButton(
                onClick = onRemove
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_directory)
                )
            }
        }
    }
}

@Composable
private fun BlacklistDirectoryItem(
    directory: WhitelistDirectory,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                    style = MaterialTheme.typography.bodyLarge,
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
            IconButton(
                onClick = onRemove
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_directory)
                )
            }
        }
    }
}