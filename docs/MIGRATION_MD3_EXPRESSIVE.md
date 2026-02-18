# MD3 to MD3 Expressive Migration Guide

## Overview

This document describes the changes made to upgrade the Voxly app from Material Design 3 to Material 3 Expressive.

## What is MD3 Expressive?

Material 3 Expressive is an evolution of Material Design 3 that emphasizes:
- **Playful appearance** with more rounded corners
- **Tonal surface system** using color instead of elevation for visual separation
- **Physics-based animations** for more natural motion
- **Expressive color schemes** with distinct visual identity

## Changes Made

### 1. Color Schemes

**New File**: `app/src/main/java/com/voxly/presentation/theme/ColorSchemes.kt`

Added Expressive color schemes with the new Surface Container colors:

| Color Token | Light Mode | Dark Mode | Usage |
|-------------|------------|-----------|-------|
| `surfaceContainerLowest` | #FFFFFF | #0F0D13 | Highest elevation surfaces |
| `surfaceContainerLow` | #F7F2F8 | #1D1B20 | Card backgrounds |
| `surfaceContainer` | #F3EDF7 | #211F26 | Default surface |
| `surfaceContainerHigh` | #EDE6F2 | #2B292F | Popups, sheets |
| `surfaceContainerHighest` | #E8E2EC | #363339 | Dialogs |

### 2. Shapes

**New File**: `app/src/main/java/com/voxly/presentation/theme/Shapes.kt`

MD3 Expressive uses more rounded corners:

| Shape Token | Value | Usage |
|-------------|-------|-------|
| `extraSmall` | 4.dp | Small elements |
| `small` | 8.dp | Chips, badges |
| `medium` | 12.dp | Buttons, cards |
| `large` | 16.dp | Cards, dialogs |
| `extraLarge` | 28.dp | Large surfaces |

### 3. Motion

**New File**: `app/src/main/java/com/voxly/presentation/theme/Motion.kt`

Physics-based animation utilities:

- **EmphasizedSpring**: For interactive elements (damping: 0.6, stiffness: medium)
- **StandardSpring**: For state changes (damping: 1.0, stiffness: medium-low)
- **ResponsiveSpring**: For responsive feedback (damping: 0.5, stiffness: low)

### 4. Theme Integration

**Modified**: `app/src/main/java/com/voxly/presentation/theme/Theme.kt`

- Integrated Expressive color schemes as fallback for Android 12+ without dynamic color
- Added Shapes to MaterialTheme
- Implemented graceful fallback for Android < 12

### 5. Version Utils

**New File**: `app/src/main/java/com/voxly/core/AndroidVersionUtils.kt`

- `isAtLeastS()`: Check for Android 12+
- `isAtLeastR()`: Check for Android 11+
- `isDynamicColorAvailable()`: Check for Material You

## Compatibility

| Android Version | Dynamic Color | Expressive Static | Fallback |
|-----------------|---------------|-------------------|----------|
| Android 12+ (S) | ✅ | ✅ | - |
| Android 11 (R) | ❌ | ✅ | - |
| Android 10 (Q) | ❌ | ❌ | Standard MD3 |
| Android 9 (P) | ❌ | ❌ | Standard MD3 |

## Migration Checklist

### For New Components

When creating new components, use:

```kotlin
// Use Surface Container colors
Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = MaterialTheme.shapes.medium
) {
    // Content
}

// Use Expressive shapes
Button(
    shape = MaterialTheme.shapes.medium,
    onClick = { }
) { Text("Click me") }
```

### For Animations

Use the motion utilities:

```kotlin
import com.voxly.presentation.theme.MotionPresets
import com.voxly.presentation.theme.ExpressiveMotion

// For physics-based animation
val animatedValue by animateFloatAsState(
    targetValue = target,
    animationSpec = MotionPresets.StateChange
)
```

## Rollback Instructions

If issues arise, the changes can be rolled back by:

1. Reverting changes to `Theme.kt` to use original color schemes
2. Removing the `shapes` parameter from MaterialTheme
3. Deleting new theme files (ColorSchemes.kt, Shapes.kt, Motion.kt)

## Known Issues

- Pre-existing compilation errors in `AudioFileScanner.kt` and `RepositoriesImpl.kt` are unrelated to this migration
- Full MotionScheme API requires Material 3 Compose 1.4.0+ (currently using fallback utilities)

## References

- [Material 3 Expressive](https://m3.material.io/)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Motion in Material Design](https://m3.material.io/styles/motion/overview)
