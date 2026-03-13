# 页面过渡效果优化设计

**日期:** 2026-03-13
**项目:** Voxly Android MP3 Tag Editor

## 1. 概述

优化应用页面过渡效果，将现有的 tween 动画替换为 spring 物理动画，减少过渡时长，提升响应速度，同时添加预测性返回支持和触觉反馈，符合 Material Design 3 Expressive (M3E) 设计规范。

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

```kotlin
// M3E 强调型弹簧规格
val M3E_Emphasized_Spring = spring(
    dampingRatio = 0.72f,  // 轻微弹性
    stiffness = 400f
)

val M3E_Exit_Spring = spring(
    dampingRatio = 0.72f,
    stiffness = 500f  // 退出时稍微快一点
)
```

## 4. 场景一：底部导航切换 (Tab → Tab)

**适用场景:** 在 BottomNavigationBar 的不同 Tab 之间切换

**Enter:**
- `fadeIn` (350ms) + `scaleIn(initialScale = 0.95f, 350ms, spring)`

**Exit:**
- `fadeOut` (250ms) + `scaleOut(targetScale = 0.95f, 250ms, spring)`

## 5. 场景二：层级页面跳转 (列表 → 详情)

**适用场景:** 从顶级列表页跳转到全屏详情页（如文件浏览器 → 元数据编辑器）

### Forward Enter (新页面)
- `slideInVertically(initialOffsetY = { it / 10 }, 350ms, spring)`
- `fadeIn(350ms, spring)`
- `scaleIn(initialScale = 0.95f, 350ms, spring)`

### Forward Exit (旧页面)
- `scaleOut(targetScale = 0.95f, 250ms, spring)`
- `fadeOut(250ms, spring)`

### Pop Enter (返回)
- `scaleIn(initialScale = 0.95f, 300ms, spring)`
- `fadeIn(300ms, spring)`

### Pop Exit (退出)
- `slideOutVertically(targetOffsetY = { it / 10 }, 250ms, spring)`
- `fadeOut(250ms, spring)`
- `scaleOut(targetScale = 0.95f, 250ms, spring)`

## 6. 预测性返回 (Predictive Back)

适用于需要拦截的页面（如元数据编辑器未保存时）。

```kotlin
PredictiveBackHandler(onBack = { progress ->
    // 0.0f - 1.0f 期间，UI 容器跟随缩小
    scale = 1f - (progress * 0.1f)  // 最小到 0.9f
}) {
    // 执行返回导航
    navController.popBackStack()
}
```

## 7. 触觉反馈

在导航动画完成时触发 M3E 标志性的物理质感：

```kotlin
LaunchedEffect(transitionInfo) {
    if (transitionInfo.state == TransitionState.Idle) {
        hapticFeedback.performHapticFeedback(
            HapticFeedbackType.TextHandleMove
        )
    }
}
```

## 8. 需要修改的文件

1. **`app/src/main/java/com/voxly/presentation/theme/Motion.kt`**
   - 更新动画参数，使用 spring 替代 tween
   - 更新时长常量：Enter 350ms, Exit 250ms

2. **`app/src/main/java/com/voxly/presentation/navigation/MP3TagNavHost.kt`**
   - 更新导航过渡配置
   - 使用新的 spring 动画

3. **`app/src/main/java/com/voxly/presentation/screens/metadata/MetadataEditorScreen.kt`** (可选)
   - 添加 PredictiveBackHandler 支持

## 9. 预期效果

- 过渡响应速度提升 30%
- Spring 物理动画带来 M3E 标志性的"生动"质感
- 底部导航切换更流畅，不再有拖沓感
- Android 14+ 用户体验预测性返回手势
- 触觉反馈增强交互沉浸感

## 10. M3E 规范参考

参考 `.claude/rules/m3e_navigation.clauderules.md` 中的规范：
- Emphasized Easing 用于 Enter 动画
- Emphasized Accelerate 用于 Exit 动画
- 缩放基准值为 0.95f
