package com.voxly.presentation.components.scrollbar

import androidx.compose.ui.unit.dp

/**
 * Configuration for Material 3 Expressive scrollbar appearance and behavior.
 *
 * This configuration follows the design principles of the future official
 * ScrollIndicator API while working with current Compose Foundation versions.
 *
 * @property thumbWidth Width of the scrollbar thumb in default state
 * @property thumbWidthDragging Width of the thumb when being dragged
 * @property thumbHeight Base height of the scrollbar thumb
 * @property minThumbHeight Minimum thumb height to ensure it's always tappable
 * @property touchAreaWidth Width of the touch area for easier interaction
 * @property thumbCornerRadius Corner radius for rounded scrollbar thumb
 * @property bubbleSize Size of the preview bubble
 * @property bubbleCornerRadius Corner radius of the preview bubble
 * @property hideDelayMillis Delay before hiding scrollbar after scroll stops
 * @property trackAlpha Alpha value for the scrollbar track in normal state
 * @property trackAlphaDragging Alpha value for the scrollbar track when dragging
 */
data class ScrollbarConfig(
    val thumbWidth: androidx.compose.ui.unit.Dp = 6.dp,
    val thumbWidthDragging: androidx.compose.ui.unit.Dp = 12.dp,
    val thumbHeight: androidx.compose.ui.unit.Dp = 48.dp,
    val minThumbHeight: androidx.compose.ui.unit.Dp = 32.dp,
    val touchAreaWidth: androidx.compose.ui.unit.Dp = 24.dp,
    val thumbCornerRadius: androidx.compose.ui.unit.Dp = 3.dp,
    val bubbleSize: androidx.compose.ui.unit.Dp = 56.dp,
    val bubbleCornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    val hideDelayMillis: Long = 1200L,
    val trackAlpha: Float = 0.15f,
    val trackAlphaDragging: Float = 0.35f
) {
    companion object {
        /** Default configuration following Material 3 Expressive guidelines */
        val Default = ScrollbarConfig()
    }
}
