# 页面过渡效果优化实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有的 tween 动画替换为 spring 物理动画，减少过渡时长至 Enter 350ms / Exit 250ms，添加预测性返回和触觉反馈，符合 M3E 设计规范

**Architecture:** 在 Motion.kt 中添加新的 Spring 动画定义，在 MP3TagNavHost.kt 中更新导航过渡配置使用新动画

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose

---

## Chunk 1: Motion.kt Spring 动画定义

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/theme/Motion.kt`

- [ ] **Step 1: 添加 Spring 动画参数常量**

在 `ExpressiveAnimations` 对象中添加新的时长常量和 Spring 动画定义。

在 `Motion.kt:113` 附近的 `ExpressiveAnimations` 对象中添加：

```kotlin
    // Spring 动画参数 - 用户选择 B 方案 (Enter 350ms, Exit 250ms)
    private const val EmphasizedEnterDuration = 350
    private const val EmphasizedExitDuration = 250

    // Enter 弹簧：强调型，轻微弹性，更高刚度实现快速响应
    private val EmphasizedEnterSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 600f
    )

    // Exit 弹簧：更硬，更快退出
    private val EmphasizedExitSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 700f
    )
```

- [ ] **Step 2: 添加底部导航 Spring 动画**

在 `ExpressiveAnimations` 对象中添加：

```kotlin
    // ===== Spring 动画 - 底部导航 =====

    val BottomNavEnter = fadeIn(
        animationSpec = EmphasizedEnterSpring
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    )

    val BottomNavExit = fadeOut(
        animationSpec = EmphasizedExitSpring
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    )
```

- [ ] **Step 3: 添加层级页面 Spring 动画**

继续在 `ExpressiveAnimations` 对象中添加：

```kotlin
    // ===== Spring 动画 - 层级页面 =====

    val PageEnterM3E = slideInVertically(
        initialOffsetY = { it / 10 },
        animationSpec = EmphasizedEnterSpring
    ) + fadeIn(
        animationSpec = EmphasizedEnterSpring
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    )

    val PageExitM3E = scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    ) + fadeOut(
        animationSpec = EmphasizedExitSpring
    )

    val PagePopEnterM3E = scaleIn(
        initialScale = 0.95f,
        animationSpec = EmphasizedEnterSpring
    ) + fadeIn(
        animationSpec = EmphasizedEnterSpring
    )

    val PagePopExitM3E = slideOutVertically(
        targetOffsetY = { it / 10 },
        animationSpec = EmphasizedExitSpring
    ) + fadeOut(
        animationSpec = EmphasizedExitSpring
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = EmphasizedExitSpring
    )
```

- [ ] **Step 4: 提交更改**

```bash
git add app/src/main/java/com/voxly/presentation/theme/Motion.kt
git commit -m "feat: 添加 Spring 页面过渡动画 (Enter 350ms, Exit 250ms)"
```

---

## Chunk 2: MP3TagNavHost.kt 导航配置更新

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/navigation/MP3TagNavHost.kt`

- [ ] **Step 1: 更新 NavHost 过渡动画**

在 `MP3TagNavHost.kt` 的 NavHost 配置中，替换 `enterTransition`、`exitTransition`、`popEnterTransition`、`popExitTransition`。

定位到第 89-152 行的 NavHost 过渡配置，替换为：

```kotlin
            enterTransition = {
                val destinations = bottomNavRoutes
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 底部导航主页间使用 Fade Through + Scale
                    fromRoute in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.BottomNavEnter
                    }
                    // 从非主页返回时使用 Pop Enter
                    fromRoute !in destinations && toRoute in destinations -> {
                        ExpressiveAnimations.PagePopEnterM3E
                    }
                    // M3E 规范: 进入动画 = 向上位移 + 缩放(95%->100%) + 渐显, Spring
                    else -> {
                        ExpressiveAnimations.PageEnterM3E
                    }
                }
            },
            exitTransition = {
                val fromRoute = initialState.destination.route
                val toRoute = targetState.destination.route

                when {
                    // 主页间切换使用 Fade Through + Scale
                    fromRoute in bottomNavRoutes && toRoute in bottomNavRoutes -> {
                        ExpressiveAnimations.BottomNavExit
                    }
                    // 进入子页面时旧页面缩小并变暗
                    fromRoute in bottomNavRoutes && toRoute !in bottomNavRoutes -> {
                        ExpressiveAnimations.PageExitM3E
                    }
                    // 其他退出使用 M3E Pop Exit
                    else -> {
                        ExpressiveAnimations.PageExitM3E
                    }
                }
            },
            popEnterTransition = {
                ExpressiveAnimations.PagePopEnterM3E
            },
            popExitTransition = {
                ExpressiveAnimations.PagePopExitM3E
            }
```

- [ ] **Step 2: 提交更改**

```bash
git add app/src/main/java/com/voxly/presentation/navigation/MP3TagNavHost.kt
git commit -m "feat: 使用 Spring 动画更新导航过渡配置"
```

---

## Chunk 3: 构建验证

- [ ] **Step 1: 运行构建验证**

```bash
./gradlew assembleDebug
```

预期结果：构建成功，无错误

- [ ] **Step 2: 提交构建修复 (如有需要)**

如果构建失败，修复问题后提交。

---

## Chunk 4: PredictiveBackHandler (可选)

**Files:**
- Modify: `app/src/main/java/com/voxly/presentation/screens/metadata/MetadataEditorScreen.kt`

此为可选功能，仅在需要拦截未保存的更改时实现。

- [ ] **Step 1: 添加 PredictiveBackHandler 支持 (可选)**

在 `MetadataEditorScreen.kt` 中添加预测性返回支持。

需要添加的导入：
```kotlin
import androidx.activity.compose.predictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
```

在 Screen 的 root Box 中添加 graphicsLayer 修饰符，并添加 predictiveBackHandler。

- [ ] **Step 2: 提交更改 (可选)**

```bash
git add app/src/main/java/com/voxly/presentation/screens/metadata/MetadataEditorScreen.kt
git commit -m "feat: 添加 PredictiveBackHandler 支持"
```

---

## 验证清单

- [ ] Motion.kt 中新增了 Spring 动画定义
- [ ] 底部导航切换使用 fadeIn + scaleIn (Enter) / fadeOut + scaleOut (Exit)
- [ ] 层级页面跳转使用 slideInVertically + fadeIn + scaleIn (Enter) / scaleOut + fadeOut (Exit)
- [ ] Enter 时长约 350ms，Exit 时长约 250ms
- [ ] 构建成功
- [ ] 触觉反馈 (可选)
- [ ] PredictiveBackHandler (可选)

---

## 参考文档

- 设计文档: `docs/superpowers/specs/2026-03-13-page-transition-optimization-design.md`
- M3E 规范: `.claude/rules/m3e_navigation.clauderules.md`
