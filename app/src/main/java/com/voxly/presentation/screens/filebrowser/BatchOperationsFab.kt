package com.voxly.presentation.screens.filebrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter

/**
 * Batch Operations FloatingToolbar using M3E styling
 * Uses FloatingToolbarScrollBehavior for scroll-to-hide animation
 * Automatically expands/collapses based on selection mode
 *
 * Uses Surface with IconButtons for the toolbar items, styled with M3E colors.
 * WindowInsets.systemBars provides proper bottom inset handling.
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
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = FloatingToolbarDefaults.ScreenOffset,
                    end = FloatingToolbarDefaults.ScreenOffset,
                    bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
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
