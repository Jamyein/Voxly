# 页面过渡效果优化设计

**日期:** 2026-03-13
**项目:** Voxly Android MP3 Tag Editor

## 1. 概述

优化应用页面过渡效果，将现有的 tween 动画替换为 spring 物理动画，减少过渡时长，提升响应速度，同时添加预测性返回支持和触觉反馈，符合 Material Design 3 Expressive (M3E) 设计规范。

**注:** 时长选择 B (350ms/250ms) 是用户明确要求的优化方案，旨在获得更快速的响应体验。

## 2. 核心改动

| 改动项 | 当前实现 | 优化后 |
|--------|----------|--------|
| 动画类型 | tween (线性) | spring (物理弹簧) |
| Enter 时长 | 500ms | 350ms |
| Exit 时长 | 300ms | 250ms |
| 底部导航切换 | 滑动+淡入 | Fade Through + 缩放 |
| 预测性返回 | 无 | 支持 Android 14+ |
| 触觉反馈 | 无 | 添加 |

## 3. Spring 动画参数

**说明:** Spring 动画是物理驱动的，通过 `dampingRatio` 和 `stiffness` 参数控制。为实现快速响应，使用以下配置：

```kotlin
// Enter 动画：强调型弹簧，轻微弹性，更快响应
val M3E_Enter_Spring = spring(
    dampingRatio = 0.72f,    // 轻微弹性
    stiffness = 600f         // 较高的刚度实现快速响应
)

// Exit 动画：更硬的弹簧，退出更迅速
val M3E_Exit_Spring = spring(
    dampingRatio = 0.72f,
    stiffness = 700f         // 更高的刚度
)
```

**关于时长:** 由于 Spring 是物理驱动的，实际时长由物理参数决定。为满足用户对 350ms/250ms 的需求，我们将使用 `spring()` 配合 `durationMillis` 包装，或使用调整后的参数使动画自然落在目标时长范围内。

## 4. 场景一：底部导航切换 (Tab → Tab)

**适用场景:** 在 BottomNavigationBar 的不同 Tab 之间切换

**设计规范:** 采用 Fade Through (淡入淡出穿越) + 非对称轻微深度缩放

**Enter (进入新 Tab):**
```kotlin
fadeIn(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
)
```

**Exit (离开当前 Tab):**
```kotlin
fadeOut(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
) + scaleOut(
    targetScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
)
```

## 5. 场景二：层级页面跳转 (列表 → 详情)

**适用场景:** 从顶级列表页跳转到全屏详情页（如文件浏览器 → 元数据编辑器）

### Forward Enter (新页面抬升)
```kotlin
slideInVertically(
    initialOffsetY = { it / 10 },
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
) + fadeIn(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
)
```

### Forward Exit (旧页面被遮挡)
```kotlin
scaleOut(
    targetScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
) + fadeOut(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
)
```

### Pop Enter (返回时的恢复)
```kotlin
scaleIn(
    initialScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
) + fadeIn(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f)
)
```

### Pop Exit (退出当前页面)
```kotlin
slideOutVertically(
    targetOffsetY = { it / 10 },
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
) + fadeOut(
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
) + scaleOut(
    targetScale = 0.95f,
    animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f)
)
```

## 6. 多层级导航场景

**适用场景:** 超过 2 层的导航（如 FileBrowser → MetadataEditor → OnlineCoverSearch）

对于多层导航，使用统一的"详情页"过渡动画，无需特殊配置。动画效果会自动应用到每一层级的变化。

## 7. 预测性返回 (Predictive Back)

适用于需要拦截的页面（如元数据编辑器未保存时）。

**集成方案 - 在 MetadataEditorScreen 中使用:**

```kotlin
import androidx.activity.compose.predictiveBackHandler

@Composable
fun MetadataEditorScreen(
    // ... existing parameters
    hasUnsavedChanges: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val scale = remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.floatValue
                scaleY = scale.floatValue
            }
    ) {
        // Screen content
    }

    // 仅在有未保存更改时拦截
    if (hasUnsavedChanges) {
        predictiveBackHandler { progress ->
            // 0.0f - 1.0f 期间，UI 容器跟随缩小
            scale.floatValue = 1f - (progress * 0.1f) // 最小到 0.9f

            awaitCancellation {
                // 取消返回时弹回
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    // 返回确认对话框
    if (hasUnsavedChanges) {
        AlertDialog(
            onDismissRequest = { /* dismiss */ },
            title = { Text("未保存的更改") },
            text = { Text("确定要放弃更改吗？") },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { /* dismiss */ }) {
                    Text("取消")
                }
            }
        )
    }
}
```

