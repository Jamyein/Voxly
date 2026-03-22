package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarAction
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.FloatingToolbarState
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberFloatingToolbarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    // Create and remember the floating toolbar state
    val floatingToolbarState = rememberFloatingToolbarState()

    // Listen to expanded state changes
    LaunchedEffect(expanded) {
        if (expanded) {
            floatingToolbarState.expand()
        } else {
            floatingToolbarState.collapse()
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        HorizontalFloatingToolbar(
            state = floatingToolbarState,
            scrollBehavior = scrollBehavior,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
        ) {
            // Online Metadata
            FloatingToolbarAction(
                onClick = onOnlineMetadata,
                icon = { Icon(appIconPainter(AppIcon.CloudDownload), contentDescription = null) },
                label = { Text(stringResource(R.string.batch_online_metadata)) }
            )

            // Unified Field
            FloatingToolbarAction(
                onClick = onUnifiedField,
                icon = { Icon(appIconPainter(AppIcon.Edit), contentDescription = null) },
                label = { Text(stringResource(R.string.batch_unified_field)) }
            )

            // Replace Text
            FloatingToolbarAction(
                onClick = onReplaceText,
                icon = { Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null) },
                label = { Text(stringResource(R.string.batch_replace_text)) }
            )

            // Auto Number
            FloatingToolbarAction(
                onClick = onAutoNumber,
                icon = { Icon(appIconPainter(AppIcon.Schedule), contentDescription = null) },
                label = { Text(stringResource(R.string.batch_auto_number)) }
            )

            // Rename Files
            FloatingToolbarAction(
                onClick = onRenameFiles,
                icon = { Icon(appIconPainter(AppIcon.Rename), contentDescription = null) },
                label = { Text(stringResource(R.string.batch_rename_files)) }
            )

            // Fix Metadata
            FloatingToolbarAction(
                onClick = onFixMetadata,
                icon = { Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null) },
                label = { Text(stringResource(R.string.fix_metadata)) }
            )
        }
    }
}
