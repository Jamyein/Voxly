package com.voxly.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.voxly.R

/**
 * Material Design 3 Expressive Typography configuration.
 *
 * M3 Expressive特点：
 * 1. 更强的标题字重对比（headline使用SemiBold/Bold）
 * 2. 支持Variable Font（Google Sans Flex）
 * 3. 更灵活的letterSpacing
 *
 * 字体选择策略：
 * - 默认字体为 Google Sans Flex（OFL 1.1），一个 variable font 覆盖全部字重
 *   （wght 轴 100..1000），无需为每个字重打包独立文件
 * - Google Sans Flex 仅含 Latin/Latin-Ext/Vietnamese 字形；中文（CJK）无字形，
 *   会自动回退到系统字体（与 ReadYou、Google 自家 App 的做法一致）
 */

// ---- Google Sans Flex variable font family ----
// 同一份 TTF 以不同 FontVariation 声明多个 Font 条目，Compose 的 FontMatcher
// 按请求字重命中最近条目并应用对应的 variation settings。

private val GoogleSansFlexRegular = Font(
    R.font.google_sans_flex,
    weight = FontWeight.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(400)),
)

private val GoogleSansFlexMedium = Font(
    R.font.google_sans_flex,
    weight = FontWeight.Medium,
    variationSettings = FontVariation.Settings(FontVariation.weight(500)),
)

private val GoogleSansFlexSemiBold = Font(
    R.font.google_sans_flex,
    weight = FontWeight.SemiBold,
    variationSettings = FontVariation.Settings(FontVariation.weight(600)),
)

private val GoogleSansFlexBold = Font(
    R.font.google_sans_flex,
    weight = FontWeight.Bold,
    variationSettings = FontVariation.Settings(FontVariation.weight(700)),
)

private val GoogleSansFlexExtraBold = Font(
    R.font.google_sans_flex,
    weight = FontWeight.ExtraBold,
    variationSettings = FontVariation.Settings(FontVariation.weight(800)),
)

// 斜体：Google Sans Flex 用 slnt 轴（-10..0），而非 ital 轴
private fun googleSansFlexItalic(weight: Int, fontWeight: FontWeight) = Font(
    R.font.google_sans_flex,
    weight = fontWeight,
    style = FontStyle.Italic,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.slant(-10f),
    ),
)

private val GoogleSansFlexRegularItalic = googleSansFlexItalic(400, FontWeight.Normal)
private val GoogleSansFlexMediumItalic = googleSansFlexItalic(500, FontWeight.Medium)
private val GoogleSansFlexSemiBoldItalic = googleSansFlexItalic(600, FontWeight.SemiBold)
private val GoogleSansFlexBoldItalic = googleSansFlexItalic(700, FontWeight.Bold)
private val GoogleSansFlexExtraBoldItalic = googleSansFlexItalic(800, FontWeight.ExtraBold)

/**
 * 默认字体族：Google Sans Flex variable font。
 * 首个无 variation 的条目作为兜底（渲染默认实例），其余条目按字重/斜体命中。
 */
private val DefaultFontFamily = FontFamily(
    Font(R.font.google_sans_flex),
    GoogleSansFlexRegular,
    GoogleSansFlexMedium,
    GoogleSansFlexSemiBold,
    GoogleSansFlexBold,
    GoogleSansFlexExtraBold,
    GoogleSansFlexRegularItalic,
    GoogleSansFlexMediumItalic,
    GoogleSansFlexSemiBoldItalic,
    GoogleSansFlexBoldItalic,
    GoogleSansFlexExtraBoldItalic,
)

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
