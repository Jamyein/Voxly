package com.voxly.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Standard pull-to-refresh wrapper for library list screens (Files, Albums,
 * Artists). Uses `Modifier.pullToRefresh` (lower-level than `PullToRefreshBox`)
 * so the [LoadingIndicator] renders ABOVE any top bar — standard M3 behavior.
 *
 * Pass the collapsing top bar's [TopAppBarScrollBehavior] to resolve the pull-to-refresh /
 * bar-re-expansion conflict: while the bar is collapsed, the pull gesture must re-expand the
 * bar, so refresh is disabled until `collapsedFraction` reaches 0; once fully expanded, the
 * same gesture continues to drive refresh (expand-then-refresh, iOS-style). `null` (pinned
 * bar, or no bar) keeps refresh always enabled — it only matters for collapsing bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    // Gate the pull consumption on the bar being fully expanded. Both the pull node and the
    // bar's scroll behavior claim the top overscroll in onPostScroll, which dispatches
    // innermost-first — the pull node (closer to the list) would otherwise swallow the overscroll
    // the collapsed bar needs to expand. derivedStateOf: recompose only when the boolean flips.
    val enabled by remember(scrollBehavior?.state) {
        derivedStateOf {
            scrollBehavior == null || scrollBehavior.state.collapsedFraction == 0f
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                enabled = enabled,
                onRefresh = onRefresh
            )
    ) {
        content()
        LoadingIndicator(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}