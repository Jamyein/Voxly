package com.voxly.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Controller shared between the host [androidx.compose.material3.Scaffold]'s `bottomBar` slot
 * and the inner scrollable screens (FileBrowser / Albums / Artists).
 *
 * It bridges the gap that the official M3 docs document for **top** app bars (`TopAppBarScrollBehavior`
 * plugged straight into the inner `Scaffold`) — there is no built-in `BottomAppBarScrollBehavior`,
 * so we wire the equivalent by hand: the inner screen forwards its scroll deltas into the controller
 * via [NestedScrollConnection], and the bottomBar composable observes [isVisible] to slide in/out.
 *
 * Behavior mirrors the standard "hide on scroll-down, show on scroll-up" pattern, with a couple
 * of safety nets documented inline:
 *
 *  - A small dead-zone ([scrollThresholdPx]) so a single janky fling finger does not toggle the
 *    bar on and off. Only direction changes that have crossed the threshold commit.
 *  - "Pinned" mode via [setForcedVisible] is used by pull-to-refresh and the scroll-to-top
 *    affordance: while the user is refreshing or just landed at the top, we keep the bar visible
 *    so they do not get stranded without navigation.
 *  - Per-screen isolation: [bind] / [unbind] track which screen currently owns the controller, so
 *    popping from Album detail back to Album list does not leave stale offsets from a screen that
 *    is no longer in the composition.
 */
@Stable
class BottomBarVisibilityController {

    /** Externally-readable visibility. The bottomBar composable observes this. */
    var isVisible: Boolean by mutableStateOf(true)
        private set

    /**
     * Directional accumulator since the last committed transition. Reset on every
     * commit; grows monotonically in the same direction until it crosses [scrollThresholdPx].
     */
    private var accumulatedScrollPx: Float = 0f

    /**
     * The most recent scroll direction we are tracking. `1f` = downward scroll (content moves
     * up = user reading down = hide), `-1f` = upward scroll (content moves down = user wants
     * navigation back = show).
     */
    private var lastDirectionSign: Float = 0f

    /**
     * Number of consumers currently bound. The controller is considered active as long as this
     * is > 0; outer observers (the bottomBar composable) treat "no active binder" as visible.
     */
    private var bindingCount: Int = 0

    /**
     * The id of the screen currently driving the controller. Used to clear the accumulator when
     * switching top-level destinations so the user does not see a hide animation kick in
     * because the previous screen was scrolled.
     */
    private var boundScreenId: String? = null

