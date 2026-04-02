package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Batch Operations FloatingToolbar using official M3 HorizontalFloatingToolbar API
 * Uses FloatingToolbarScrollBehavior for scroll-to-hide animation
 * Automatically expands/collapses based on selection mode
 *
 * Merged items:
 * - Online Metadata (independent)
 * - Edit Metadata (dropdown: Unified Field, Replace Text, Fix Metadata)
 * - Auto Number (independent)
 * - Rename Files (independent)
 * - ReplayGain (independent)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatchOperationsToolbar(
    expanded: Boolean,
    scrollBehavior: FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit,
    onReplayGain: () -> Unit
) {
    var editMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ) {
        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .padding(
                    start = FloatingToolbarDefaults.ScreenOffset,
                    end = FloatingToolbarDefaults.ScreenOffset
                )
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            scrollBehavior = scrollBehavior,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
        ) {
            // Online Metadata
            IconButton(onClick = onOnlineMetadata) {
                Icon(appIconPainter(AppIcon.CloudDownload), contentDescription = stringResource(R.string.batch_online_metadata))
            }

            // Edit Metadata (dropdown with merged items)
            Box {
                IconButton(onClick = { editMenuExpanded = true }) {
                    Icon(appIconPainter(AppIcon.Edit), contentDescription = stringResource(R.string.batch_edit_metadata_title))
                }
                DropdownMenu(
                    expanded = editMenuExpanded,
                    onDismissRequest = { editMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_unified_field)) },
                        onClick = {
                            editMenuExpanded = false
                            onUnifiedField()
                        },
                        leadingIcon = {
                            Icon(appIconPainter(AppIcon.Edit), contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_replace_text)) },
                        onClick = {
                            editMenuExpanded = false
                            onReplaceText()
                        },
                        leadingIcon = {
                            Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_fix_metadata)) },
                        onClick = {
                            editMenuExpanded = false
                            onFixMetadata()
                        },
                        leadingIcon = {
                            Icon(appIconPainter(AppIcon.Check), contentDescription = null)
                        }
                    )
                }
            }

            // Auto Number
            IconButton(onClick = onAutoNumber) {
                Icon(appIconPainter(AppIcon.Schedule), contentDescription = stringResource(R.string.batch_auto_number))
            }

            // Rename Files
            IconButton(onClick = onRenameFiles) {
                Icon(appIconPainter(AppIcon.Rename), contentDescription = stringResource(R.string.batch_rename_files))
            }

            // ReplayGain Scan
            IconButton(onClick = onReplayGain) {
                Icon(appIconPainter(AppIcon.Equalizer), contentDescription = stringResource(R.string.replay_gain_scanner_title))
            }
        }
    }
}
