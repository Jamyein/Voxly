package com.mp3tag.android.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mp3tag.android.BuildConfig
import com.mp3tag.android.R
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
    val currentDataSourceLabel = when (dataSource) {
        EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ ->
            stringResource(R.string.settings_metadata_source_musicbrainz)
        EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC ->
            stringResource(R.string.settings_metadata_source_apple_music)
        EnhancedOnlineMetadataViewModel.DataSource.BOTH ->
            stringResource(R.string.settings_metadata_source_both)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) }
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
            SettingsSection(title = stringResource(R.string.settings_section_online_metadata)) {
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
                                    text = stringResource(R.string.settings_metadata_source),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.settings_metadata_source_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = currentDataSourceLabel,
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
                                        text = { Text(stringResource(R.string.settings_metadata_source_musicbrainz)) },
                                        onClick = {
                                            viewModel.setDataSource(EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ)
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings_metadata_source_apple_music)) },
                                        onClick = {
                                            viewModel.setDataSource(EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC)
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings_metadata_source_both)) },
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
                                    stringResource(R.string.settings_metadata_source_description_musicbrainz)
                                EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC -> 
                                    stringResource(R.string.settings_metadata_source_description_apple_music)
                                EnhancedOnlineMetadataViewModel.DataSource.BOTH -> 
                                    stringResource(R.string.settings_metadata_source_description_both)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                SettingsInfoRow(title = stringResource(R.string.settings_data_sources_label), value = stringResource(R.string.settings_data_sources_value))
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
