package com.voxly.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 Expressive Typography configuration.
 * 
 * M3 Expressive特点：
 * 1. 更强的标题字重对比（headline使用SemiBold/Bold）
 * 2. 支持Variable Font（Roboto Flex）
 * 3. 更灵活的letterSpacing
 * 
 * 字体选择策略：
 * - 优先使用系统默认字体以确保性能
 * - 可选：添加Google Fonts的Roboto Flex以获得Variable Font支持
 */

// 默认字体（系统字体，性能最佳）
private val DefaultFontFamily = FontFamily.Default

/**
 * Line height style configuration for consistent vertical rhythm
 * Used to ensure text is properly aligned and has consistent spacing
 */
private val NiaLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

/**
 * Line height style for display text (large headlines)
 */
private val DisplayLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

/**
 * Line height style for body text
 */
private val BodyLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

/**
 * Line height style for label text (small text)
 */
private val LabelLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

/**
 * Expressive Typography - 增强的排版样式
 * 用于需要强调的文本
 */
object ExpressiveTypography {
    // 强调展示文本 - 用于大标题
    val EmphasizedDisplay = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp
    )

    // 强调标题
    val EmphasizedHeadline = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    )

    // 紧凑标签
    val CompactLabel = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

    // 强调正文
    val EmphasizedBody = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    )

    // 卡片标题 - 用于列表项、卡片标题
    val CardTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    // 按钮标签 - 用于主要按钮文字
    val ButtonLabel = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )

    // 小字说明 - 用于辅助信息、时间戳
    val Caption = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )

    // 列表项标题 - 用于歌曲名、文件名
    val ListItemTitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )

    // 副标题 - 用于艺术家、专辑名
    val Subtitle = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
}

/**
 * Main Typography configuration for Material 3 Expressive.
 * 
 * 关键改进：
 * - headlineLarge到headlineSmall使用Bold/SemiBold（比标准M3更粗）
 * - titleLarge使用SemiBold增强
 * - body和label保持标准
 */
val Typography = Typography(
    // Display - 超大展示文本
    displayLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        lineHeightStyle = DisplayLineHeightStyle,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        lineHeightStyle = DisplayLineHeightStyle,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        lineHeightStyle = DisplayLineHeightStyle,
        letterSpacing = 0.sp
    ),

    // Headline - 强调使用Bold/ExtraBold（M3 Expressive特点）
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.sp
    ),

    // Title - 使用SemiBold增强
    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = NiaLineHeightStyle,
        letterSpacing = 0.1.sp
    ),

    // Body - 保持标准
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = BodyLineHeightStyle,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = BodyLineHeightStyle,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = BodyLineHeightStyle,
        letterSpacing = 0.4.sp
    ),

    // Label - 保持标准
    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        lineHeightStyle = LabelLineHeightStyle,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = LabelLineHeightStyle,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        lineHeightStyle = LabelLineHeightStyle,
        letterSpacing = 0.5.sp
    )
)

/**
 * Emphasized title used for page/section/row titles — `titleMedium` at
 * SemiBold weight. Screens used to inline this `copy(...)`; centralizing it
 * keeps the "titles are SemiBold" intent in one place.
 */
val emphasizedTitleMedium: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