## 8. 触觉反馈

在导航动作完成后触发 M3E 标志性的物理质感：

```kotlin
import androidx.compose.material3.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun NavigationWrapper(
    onNavigationComplete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(onNavigationComplete) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}
```

**简化方案:** 也可以在 NavHost 层级统一处理，使用 `AnimatedContent` 的 `onAnimationFinished` 回调。

## 9. 需要修改的文件

### 9.1 Motion.kt 更新

```kotlin
// 更新 ExpressiveAnimations 对象，添加 Spring 版本

object ExpressiveAnimations {
    // Spring 动画参数
    private const val EmphasizedEnterDuration = 350  // 用户选择 B
    private const val EmphasizedExitDuration = 250  // 用户选择 B

    // Enter 弹簧：强调型，轻微弹性
    private val EmphasizedEnterSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 600f
    )

    // Exit 弹簧：更硬，更快
    private val EmphasizedExitSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 700f
    )

    // ===== 新增 Spring 动画 =====

    // 底部导航 Enter (Fade Through + Scale)
    val BottomNavEnter = fadeIn(
        animationSpec = EmphasizedEnterSpring
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    )

    // 底部导航 Exit
    val BottomNavExit = fadeOut(
        animationSpec = EmphasizedExitSpring
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    )

    // 层级页面 Enter
    val PageEnterM3E = slideInVertically(
        initialOffsetY = { it / 10 },
        animationSpec = EmphasizedEnterSpring
    ) + fadeIn(animationSpec = EmphasizedEnterSpring) + scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    )

    // 层级页面 Exit
    val PageExitM3E = scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    ) + fadeOut(animationSpec = EmphasizedExitSpring)

    // Pop Enter (返回恢复)
    val PagePopEnterM3E = scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    ) + fadeIn(animationSpec = EmphasizedEnterSpring)

    // Pop Exit (退出)
    val PagePopExitM3E = slideOutVertically(
        targetOffsetY = { it / 10 },
        animationSpec = EmphasizedExitSpring
    ) + fadeOut(animationSpec = EmphasizedExitSpring) + scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    )
}
```

### 9.2 MP3TagNavHost.kt 更新

更新导航过渡配置以使用新的 Spring 动画：

```kotlin
NavHost(
    enterTransition = {
        when {
            // 底部导航主页间
            fromRoute in destinations && toRoute in destinations ->
                ExpressiveAnimations.BottomNavEnter

            // 从非主页返回
            fromRoute !in destinations && toRoute in destinations ->
                ExpressiveAnimations.PagePopEnterM3E

            // 其他进入
            else ->
                ExpressiveAnimations.PageEnterM3E
        }
    },
    exitTransition = {
        when {
            fromRoute in destinations && toRoute in destinations ->
                ExpressiveAnimations.BottomNavExit
            fromRoute in bottomNavRoutes && toRoute !in bottomNavRoutes ->
                ExpressiveAnimations.PageExitM3E
            else ->
                ExpressiveAnimations.PageExitM3E
        }
    },
    popEnterTransition = {
        ExpressiveAnimations.PagePopEnterM3E
    },
    popExitTransition = {
        ExpressiveAnimations.PagePopExitM3E
    }
)
```

### 9.3 MetadataEditorScreen.kt (可选)

添加 PredictiveBackHandler 支持以拦截未保存的更改。

## 10. 预期效果

- 过渡响应速度提升 30%
- Spring 物理动画带来 M3E 标志性的"生动"质感
- 底部导航切换更流畅，不再有拖沓感
- Android 14+ 用户体验预测性返回手势
- 触觉反馈增强交互沉浸感

## 11. M3E 规范参考

参考 `.claude/rules/m3e_navigation.clauderules.md` 中的规范：
- Emphasized Easing 用于 Enter 动画
- Emphasized Accelerate 用于 Exit 动画
- 缩放基准值为 0.95f
