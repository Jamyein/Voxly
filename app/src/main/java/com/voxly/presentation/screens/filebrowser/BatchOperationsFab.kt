package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Batch Operations FloatingToolbar using official M3E HorizontalFloatingToolbar API.
 * Uses FloatingToolbarScrollBehavior for scroll-to-hide animation.
 *
 * Optimized per official M3E guidelines:
 * - Tooltips on each action for discoverability
 * - Focus management for accessibility (canFocus = expanded)
 * - Proper positioning with align, offset, zIndex
 * - Vibrant colors for better visual integration
 * - FAB used for online metadata (primary action)
 *
 * Actions:
 * - FAB: Online Metadata
 * - Edit Metadata (dropdown: Unified Field, Replace Text, Fix Metadata)
 * - Auto Number
 * - Rename Files
 * - ReplayGain
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
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1f),
        contentAlignment = Alignment.BottomCenter
    ) {
        HorizontalFloatingToolbar(
            expanded = expanded,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = onOnlineMetadata
                ) {
                    Icon(
                        appIconPainter(AppIcon.CloudDownload),
                        contentDescription = stringResource(R.string.batch_online_metadata)
                    )
                }
            },
            modifier = Modifier
                .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 8.dp
                ),
            scrollBehavior = scrollBehavior,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
        ) {
            // Edit Metadata (dropdown with merged items)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.batch_edit_metadata_title)) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = { editMenuExpanded = true },
                    modifier = Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(
                        appIconPainter(AppIcon.Edit),
                        contentDescription = stringResource(R.string.batch_edit_metadata_title)
                    )
                }
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

            // Auto Number
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.batch_auto_number)) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = onAutoNumber,
                    modifier = Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(
                        appIconPainter(AppIcon.Schedule),
                        contentDescription = stringResource(R.string.batch_auto_number)
                    )
                }
            }

            // Rename Files
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.batch_rename_files)) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = onRenameFiles,
                    modifier = Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(
                        appIconPainter(AppIcon.Rename),
                        contentDescription = stringResource(R.string.batch_rename_files)
                    )
                }
            }

            // ReplayGain Scan
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.replay_gain_scanner_title)) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = onReplayGain,
                    modifier = Modifier.focusProperties { canFocus = expanded }
                ) {
                    Icon(
                        appIconPainter(AppIcon.Equalizer),
                        contentDescription = stringResource(R.string.replay_gain_scanner_title)
                    )
                }
            }
        }
    }
}
