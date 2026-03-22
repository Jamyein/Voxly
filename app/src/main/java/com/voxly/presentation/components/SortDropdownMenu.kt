package com.voxly.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.voxly.R
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults

/**
 * M3 Expressive styled sort dropdown menu with custom menu content.
 * Uses ExposedDropdownMenuBox for proper menu positioning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDropdownMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable ExposedDropdownMenuBoxScope.() -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            anchor()
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(min = 220.dp),
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SortMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    currentSortOption: T,
    options: List<T>,
    optionLabelResId: (T) -> Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onSortOptionChange: (T) -> Unit
) {
    SortDropdownMenu(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        anchor = {
            IconButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = contentDescription,
                    tint = if (expanded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    ) {
        options.forEach { option ->
            SortMenuItem(
                option = option,
                labelResId = optionLabelResId(option),
                currentSortOption = currentSortOption,
                onSortOptionChange = onSortOptionChange,
                onDismiss = { onExpandedChange(false) }
            )
        }
    }
}

/**
 * Creates a standard M3 Expressive sort menu item with check indicator.
 */
@Composable
fun <T> SortMenuItem(
    option: T,
    labelResId: Int,
    currentSortOption: T,
    onSortOptionChange: (T) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.labelLarge
            )
        },
        trailingIcon = if (option == currentSortOption) {
            {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = MaterialTheme.colorScheme.primary
        ),
        onClick = {
            onSortOptionChange(option)
            onDismiss()
        }
    )
}
