package com.voxly.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Option data class for segmented settings components.
 */
data class SegmentedOption<T>(
    val value: T,
    val icon: ImageVector? = null,
    val label: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedSettingsSegmentedButtonRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = it) } },
        trailingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option.value == selectedValue,
                        onClick = { onSelected(option.value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        ),
                        icon = {
                            option.icon?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    ) {
                        Text(option.label ?: "")
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

@Composable
fun SegmentedSettingsInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { Text(text = value) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SegmentedSettingsClickableRow(
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = subtitle?.let { { Text(text = it) } },
        trailingContent = trailingContent,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

/**
 * Settings row with segmented button group for selecting one option.
 * This version uses ToggleButton with connected shapes for a segmented appearance.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedSettingsSegmentedButton(
    title: String,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val mediumHeight = 40.dp
    val mediumWeight = 64.dp

    Column(modifier = modifier.fillMaxWidth()) {
        // Title row
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Segmented buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            options.forEachIndexed { index, option ->
                val tooltipState = rememberTooltipState()
                val isSelected = option.value == selectedValue

                Box {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above
                        ),
                        tooltip = {
                            PlainTooltip {
                                Text(option.label ?: "")
                            }
                        },
                        state = tooltipState
                    ) {
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) onSelected(option.value)
                            },
                            modifier = Modifier
                                .width(mediumWeight)
                                .height(mediumHeight)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                        ) {
                            if (option.icon != null) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.label,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            } else {
                                Text(
                                    text = option.label ?: "",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
