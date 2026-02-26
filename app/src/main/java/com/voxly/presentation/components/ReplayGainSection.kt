package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.domain.model.ReplayGainInfo
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * ReplayGain section with expandable/collapsible content.
 */
@Composable
fun ReplayGainSection(
    replayGainInfo: ReplayGainInfo?,
    isScanning: Boolean,
    onScan: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
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

                if (isScanning) {
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
                } else if (replayGainInfo != null) {
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
                } else {
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
                            Icons.Default.Refresh,
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

@Composable
private fun ReplayGainRow(
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
