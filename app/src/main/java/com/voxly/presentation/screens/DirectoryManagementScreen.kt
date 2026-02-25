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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.R
import com.voxly.presentation.components.ButtonEmphasis
import com.voxly.presentation.components.ExpressiveButton
import com.voxly.presentation.components.ExpressiveCard
import com.voxly.presentation.components.ExpressiveScaffoldWithBack
import com.voxly.presentation.components.ExpressiveIconButton
import com.voxly.presentation.components.ExpressiveTextButton
import com.voxly.presentation.theme.ContainerLevel
import com.voxly.presentation.viewmodel.DirectoryManagementViewModel
import com.voxly.presentation.viewmodel.SelectedDirectory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: DirectoryManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val directories by viewModel.directories.collectAsState()

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

    ExpressiveScaffoldWithBack(
        title = stringResource(R.string.settings_directory_management),
        onBackClick = onNavigateBack,
        actions = {
            ExpressiveIconButton(
                onClick = { folderPickerLauncher.launch(null) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.add_directory),
                emphasis = ButtonEmphasis.Medium
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (directories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.directory_management_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        emphasis = ButtonEmphasis.High
                    ) {
                        Text(stringResource(R.string.add_directory))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.selected_directories_count, directories.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveTextButton(onClick = { viewModel.clearDirectories() }) {
                        Text(stringResource(R.string.clear_directories))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(directories, key = { it.uri }) { directory ->
                        DirectoryManageItem(
                            directory = directory,
                            onRemove = { viewModel.removeDirectory(directory.uri) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryManageItem(
    directory: SelectedDirectory,
    onRemove: () -> Unit
) {
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        containerLevel = ContainerLevel.Low
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directory.path.substringAfterLast('/').ifBlank { directory.path },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = directory.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveIconButton(
                onClick = onRemove,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.clear_selection),
                emphasis = ButtonEmphasis.Medium
            )
        }
    }
}
