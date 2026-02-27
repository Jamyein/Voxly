package com.voxly.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.animation.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.presentation.components.CardPosition
import com.voxly.presentation.components.SettingsItemCard
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(),
                windowInsets = WindowInsets(0.dp),
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
                // First item - Segmented Buttons
                SettingsItemCard(position = CardPosition.FIRST) {
                    Text(
                        text = stringResource(R.string.settings_metadata_source),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Data Source Segmented Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        listOf(
                            EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ to R.string.settings_metadata_source_musicbrainz,
                            EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC to R.string.settings_metadata_source_apple_music,
                            EnhancedOnlineMetadataViewModel.DataSource.BOTH to R.string.settings_metadata_source_both
                        ).forEachIndexed { index, (source, labelRes) ->
                            val isSelected = dataSource == source
                            val cornerSize by animateDpAsState(
                                targetValue = if (isSelected) 8.dp else 0.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "cornerSize"
                            )
                            FilledTonalButton(
                                onClick = { viewModel.setDataSource(source) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                                shape = getButtonGroupShape(index = index, count = 3).let { baseShape ->
                                    RoundedCornerShape(
                                        topStart = if (index == 0) CornerSize(28.dp) else CornerSize(cornerSize),
                                        topEnd = if (index == 2) CornerSize(28.dp) else CornerSize(cornerSize),
                                        bottomStart = if (index == 0) CornerSize(28.dp) else CornerSize(cornerSize),
                                        bottomEnd = if (index == 2) CornerSize(28.dp) else CornerSize(cornerSize)
                                    )
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Last item - ListItem
                SettingsItemCard(position = CardPosition.LAST) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                // First item
                SettingsItemCard(position = CardPosition.FIRST) {
                    SettingsInfoRow(title = stringResource(R.string.settings_version_label), value = BuildConfig.VERSION_NAME)
                }
                // Last item
                SettingsItemCard(position = CardPosition.LAST) {
                    SettingsInfoRow(title = stringResource(R.string.settings_data_sources_label), value = stringResource(R.string.settings_data_sources_value))
                }
            }
        }
    }
}

/**
 * Returns the appropriate shape for a button in a connected button group.
 * First button gets rounded left corners, last button gets rounded right corners,
 * middle buttons are square (connected).
 */
@Composable
private fun getButtonGroupShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    return when {
        count == 1 -> MaterialTheme.shapes.extraLarge
        index == 0 -> MaterialTheme.shapes.extraLarge.copy(
            topEnd = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        )
        index == count - 1 -> MaterialTheme.shapes.extraLarge.copy(
            topStart = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp)
        )
        else -> MaterialTheme.shapes.small.copy(
            topStart = CornerSize(0.dp),
            topEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        )
    }
}


