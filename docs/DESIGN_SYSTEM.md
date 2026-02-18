# Voxly Design System - MD3 Expressive

## Introduction

This document outlines the design system for the Voxly MP3 Tag Editor app, based on Material Design 3 Expressive.

## Design Principles

1. **Playful & Expressive**: Use rounded corners and vibrant colors to create a friendly, modern feel
2. **Tonal Surfaces**: Use color tonal variations instead of elevation shadows
3. **Physics Motion**: Use spring-based animations for natural, responsive feedback
4. **Accessible**: Maintain WCAG AA contrast ratios and touch targets

## Color System

### Primary Colors

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `primary` | #6B5E95 | #D4C1E8 | Main actions, FAB |
| `onPrimary` | #FFFFFF | #3B3063 | Text on primary |
| `primaryContainer` | #E8E0F0 | #52467B | Primary surfaces |
| `onPrimaryContainer` | #251F3A | #E8E0F0 | Text on primary container |

### Secondary Colors

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `secondary` | #745D69 | #E4BDC4 | Secondary actions |
| `tertiary` | #7E5F58 | #EFC4BC | Accents |

### Surface Container Colors (MD3 Expressive)

The new tonal surface system provides visual hierarchy without elevation:

```
Surface Container Hierarchy (Light):
surfaceContainerLowest (lightest)
    ↓
surfaceContainerLow
    ↓
surfaceContainer ← Default
    ↓
surfaceContainerHigh
    ↓
surfaceContainerHighest (darkest)
```

**Usage Guidelines**:
- `surfaceContainerLowest`: Modal backgrounds
- `surfaceContainerLow`: Cards, elevated surfaces
- `surfaceContainer`: Default content areas
- `surfaceContainerHigh`: Bottom sheets, floating panels
- `surfaceContainerHighest`: Dialogs, popups

## Typography

Using system default font (`FontFamily.Default`):

| Style | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| displayLarge | 57sp | Normal | 64sp | - |
| headlineLarge | 32sp | Normal | 40sp | Screen titles |
| titleLarge | 22sp | Medium | 28sp | Section headers |
| titleMedium | 16sp | Medium | 24sp | Card titles |
| bodyLarge | 16sp | Normal | 24sp | Main content |
| bodyMedium | 14sp | Normal | 20sp | Secondary content |
| labelLarge | 14sp | Medium | 20sp | Buttons |

## Shape System

MD3 Expressive uses more rounded corners:

| Token | Corner Radius | Usage |
|-------|---------------|-------|
| extraSmall | 4.dp | Small elements, tags |
| small | 8.dp | Chips, input fields |
| **medium** | 12.dp | **Buttons, cards (default)** |
| large | 16.dp | Large cards, dialogs |
| **extraLarge** | 28.dp | **Sheets, large surfaces** |

## Motion System

### Animation Principles

- **Emphasized**: For interactive elements (buttons, FABs)
- **Standard**: For state changes (selection, focus)
- **Responsive**: For loading states

### Duration Constants

| Duration | Milliseconds | Usage |
|----------|--------------|-------|
| Short | 150ms | Micro-interactions |
| Medium | 300ms | Standard transitions |
| Long | 500ms | Large movements |

### Spring Configurations

```kotlin
// Emphasized - bouncy, playful
EmphasizedSpring: damping=0.6, stiffness=medium

// Standard - smooth, natural  
StandardSpring: damping=1.0, stiffness=medium-low

// Responsive - quick, responsive
ResponsiveSpring: damping=0.5, stiffness=low
```

## Component Guidelines

### Buttons

- Use `medium` shape (12dp radius)
- Minimum touch target: 48dp
- Primary button: Use `primary` color
- Secondary button: Use `outlined` or `text` variant

### Cards

- Use `surfaceContainerLow` for card background
- Use `medium` or `large` shape depending on content density
- No elevation shadows (use tonal surfaces)

### Bottom Sheets

- Use `surfaceContainerHigh` for background
- Use `extraLarge` shape for top corners (28dp)

### Dialogs

- Use `surfaceContainerHighest` for background
- Use `extraLarge` shape (28dp)
- Center-aligned content

## Accessibility

- Minimum contrast ratio: 4.5:1 (WCAG AA)
- Touch targets: minimum 48dp
- Focus indicators: visible in all modes
- Reduced motion: respect system `prefers-reduced-motion`

## File Structure

```
app/src/main/java/com/voxly/
├── core/
│   └── AndroidVersionUtils.kt       # Version checks
└── presentation/
    ├── theme/
    │   ├── ColorSchemes.kt         # Expressive color schemes
    │   ├── Shapes.kt              # Shape tokens
    │   ├── Motion.kt               # Animation utilities
    │   ├── Typography.kt           # Typography (standard MD3)
    │   └── Theme.kt               # Main theme composable
    └── components/
        └── MD3ExpressiveComponents.kt  # Usage examples
```

## Dependencies

- Compose BOM: 2026.02.00
- Material 3: (via BOM)
- Min SDK: 28 (Android 9)
- Target SDK: 36 (Android 16)

## Further Reading

- [Material 3 Expressive](https://m3.material.io/)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Material Motion](https://m3.material.io/styles/motion/overview)
