package com.voxly.presentation.screens.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.voxly.R

/**
 * Save button bar component.
 * Shows save button that is enabled only when there are unsaved changes.
 */
@Composable
fun SaveButtonsBar(
    hasUnsavedChanges: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            if (hasUnsavedChanges) {
                onSave()
            }
        },
        enabled = hasUnsavedChanges,
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Save,
            contentDescription = stringResource(R.string.cd_save)
        )
    }
}