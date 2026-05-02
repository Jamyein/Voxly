package com.voxly.presentation.components.scrollbar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable

/**
 * Holds the internal mutable state of a scrollbar.
 *
 * This state holder is hoisted so that callers can observe and control
 * scrollbar behavior (e.g., programmatically show/hide, read drag state).
 *
 * @see rememberScrollbarStateHolder
 */
@Stable
class ScrollbarStateHolder {

    /** Whether the user is currently dragging the thumb. */
    var isDragging by mutableStateOf(false)
        internal set

    /** Whether the thumb is settling after a drag ends. */
    var isSettling by mutableStateOf(false)
        internal set

    /** Current Y position of the thumb during drag/settle (in pixels). */
    var dragY by mutableFloatStateOf(0f)
        internal set

    /** Height of the scrollbar container (in pixels). */
    var containerHeight by mutableFloatStateOf(0f)
        internal set

    /** Whether the scrollbar is visible (alpha > 0). */
    var isVisible by mutableStateOf(false)
        internal set

    /** Last item index that triggered a haptic tick. */
    var lastHapticIndex by mutableIntStateOf(-1)
        internal set

    /** Whether the thumb was at the start boundary during the current drag. */
    var wasAtStart by mutableStateOf(false)
        internal set

    /** Whether the thumb was at the end boundary during the current drag. */
    var wasAtEnd by mutableStateOf(false)
        internal set

    // -- Public mutation API --

    fun onDragStart(y: Float, atStart: Boolean, atEnd: Boolean) {
        isSettling = false
        isDragging = true
        dragY = y
        wasAtStart = atStart
        wasAtEnd = atEnd
    }

    fun onDrag(y: Float) {
        dragY = y
    }

    fun onDragEnd() {
        isDragging = false
        isSettling = true
        lastHapticIndex = -1
    }

    fun onDragCancel() {
        isDragging = false
        isSettling = true
        lastHapticIndex = -1
    }

    fun onHapticTick(index: Int) {
        lastHapticIndex = index
    }

    fun resetHaptic() {
        lastHapticIndex = -1
    }

    fun updateVisibility(visible: Boolean) {
        isVisible = visible
    }

    /** Programmatically show the scrollbar. */
    fun show() {
        isVisible = true
    }

    /** Programmatically hide the scrollbar. */
    fun hide() {
        isVisible = false
    }

    fun markSettled() {
        isSettling = false
    }

    /** Update boundary flags during drag. */
    fun updateBoundaries(atStart: Boolean, atEnd: Boolean) {
        wasAtStart = atStart
        wasAtEnd = atEnd
    }
}

/**
 * Creates and remembers a [ScrollbarStateHolder].
 */
@Composable
fun rememberScrollbarStateHolder(): ScrollbarStateHolder = remember {
    ScrollbarStateHolder()
}
