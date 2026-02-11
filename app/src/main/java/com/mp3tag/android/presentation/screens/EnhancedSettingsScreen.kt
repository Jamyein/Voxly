package com.mp3tag.android.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mp3tag.android.presentation.viewmodel.EnhancedOnlineMetadataViewModel

/**
 * Enhanced settings screen with Apple Music data source options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSettingsScreen(
    viewModel: EnhancedOnlineMetadataViewModel = hiltViewModel()
) {
    val dataSource by viewModel.dataSource.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Online Metadata Section
            SettingsSection(title = "Online Metadata") {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Metadata Source",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Choose where to fetch metadata from",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = viewModel.getDataSourceName(dataSource),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { 
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) 
                                    },
                                    modifier = Modifier.menuAnchor()
                                )

                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("MusicBrainz") },
                                        onClick = {
                                            viewModel.setDataSource(EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ)
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Apple Music") },
                                        onClick = {
                                            viewModel.setDataSource(EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC)
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Both Sources") },
                                        onClick = {
                                            viewModel.setDataSource(EnhancedOnlineMetadataViewModel.DataSource.BOTH)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Source description
                        Text(
                            text = when (dataSource) {
                                EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ -> 
                                    "MusicBrainz: Open music encyclopedia with detailed metadata"
                                EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC -> 
                                    "Apple Music: High-quality artwork and comprehensive catalog"
                                EnhancedOnlineMetadataViewModel.DataSource.BOTH -> 
                                    "Both: Combines results from all sources for best coverage"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = "About") {
                SettingsInfoRow(title = "Version", value = "1.1.0")
                SettingsInfoRow(title = "Data Sources", value = "MusicBrainz + Apple Music")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
