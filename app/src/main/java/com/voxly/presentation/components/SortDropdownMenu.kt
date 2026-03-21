package com.voxly.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.voxly.R

/**
 * A generic sort dropdown menu component that can be reused across different screens.
 *
 * @param isExpanded Whether the dropdown is expanded
 * @param currentSortOption The currently selected sort option
 * @param sortOptions Map of sort options to their label string resources
 * @param onSortOptionChange Callback when a sort option is selected
 * @param onDismiss Callback when the dropdown is dismissed
 */
@Composable
fun <T> SortDropdownMenu(
    isExpanded: Boolean,
    currentSortOption: T,
    sortOptions: List<Pair<T, Int>>,
    onSortOptionChange: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            sortOptions.forEach { (option, labelResId) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelResId)) },
                    leadingIcon = if (option == currentSortOption) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.cd_selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        onSortOptionChange(option)
                        onDismiss()
                    }
                )
            }
        }
    }
}
