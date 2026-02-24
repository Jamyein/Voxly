package com.voxly.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 Expressive Shapes
 * 
 * 单一数据源：所有形状定义在 ExpressiveShapes 对象中
 * Shapes 对象作为 MaterialTheme 的代理
 * 
 * MD3 Expressive特点：
 * 1. 更圆的角 - 创建活泼、友好的外观
 * 2. extraLarge = 28dp - 用于卡片、按钮等主要组件
 * 3. 完整的形状层级 - 从4dp到28dp
 * 
 * 形状使用指南：
 * - extraSmall (4dp): 小型组件，如Chip、小图标
 * - small (8dp): 输入框、小按钮
 * - medium (12dp): 中型组件，如Cards
 * - large (16dp): 大型组件，如对话框
 * - extraLarge (28dp): 主要交互元素，如FAB、主要按钮（Expressive特点）
 */

/**
 * Expressive形状 - 单一数据源
 * 
 * 提供所有形状定义，包括：
 * - 标准 MD3 Shapes 尺寸 (extraSmall ~ extraLarge)
 * - 扩展形状 (Circle, ExtraRounded, TopRounded 等)
 */
object ExpressiveShapes {
    // ========== 标准 MD3 Shapes 尺寸 ==========
    
    /** 小型组件，如 Chip、小图标 */
    val ExtraSmall = RoundedCornerShape(4.dp)
    
    /** 输入框、小按钮 */
    val Small = RoundedCornerShape(8.dp)
    
    /** 中型组件，如 Cards */
    val Medium = RoundedCornerShape(12.dp)
    
    /** 大型组件，如对话框 */
    val Large = RoundedCornerShape(16.dp)
    
    /** 主要交互元素，如 FAB、主要按钮（Expressive 特点） */
    val ExtraLarge = RoundedCornerShape(28.dp)
    
    // ========== 扩展形状 ==========
    
    /** 完全圆形 - 用于头像、圆形按钮 */
    val Circle = RoundedCornerShape(50.percent)
    
    /** 极度圆润 - 用于特殊强调元素 */
    val ExtraRounded = RoundedCornerShape(32.dp)
    
    /** 轻微圆角 - 用于列表项 */
    val SlightlyRounded = RoundedCornerShape(6.dp)
    
    /** 无圆角 - 用于 Toolbar、分割线 */
    val None = RoundedCornerShape(0.dp)
    
    /** 顶部圆角（用于底部弹出面板） */
    val TopRounded = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    
    /** 底部圆角（用于顶部弹出面板） */
    val BottomRounded = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp
    )
}

/**
 * 供 MaterialTheme 使用
 * 代理至 ExpressiveShapes 以保持单一数据源
 */
val Shapes = Shapes(
    extraSmall = ExpressiveShapes.ExtraSmall,
    small = ExpressiveShapes.Small,
    medium = ExpressiveShapes.Medium,
    large = ExpressiveShapes.Large,
    extraLarge = ExpressiveShapes.ExtraLarge
)

/**
 * 百分比扩展
 */
private val Int.percent: androidx.compose.ui.unit.Dp
    get() = (this / 100f).dp
