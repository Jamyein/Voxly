package com.voxly.presentation.screens.metadata

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.components.RoleGradientBadge
import com.voxly.presentation.components.rememberRoleAccent
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.emphasizedTitleMedium

/**
 * Section title component.
 * Cookie9Sided 角色色徽章 + titleMedium 大字，长表单有节奏。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionTitle(title: String, icon: AppIcon, modifier: Modifier = Modifier) {
    val roleAccent = rememberRoleAccent(title)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoleGradientBadge(
            painter = appIconPainter(icon),
            contentDescription = null,
            accent = roleAccent.accent,
            onAccent = roleAccent.onAccent,
            badgeSize = 30.dp,
            iconSize = 16.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = emphasizedTitleMedium
        )
    }
}

/**
 * File information row component.
 */
@Composable
fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * ReplayGain section component with expand/collapse functionality.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReplayGainSection(
    replayGainInfo: ReplayGainInfo?,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    error: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header - clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = appIconPainter(AppIcon.Equalizer),
                        contentDescription = stringResource(R.string.replay_gain_section_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.replay_gain_section_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "replayGainChevron"
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                )
            }

            // Expanded content
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Show error if present
                error?.let { scanError ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = scanError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                when {
                    isScanning -> {
                        // Scanning state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.replay_gain_scanning),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    replayGainInfo != null -> {
                        // Display existing ReplayGain values
                        ReplayGainRow(
                            label = stringResource(R.string.replay_gain_track),
                            value = replayGainInfo.getFormattedTrackGain()
                        )
                        ReplayGainRow(
                            label = stringResource(R.string.replay_gain_peak),
                            value = replayGainInfo.getFormattedTrackPeak()
                        )
                        replayGainInfo.albumGain?.let { albumGain ->
                            ReplayGainRow(
                                label = stringResource(R.string.replay_gain_album),
                                value = String.format("%.2f dB", albumGain)
                            )
                        }
                        replayGainInfo.albumPeak?.let { albumPeak ->
                            ReplayGainRow(
                                label = stringResource(R.string.replay_gain_album_peak),
                                value = String.format("%.4f", albumPeak)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onScan,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.cd_rescan_replay_gain)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.replay_gain_rescan))
                            }
                            OutlinedButton(
                                onClick = onClear,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_clear_replay_gain)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.replay_gain_clear))
                            }
                        }
                    }
                    else -> {
                        // No ReplayGain info
                        Text(
                            text = stringResource(R.string.replay_gain_no_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = stringResource(R.string.cd_scan_replay_gain)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.replay_gain_scan))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single ReplayGain value row.
 */
@Composable
fun ReplayGainRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}
