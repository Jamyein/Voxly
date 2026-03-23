package com.voxly.presentation.components.lyricsposter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compose UI 截图工具
 * 
 * 将 Compose UI 内容渲染为 Bitmap
 * 使用 ComposeView 进行 measure/layout/draw
 */
object PosterCaptureUtil {

    /**
     * 截图参数
     */
    data class CaptureParams(
        val width: Int = 800,
        val maxHeight: Int = 2400
    )

    /**
     * 将 Compose UI 内容截图保存为 Bitmap
     * 
     * 注意：必须在主线程调用，但 Bitmap 创建在后台线程
     * 
     * @param context Android Context
     * @param content Compose UI 内容
     * @param params 截图参数
     * @return 生成的 Bitmap
     */
    suspend fun captureToBitmap(
        context: Context,
        content: @Composable () -> Unit,
        params: CaptureParams = CaptureParams()
    ): Bitmap = withContext(Dispatchers.Main) {
        // 创建 ComposeView
        val composeView = ComposeView(context)
        
        // 设置内容
        composeView.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(2f, 1f)
            ) {
                content()
            }
        }

        // 测量
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        
        composeView.measure(widthMeasureSpec, heightMeasureSpec)
        
        val width = composeView.measuredWidth
        val height = minOf(composeView.measuredHeight, params.maxHeight)
        
        // 布局
        composeView.layout(0, 0, width, height)

        // 等待布局完成
        composeView.post {
            // 布局已完成
        }

        // 绘制到 Bitmap（在后台线程执行以避免阻塞主线程）
        withContext(Dispatchers.Default) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 在主线程执行 draw
            withContext(Dispatchers.Main) {
                composeView.draw(canvas)
            }
            
            bitmap
        }
    }

    /**
     * 测量 ComposeView 的高度（用于预览）
     */
    suspend fun measureHeight(
        context: Context,
        content: @Composable () -> Unit,
        width: Int = 800
    ): Int = withContext(Dispatchers.Main) {
        val composeView = ComposeView(context)
        
        composeView.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(2f, 1f)
            ) {
                content()
            }
        }

        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        
        composeView.measure(widthMeasureSpec, heightMeasureSpec)
        
        composeView.measuredHeight
    }
}
