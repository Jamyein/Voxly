package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onFixMetadata: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        HorizontalFloatingToolbar(
            expanded = expanded,
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
        ) {
            // Online Metadata
            SmallFloatingActionButton(
                onClick = onOnlineMetadata,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.CloudDownload), contentDescription = null)
            }

            // Unified Field
            SmallFloatingActionButton(
                onClick = onUnifiedField,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.Edit), contentDescription = null)
            }

            // Replace Text
            SmallFloatingActionButton(
                onClick = onReplaceText,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null)
            }

            // Auto Number
            SmallFloatingActionButton(
                onClick = onAutoNumber,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.Schedule), contentDescription = null)
            }

            // Rename Files
            SmallFloatingActionButton(
                onClick = onRenameFiles,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.Rename), contentDescription = null)
            }

            // Fix Metadata
            SmallFloatingActionButton(
                onClick = onFixMetadata,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null)
            }
        }
    }
}
