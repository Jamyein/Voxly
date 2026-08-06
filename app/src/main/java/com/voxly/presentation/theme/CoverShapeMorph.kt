@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.voxly.presentation.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * 封面形状渐变（Cookie9Sided 多边形 → 圆角方形）。
 *
 * 共享过渡的 bounds 动画只插值位置/尺寸，不插值 clip 形状；overlay 渲染的是目标端内容的
 * 实时快照（每帧重录 GraphicsLayer），因此给目标端的 clip 一个随过渡进度变化的形状，
 * overlay 就会实时显示形状渐变（见 CoverShapeMorph 的实现说明）。
 *
 * 本文件提供两端共用的 Morph 形状：
 * - **源端（歌曲行）**：progress = 0 → 与 `MaterialShapes.Cookie9Sided` 完全一致；
 * - **目标端（元数据编辑器）**：progress = 1 → 圆角方形（圆角 ≈ `MaterialTheme.shapes.extraLarge`
 *   28dp 在 ~340dp 封面上的观感），作为编辑器的稳态形状。
 *
 * 形状坐标空间：两端多边形都 normalize 到 (0,0)-(1,1) 单位方块，`createOutline` 时按组件
 * 尺寸缩放并居中（与 M3 `RoundedPolygon.toShape()` 相同手法）。
 */

/** 源端多边形：M3 Expressive 的 Cookie9Sided（normalized 单位空间）。 */
private val CookiePolygon = MaterialShapes.Cookie9Sided

/**
 * 目标端多边形：4 顶点**轴对齐**圆角方形。注意不能用 `RoundedPolygon(numVertices = 4, ...)` ——
 * 那会生成顶点在 0°/90°/180°/270° 的菱形（对角在轴向），显示为旋转 45° 的方块；轴对齐矩形
 * 必须用 `RoundedPolygon.rectangle(width, height, rounding)`（顶点在四角）。
 * `CornerRounding.radius` 是相对多边形整体尺寸的圆角半径，0.08 ≈ 8%（340dp 封面 ≈ 27dp
 * 圆角，视觉上约等于 extraLarge 的 28dp）。如需微调圆润度，只改这一个常量即可。
 */
private val RoundedSquarePolygon = RoundedPolygon.rectangle(
    width = 1f,
    height = 1f,
    rounding = CornerRounding(radius = 0.08f),
).normalized()

/**
 * 返回随 [progress] 从 Cookie9Sided（0f）渐变到圆角方形（1f）的 [Shape]。
 *
 * [Morph] 在构造时一次性完成特征匹配（featureMapper，开销集中在构造），之后每帧
 * `toPath(progress)` 只把 ~30-50 条 cubic 写出 Path，微秒级，足以支撑过渡期间逐帧重建。
 */
@Composable
fun rememberCoverMorphShape(progress: Float): Shape {
    val morph = remember { Morph(CookiePolygon, RoundedSquarePolygon) }
    return remember(morph, progress) { MorphShape(morph, progress) }
}

/** 封面形状渐变的稳态终点 —— 圆角方形（编辑器详情封面、占位等无过渡场景使用）。 */
@Composable
fun rememberSettledCoverShape(): Shape = rememberCoverMorphShape(1f)

private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        val scaleMatrix = Matrix().apply { scale(size.width, size.height) }
        path.transform(scaleMatrix)
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}
