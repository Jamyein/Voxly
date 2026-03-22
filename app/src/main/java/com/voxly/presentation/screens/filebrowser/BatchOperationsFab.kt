package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Batch Operations FloatingToolbar using official M3 HorizontalFloatingToolbar API
 * Uses FloatingToolbarScrollBehavior for scroll-to-hide animation
 * Automatically expands/collapses based on selection mode
 *
 * Official API usage: IconButton in content slot (no FAB)
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
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = FloatingToolbarDefaults.ScreenOffset,
                    end = FloatingToolbarDefaults.ScreenOffset,
                    bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            HorizontalFloatingToolbar(
                expanded = expanded,
                scrollBehavior = scrollBehavior,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
            ) {
                // Using IconButton per official M3E FloatingToolbar API
                // IconButton should be used in content slot when no FAB is needed
                IconButton(onClick = onOnlineMetadata) {
                    Icon(appIconPainter(AppIcon.CloudDownload), contentDescription = null)
                }
                IconButton(onClick = onUnifiedField) {
                    Icon(appIconPainter(AppIcon.Edit), contentDescription = null)
                }
                IconButton(onClick = onReplaceText) {
                    Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null)
                }
                IconButton(onClick = onAutoNumber) {
                    Icon(appIconPainter(AppIcon.Schedule), contentDescription = null)
                }
                IconButton(onClick = onRenameFiles) {
                    Icon(appIconPainter(AppIcon.Rename), contentDescription = null)
                }
                IconButton(onClick = onFixMetadata) {
                    Icon(appIconPainter(AppIcon.AutoFix), contentDescription = null)
                }
            }
        }
    }
}
