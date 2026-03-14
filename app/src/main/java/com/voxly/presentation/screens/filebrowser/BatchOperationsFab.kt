package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
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
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Batch Operations FloatingToolbar using M3 HorizontalFloatingToolbar (without FAB)
 * Expands automatically when entering selection mode
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatchOperationsToolbar(
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit
) {
    // Only show toolbar in selection mode - auto expand
    if (!isSelectionMode) return

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        HorizontalFloatingToolbar(
            expanded = true, // Always expanded in selection mode
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
        ) {
            // Online Metadata
            ToolbarAction(
                label = stringResource(R.string.batch_online_metadata),
                icon = AppIcon.CloudDownload,
                onClick = onOnlineMetadata
            )

            // Unified Field
            ToolbarAction(
                label = stringResource(R.string.batch_unified_field),
                icon = AppIcon.Edit,
                onClick = onUnifiedField
            )

            // Replace Text
            ToolbarAction(
                label = stringResource(R.string.batch_replace_text),
                icon = AppIcon.AutoFix,
                onClick = onReplaceText
            )

            // Auto Number
            ToolbarAction(
                label = stringResource(R.string.batch_auto_number),
                icon = AppIcon.Schedule,
                onClick = onAutoNumber
            )

            // Rename Files
            ToolbarAction(
                label = stringResource(R.string.batch_rename_files),
                icon = AppIcon.Rename,
                onClick = onRenameFiles
            )

            // Fix Metadata
            ToolbarAction(
                label = stringResource(R.string.fix_metadata),
                icon = AppIcon.AutoFix,
                onClick = onFixMetadata
            )
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Icon(
            painter = appIconPainter(icon),
            contentDescription = label
        )
    }
}
