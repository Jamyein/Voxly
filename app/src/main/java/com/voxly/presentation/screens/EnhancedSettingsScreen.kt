package com.voxly.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.presentation.components.SettingsSection
import com.voxly.presentation.viewmodel.EnhancedOnlineMetadataViewModel

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
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = WindowInsets.statusBars.asPaddingValues()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Online Metadata Section
            SettingsSection(title = stringResource(R.string.settings_section_online_metadata)) {
                SettingsDropdownRow(
                    title = stringResource(R.string.settings_metadata_source),
                    subtitle = stringResource(R.string.settings_metadata_source_subtitle),
                    selectedLabel = currentDataSourceLabel,
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
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

                Spacer(modifier = Modifier.height(4.dp))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_metadata_source_subtitle)) },
                    supportingContent = {
                        Text(
                            text = when (dataSource) {
                                EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ ->
                                    stringResource(R.string.settings_metadata_source_description_musicbrainz)
                                EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC ->
                                    stringResource(R.string.settings_metadata_source_description_apple_music)
                                EnhancedOnlineMetadataViewModel.DataSource.BOTH ->
                                    stringResource(R.string.settings_metadata_source_description_both)
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                Spacer(modifier = Modifier.height(4.dp))
                SettingsInfoRow(title = stringResource(R.string.settings_data_sources_label), value = stringResource(R.string.settings_data_sources_value))
            }
        }
    }
}