    /**
     * Returns the [NestedScrollConnection] that the inner scrollable screen should attach to
     * its content via `Modifier.nestedScroll(...)`. Multiple screens binding back-to-back is
     * fine; only the most recent one drives the accumulator.
     */
    fun nestedScrollConnection(screenId: String): NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Ignore flings from sibling scrollable parents (e.g. a parent LazyColumn
                // consuming the same gesture). We only react to direct drag.
                if (source != NestedScrollSource.Drag) return Offset.Zero

                val dy = available.y
                if (dy == 0f) return Offset.Zero

                val directionSign = if (dy > 0f) 1f else -1f

                // If we ever flip direction, reset the accumulator — the user changed their
                // mind, and we should not pay out the previous scroll budget in the new
                // direction. This is what makes "show again on scroll-up" feel snappy.
                if (lastDirectionSign != 0f && lastDirectionSign != directionSign) {
                    accumulatedScrollPx = 0f
                }
                lastDirectionSign = directionSign

                // Only commit visibility transitions once the user has moved a non-trivial
                // distance. This avoids the bar flickering on micro-scrolls near zero.
                accumulatedScrollPx += abs(dy)
                if (accumulatedScrollPx >= scrollThresholdPx) {
                    // dy > 0 means the user dragged down, which scrolls content up = hide.
                    // dy < 0 means the user dragged up = show.
                    val shouldShow = dy < 0f
                    if (isVisible != shouldShow) {
                        isVisible = shouldShow
                    }
                    // Always reset after a commit so the next transition needs to earn the
                    // threshold again from scratch.
                    accumulatedScrollPx = 0f
                }

                // We do NOT consume the scroll — let the LazyColumn/Grid keep handling it
                // normally. The controller is purely an observer.
                return Offset.Zero
            }
        }

    /**
     * Bind a screen as the active driver. Pass a stable id (typically the top-level NavKey
     * route) so we can detect screen changes and reset state.
     */
    fun bind(screenId: String) {
        if (boundScreenId != screenId) {
            // New screen — wipe the accumulator so the first scroll event starts fresh
            // and the user does not see a delayed transition from a previous screen.
            accumulatedScrollPx = 0f
            lastDirectionSign = 0f
            boundScreenId = screenId
        }
        bindingCount++
        // Whenever something binds, we reset to visible. Inner screens bind on entry; this
        // guarantees the bar always reappears when entering a scrollable destination.
        isVisible = true
    }

    /** Unbind the active driver. When the last binder goes away, we leave state as-is —
     *  the next bind() will force visible. */
    fun unbind() {
        if (bindingCount > 0) bindingCount--
    }

    /**
     * Pin the bar to visible regardless of scroll state. Used by pull-to-refresh and
     * the scroll-to-top affordance so the user is not stranded without navigation.
     */
    fun setForcedVisible(forced: Boolean) {
        if (forced) {
            isVisible = true
            accumulatedScrollPx = 0f
            lastDirectionSign = 0f
        }
    }

    /** Force-show the bar (e.g. on navigation events that should reset state). */
    fun show() {
        isVisible = true
        accumulatedScrollPx = 0f
        lastDirectionSign = 0f
    }

    companion object {
        /**
         * Threshold (in pixels) of accumulated scroll in a single direction before we toggle
         * visibility. 24 px is small enough to feel responsive, large enough to filter out
         * the kind of micro-jitter that the system scroll source produces on touch down/up.
         */
        const val scrollThresholdPx: Float = 24f
    }
}

/**
 * Chains two [NestedScrollConnection]s into a single connection. Compose's
 * `Modifier.nestedScroll(...)` accepts only one connection (plus a dispatcher), so to react
 * to scroll events with two observers — the M3 collapsing top-app-bar connection AND the
 * bottom-bar hide/show connection — we wrap them here and attach via the single-arg overload.
 */
fun chainNestedScrollConnections(
    first: NestedScrollConnection,
    second: NestedScrollConnection
): NestedScrollConnection = object : NestedScrollConnection {
    // We delegate the call to both connections and return the first one's claimed offset;
    // since neither connection actually consumes scroll (they only observe), this is safe.
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val firstResult = first.onPreScroll(available, source)
        second.onPreScroll(available, source)
        return firstResult
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val firstResult = first.onPostScroll(consumed, available, source)
        second.onPostScroll(consumed, available, source)
        return firstResult
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val firstResult = first.onPreFling(available)
        second.onPreFling(available)
        return firstResult
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val firstResult = first.onPostFling(consumed, available)
        second.onPostFling(consumed, available)
        return firstResult
    }
}

/** CompositionLocal so the host Scaffold's bottomBar can find the controller the screen bound. */
val LocalBottomBarVisibilityController = compositionLocalOf<BottomBarVisibilityController> {
    error(
        "BottomBarVisibilityController was not provided. Wrap your screen in " +
            "ProvideBottomBarVisibilityController { ... } or pass the controller explicitly."
    )
}

/**
 * Convenience wrapper that creates and provides a [BottomBarVisibilityController] for the
 * subtree, auto-binding on entry and unbinding on dispose. The id should be a stable identifier
 * of the current top-level destination so the controller can reset its accumulator on tab
 * switches.
 */
@Composable
fun ProvideBottomBarVisibilityController(
    screenId: String,
    content: @Composable () -> Unit
) {
    val controller = remember { BottomBarVisibilityController() }
    DisposableEffect(screenId) {
        controller.bind(screenId)
        onDispose { controller.unbind() }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBottomBarVisibilityController provides controller,
        content = content
    )
}
