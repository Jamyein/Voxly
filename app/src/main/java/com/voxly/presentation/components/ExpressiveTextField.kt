package com.voxly.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Expressive Text Field Defaults
 * Provides default styling values for Expressive text fields
 */
object ExpressiveTextFieldDefaults {
    val shape = RoundedCornerShape(28.dp)
    val focusedBorderThickness = 2.dp
    val unfocusedBorderThickness = 1.dp
}

/**
 * Expressive Outlined Text Field
 *
 * Material Design 3 Expressive Text Field:
 * 1. Uses extraLarge rounded corners (28dp)
 * 2. Supports single-line and multi-line modes
 * 3. Supports numeric keyboard variants
 */
@Composable
fun ExpressiveOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val shape = ExpressiveTextFieldDefaults.shape

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = shape
    )
}

/**
 * Expressive Numeric Text Field
 * Convenience component for numeric input with proper keyboard type
 */
@Composable
fun ExpressiveNumericTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    ExpressiveOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Only allow numeric input (digits and empty string)
            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        isError = isError,
        singleLine = singleLine,
        minLines = 1,
        maxLines = 1,
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

/**
 * Expressive Multi-line Text Field
 * Convenience component for multi-line text input
 */
@Composable
fun ExpressiveMultiLineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    ExpressiveOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        isError = isError,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        keyboardType = KeyboardType.Text,
        enabled = enabled,
        readOnly = readOnly,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}
