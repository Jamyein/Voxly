package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.AudioFile
import com.voxly.domain.model.AudioMetadata
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Section title component.
 */
@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
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
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * ReplayGain section component with expand/collapse functionality.
 */
@Composable
fun ReplayGainSection(
    replayGainInfo: ReplayGainInfo?,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
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
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
                )
            }

            // Expanded content
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isScanning -> {
                        // Scanning state
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
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

                        Spacer(modifier = Modifier.height(12.dp))

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

                        Spacer(modifier = Modifier.height(12.dp))

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
            color = MaterialTheme.colorScheme.primary
        )
    }
}
