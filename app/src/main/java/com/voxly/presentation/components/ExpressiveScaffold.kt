package com.voxly.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.voxly.presentation.theme.ExpressiveAnimations

/**
 * Material Design 3 Expressive Scaffold
 * 
 * MD3 Expressive Scaffold特点：
 * 1. 使用Surface Container颜色系统
 * 2. 集成ExpressiveTopAppBar
 * 3. 集成ExpressiveNavigationBar
 * 4. 支持FAB动画
 * 5. 优化的内容区域
 */

/**
 * Expressive Scaffold - 主脚手架组件
 */
@Composable
fun ExpressiveScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(hostState = SnackbarHostState()) },
    floatingActionButton: ImageVector? = null,
    onFabClick: (() -> Unit)? = null,
    fabExpanded: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = containerColor,
            topBar = {
                Box(modifier = Modifier) {
                    topBar()
                }
            },
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = {
                if (floatingActionButton != null && onFabClick != null) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = ExpressiveAnimations.FabEnter,
                        exit = ExpressiveAnimations.FabExit
                    ) {
                        FloatingActionButton(
                            onClick = onFabClick,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Icon(
                                imageVector = floatingActionButton,
                                contentDescription = "Action"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                content()
            }
        }
    }
}

/**
 * Expressive Scaffold with Top App Bar
 */
@Composable
fun ExpressiveScaffoldWithTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(hostState = SnackbarHostState()) },
    floatingActionButton: ImageVector? = null,
    onFabClick: (() -> Unit)? = null,
    fabExpanded: Boolean = true,
    titleCentered: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveScaffold(
        modifier = modifier,
        topBar = {
            ExpressiveTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                onNavigationClick = onNavigationClick,
                actions = actions,
                titleCentered = titleCentered,
                containerColor = containerColor
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        onFabClick = onFabClick,
        fabExpanded = fabExpanded,
        containerColor = containerColor,
        content = content
    )
}

/**
 * Expressive Scaffold with Large Top App Bar
 */
@Composable
fun ExpressiveScaffoldWithLargeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(hostState = SnackbarHostState()) },
    floatingActionButton: ImageVector? = null,
    onFabClick: (() -> Unit)? = null,
    fabExpanded: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveScaffold(
        modifier = modifier,
        topBar = {
            ExpressiveLargeTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                onNavigationClick = onNavigationClick,
                actions = actions,
                containerColor = containerColor
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        onFabClick = onFabClick,
        fabExpanded = fabExpanded,
        containerColor = containerColor,
        content = content
    )
}

/**
 * Expressive Scaffold with Medium Top App Bar
 */
@Composable
fun ExpressiveScaffoldWithMediumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(hostState = SnackbarHostState()) },
    floatingActionButton: ImageVector? = null,
    onFabClick: (() -> Unit)? = null,
    fabExpanded: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveScaffold(
        modifier = modifier,
        topBar = {
            ExpressiveMediumTopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                onNavigationClick = onNavigationClick,
                actions = actions,
                containerColor = containerColor
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        onFabClick = onFabClick,
        fabExpanded = fabExpanded,
        containerColor = containerColor,
        content = content
    )
}

/**
 * Expressive Scaffold with Back Navigation
 */
@Composable
fun ExpressiveScaffoldWithBack(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = { SnackbarHost(hostState = SnackbarHostState()) },
    floatingActionButton: ImageVector? = null,
    onFabClick: (() -> Unit)? = null,
    fabExpanded: Boolean = true,
    titleCentered: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.() -> Unit
) {
    ExpressiveScaffoldWithTopBar(
        modifier = modifier,
        title = title,
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onNavigationClick = onBackClick,
        actions = actions,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        onFabClick = onFabClick,
        fabExpanded = fabExpanded,
        titleCentered = titleCentered,
        containerColor = containerColor,
        content = content
    )
}
