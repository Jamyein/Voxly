package com.voxly.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.voxly.BuildConfig
import com.voxly.R
import com.voxly.presentation.components.CardPosition
import com.voxly.presentation.components.SettingsItemCard
import com.voxly.presentation.components.SettingsSection
import com.voxly.presentation.viewmodel.EnhancedOnlineMetadataViewModel

data class EnhancedConnectedIconOption<T>(
    val value: T,
    val icon: ImageVector,
    val tooltip: String
)

private fun connectedGroupWidth(optionCount: Int): Dp {
    val perButtonBase = 56.dp
    val spacing = 2.dp
    val count = optionCount.coerceAtLeast(1)
    val width = perButtonBase * count + spacing * (count - 1)
    return width.coerceIn(160.dp, 320.dp)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnhancedConnectedIconButtonGroup(
    options: List<EnhancedConnectedIconOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val mediumHeight = ButtonDefaults.MediumContainerHeight
    val outerRadius = mediumHeight / 2
    val innerRadius = 8.dp
    val pressedInnerRadius = 4.dp
    val checkedInnerRadius = 8.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val tooltipState = rememberTooltipState()
            TooltipBox(
                modifier = Modifier.weight(1f),
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(option.tooltip)
                    }
                },
                state = tooltipState
            ) {
                ToggleButton(
                    checked = option.value == selectedValue,
                    onCheckedChange = { checked ->
                        if (checked) onSelected(option.value)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = mediumHeight)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ToggleButtonDefaults.shapes(
                            shape = RoundedCornerShape(
                                topStart = outerRadius,
                                bottomStart = outerRadius,
                                topEnd = innerRadius,
                                bottomEnd = innerRadius
                            ),
                            pressedShape = RoundedCornerShape(
                                topStart = outerRadius,
                                bottomStart = outerRadius,
                                topEnd = pressedInnerRadius,
                                bottomEnd = pressedInnerRadius
                            ),
                            checkedShape = RoundedCornerShape(
                                topStart = outerRadius,
                                bottomStart = outerRadius,
                                topEnd = checkedInnerRadius,
                                bottomEnd = checkedInnerRadius
                            )
                        )
                        options.lastIndex -> ToggleButtonDefaults.shapes(
                            shape = RoundedCornerShape(
                                topStart = innerRadius,
                                bottomStart = innerRadius,
                                topEnd = outerRadius,
                                bottomEnd = outerRadius
                            ),
                            pressedShape = RoundedCornerShape(
                                topStart = pressedInnerRadius,
                                bottomStart = pressedInnerRadius,
                                topEnd = outerRadius,
                                bottomEnd = outerRadius
                            ),
                            checkedShape = RoundedCornerShape(
                                topStart = checkedInnerRadius,
                                bottomStart = checkedInnerRadius,
                                topEnd = outerRadius,
                                bottomEnd = outerRadius
                            )
                        )
                        else -> ToggleButtonDefaults.shapes(
                            shape = RoundedCornerShape(innerRadius),
                            pressedShape = RoundedCornerShape(pressedInnerRadius),
                            checkedShape = RoundedCornerShape(checkedInnerRadius)
                        )
                    },
                    contentPadding = PaddingValues(2.dp)
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.tooltip
                    )
                }
            }
        }
    }
}

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_metadata_source),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        val sourceOptionCount = 3
                        Box(modifier = Modifier.width(connectedGroupWidth(sourceOptionCount))) {
                            EnhancedConnectedIconButtonGroup(
                                options = listOf(
                                    EnhancedConnectedIconOption(
                                        EnhancedOnlineMetadataViewModel.DataSource.MUSICBRAINZ,
                                        Icons.Default.Public,
                                        stringResource(R.string.settings_metadata_source_musicbrainz)
                                    ),
                                    EnhancedConnectedIconOption(
                                        EnhancedOnlineMetadataViewModel.DataSource.ITUNES_APPLE_MUSIC,
                                        Icons.Default.Album,
                                        stringResource(R.string.settings_metadata_source_apple_music)
                                    ),
                                    EnhancedConnectedIconOption(
                                        EnhancedOnlineMetadataViewModel.DataSource.BOTH,
                                        Icons.Default.LibraryMusic,
                                        stringResource(R.string.settings_metadata_source_both)
                                    )
                                ),
                                selectedValue = dataSource,
                                onSelected = viewModel::setDataSource
                            )
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
