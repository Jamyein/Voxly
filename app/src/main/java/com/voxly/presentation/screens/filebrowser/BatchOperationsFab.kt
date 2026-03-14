package com.voxly.presentation.screens.filebrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotionTokens
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

/**
 * Batch Operations FloatingToolbar for selection mode
 * Mimics M3 FloatingToolbar behavior with expand/collapse animation
 */
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
    var expanded by remember(isSelectionMode) { mutableStateOf(isSelectionMode) }

    // Sync expanded state with selection mode
    LaunchedEffect(isSelectionMode) {
        expanded = isSelectionMode
    }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
            stiffness = ExpressiveMotionTokens.Emphasized.stiffness
        ),
        label = "fab_rotation"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toolbar content - only visible when expanded
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                        stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
                    )) + expandHorizontally(animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                        stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
                    )),
                    exit = fadeOut(animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                        stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
                    )) + shrinkHorizontally(animationSpec = spring(
                        dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                        stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
                    ))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
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

                // Main FAB to toggle expansion
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.batch_operations),
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
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
