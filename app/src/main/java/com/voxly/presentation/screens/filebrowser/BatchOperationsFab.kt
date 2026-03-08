package com.voxly.presentation.screens.filebrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import com.voxly.presentation.icons.AppIcon
import com.voxly.presentation.icons.appIconPainter
import com.voxly.presentation.theme.ExpressiveMotionTokens

/**
 * Batch Operations FAB with expandable menu (Speed Dial style)
 */
@Composable
fun BatchOperationsFAB(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onOnlineMetadata: () -> Unit,
    onUnifiedField: () -> Unit,
    onReplaceText: () -> Unit,
    onAutoNumber: () -> Unit,
    onRenameFiles: () -> Unit,
    onFixMetadata: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = ExpressiveMotionTokens.Emphasized.dampingRatio,
            stiffness = ExpressiveMotionTokens.Emphasized.stiffness
        ),
        label = "fab_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Menu items
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
            )) + expandVertically(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardDecelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardDecelerate.stiffness
            )),
            exit = fadeOut(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
            )) + shrinkVertically(animationSpec = spring(
                dampingRatio = ExpressiveMotionTokens.StandardAccelerate.dampingRatio,
                stiffness = ExpressiveMotionTokens.StandardAccelerate.stiffness
            ))
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // Online Metadata
                MenuItem(
                    label = stringResource(R.string.batch_online_metadata),
                    icon = AppIcon.CloudDownload,
                    onClick = onOnlineMetadata
                )

                // Unified Field
                MenuItem(
                    label = stringResource(R.string.batch_unified_field),
                    icon = AppIcon.Edit,
                    onClick = onUnifiedField
                )

                // Replace Text
                MenuItem(
                    label = stringResource(R.string.batch_replace_text),
                    icon = AppIcon.AutoFix,
                    onClick = onReplaceText
                )

                // Auto Number
                MenuItem(
                    label = stringResource(R.string.batch_auto_number),
                    icon = AppIcon.Schedule,
                    onClick = onAutoNumber
                )

                // Rename Files
                MenuItem(
                    label = stringResource(R.string.batch_rename_files),
                    icon = AppIcon.Rename,
                    onClick = onRenameFiles
                )

                // Fix Metadata
                MenuItem(
                    label = stringResource(R.string.fix_metadata),
                    icon = AppIcon.AutoFix,
                    onClick = onFixMetadata
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = { onExpandChange(!expanded) },
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

@Composable
fun MenuItem(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        // Label
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Icon
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
}
