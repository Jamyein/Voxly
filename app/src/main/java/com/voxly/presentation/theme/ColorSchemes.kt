package com.voxly.presentation.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material Design 3 Expressive Color Schemes
 * 
 * MD3 Expressive特点：
 * 1. 更生动的颜色系统 - 使用更深层的色调调色板
 * 2. Surface Container颜色系统 - 替代基于elevation的表面颜色
 * 3. Fixed颜色 - 用于需要保持一致性的组件
 * 4. 语义化扩展颜色 - 用于特定场景
 * 
 * Surface Container层级（从浅到深）:
 * - surfaceContainerLowest: 最浅（白色/深色主题最暗）
 * - surfaceContainerLow: 较浅
 * - surfaceContainer: 默认（基准）
 * - surfaceContainerHigh: 较深
 * - surfaceContainerHighest: 最深（白色/深色主题最亮）
 */

// 基础Expressive颜色调色板
private object ExpressivePalette {
    // Primary调色板 - 紫色系
    val PrimaryLight = Color(0xFF6B5E95)
    val PrimaryDark = Color(0xFFD4C1E8)
    
    // Secondary调色板 - 玫瑰色系
    val SecondaryLight = Color(0xFF745D69)
    val SecondaryDark = Color(0xFFE4BDC4)
    
    // Tertiary调色板 - 珊瑚色系
    val TertiaryLight = Color(0xFF7E5F58)
    val TertiaryDark = Color(0xFFEFC4BC)
    
    // Error调色板
    val ErrorLight = Color(0xFFBA1A1A)
    val ErrorDark = Color(0xFFFFB4AB)
}

/**
 * Expressive Light Color Scheme
 * 
 * 包含：
 * - 基础primary/secondary/tertiary颜色
 * - Container颜色（用于组件背景）
 * - Fixed颜色（用于按钮等保持一致性的组件）
 * - Surface Container层级颜色
 */
val ExpressiveLightColorScheme = lightColorScheme(
    // ===== Primary =====
    primary = ExpressivePalette.PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0F0),
    onPrimaryContainer = Color(0xFF251F3A),
    
    // Primary Fixed（用于需要保持一致性的组件）
    primaryFixed = Color(0xFF6B5E95),
    onPrimaryFixed = Color.White,
    primaryFixedDim = Color(0xFFD4C1E8),
    
    // ===== Secondary =====
    secondary = ExpressivePalette.SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E5),
    onSecondaryContainer = Color(0xFF2C1B22),
    
    // Secondary Fixed
    secondaryFixed = Color(0xFF745D69),
    onSecondaryFixed = Color.White,
    secondaryFixedDim = Color(0xFFE4BDC4),
    
    // ===== Tertiary =====
    tertiary = ExpressivePalette.TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3B1A14),
    
    // Tertiary Fixed
    tertiaryFixed = Color(0xFF7E5F58),
    onTertiaryFixed = Color.White,
    tertiaryFixedDim = Color(0xFFEFC4BC),
    
    // ===== Error =====
    error = ExpressivePalette.ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    // ===== Background & Surface =====
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B20),
    
    // ===== Surface Container Colors (MD3 Expressive tonal surface system) =====
    // 注意：这些颜色之间需要有足够的对比度以形成视觉层级
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3EDF7),
    surfaceContainer = Color(0xFFECE6F0),
    surfaceContainerHigh = Color(0xFFE6E0E9),
    surfaceContainerHighest = Color(0xFFDED8E1),
    
    // ===== Surface Variant =====
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    
    // ===== Outline =====
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    
    // ===== Inverse =====
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF4),
    inversePrimary = Color(0xFFD4C1E8),
    
    // ===== Surface Tint =====
    surfaceTint = Color(0xFF6B5E95),
    
    // ===== Scrim =====
    scrim = Color(0xFF000000)
)

/**
 * Expressive Dark Color Scheme
 */
val ExpressiveDarkColorScheme = darkColorScheme(
    // ===== Primary =====
    primary = ExpressivePalette.PrimaryDark,
    onPrimary = Color(0xFF3B3063),
    primaryContainer = Color(0xFF52467B),
    onPrimaryContainer = Color(0xFFE8E0F0),
    
    // Primary Fixed
    primaryFixed = Color(0xFFD4C1E8),
    onPrimaryFixed = Color(0xFF3B3063),
    primaryFixedDim = Color(0xFF9E8FC7),
    
    // ===== Secondary =====
    secondary = ExpressivePalette.SecondaryDark,
    onSecondary = Color(0xFF402F39),
    secondaryContainer = Color(0xFF584550),
    onSecondaryContainer = Color(0xFFFFD9E5),
    
    // Secondary Fixed
    secondaryFixed = Color(0xFFE4BDC4),
    onSecondaryFixed = Color(0xFF402F39),
    secondaryFixedDim = Color(0xFFBBA2A8),
    
    // ===== Tertiary =====
    tertiary = ExpressivePalette.TertiaryDark,
    onTertiary = Color(0xFF462A26),
    tertiaryContainer = Color(0xFF5E403B),
    onTertiaryContainer = Color(0xFFFFDAD2),
    
    // Tertiary Fixed
    tertiaryFixed = Color(0xFFEFC4BC),
    onTertiaryFixed = Color(0xFF462A26),
    tertiaryFixedDim = Color(0xFFD1A499),
    
    // ===== Error =====
    error = ExpressivePalette.ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    // ===== Background & Surface =====
    background = Color(0xFF1D1B20),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E1E5),
    
    // ===== Surface Container Colors =====
    // 深色主题中层级越低越暗，层级越高越亮（与浅色主题相反）
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1A181D),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B282F),
    surfaceContainerHighest = Color(0xFF363339),
    
    // ===== Surface Variant =====
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    
    // ===== Outline =====
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    
    // ===== Inverse =====
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = Color(0xFF6B5E95),
    
    // ===== Surface Tint =====
    surfaceTint = Color(0xFFD4C1E8),
    
    // ===== Scrim =====
    scrim = Color(0xFF000000)
)

/**
 * 语义化颜色扩展
 * 用于特定场景的便捷访问
 */
object SemanticColors {
    /**
     * 获取状态颜色
     */
    fun successColor(isDark: Boolean) = if (isDark) Color(0xFF81C784) else Color(0xFF4CAF50)
    fun warningColor(isDark: Boolean) = if (isDark) Color(0xFFFFB74D) else Color(0xFFFF9800)
    fun infoColor(isDark: Boolean) = if (isDark) Color(0xFF64B5F6) else Color(0xFF2196F3)
    
    /**
     * 音乐相关的语义颜色
     */
    fun playingColor(isDark: Boolean) = if (isDark) Color(0xFFD4C1E8) else Color(0xFF6B5E95)
    fun selectedColor(isDark: Boolean) = if (isDark) Color(0xFF52467B) else Color(0xFFE8E0F0)
    fun editingColor(isDark: Boolean) = if (isDark) Color(0xFF5E403B) else Color(0xFFFFDAD2)
}

/**
 * Surface Container层级枚举
 * 用于组件中指定背景层级
 */
enum class ContainerLevel {
    Lowest,   // 最浅（浅色）/最深（深色） - 用于最高elevation的卡片
    Low,      // 较浅（浅色）/较暗（深色） - 用于一般卡片
    Medium,   // 默认 - 用于主要内容区
    High,     // 较深（浅色）/较亮（深色） - 用于高亮区域
    Highest   // 最深（浅色）/最亮（深色） - 用于选中状态
}
