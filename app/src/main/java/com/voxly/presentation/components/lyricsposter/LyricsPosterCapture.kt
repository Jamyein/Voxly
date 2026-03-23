package com.voxly.presentation.components.lyricsposter

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 歌词海报 GraphicsLayer 捕获器
 *
 * 使用 rememberGraphicsLayer() 捕获 Compose UI 海报为位图
 * 确保预览和导出的图片像素级一致
 */
@Stable
class PosterCapture(
    internal val graphicsLayer: GraphicsLayer,
    private val scope: CoroutineScope
) {
    private var contentSize: IntSize = IntSize.Zero
    private var isReady: Boolean = false

    internal fun updateSize(size: IntSize) {
        contentSize = size
        isReady = size.width > 0 && size.height > 0
    }

    /**
     * 是否已准备好捕获
     */
    fun isReady(): Boolean = isReady

    /**
     * 捕获海报为 Bitmap
     * 注意：必须先使用 PosterCaptureBox 渲染内容到 graphicsLayer
     */
    suspend fun capture(): Bitmap? = withContext(Dispatchers.Default) {
        if (!isReady || contentSize == IntSize.Zero) {
            return@withContext null
        }

        try {
            graphicsLayer.toImageBitmap().asAndroidBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 异步捕获海报
     */
    fun captureAsync(onCaptured: (Bitmap?) -> Unit) {
        scope.launch {
            val bitmap = capture()
            onCaptured(bitmap)
        }
    }
}

/**
 * 创建并记住海报捕获器
 */
@Composable
fun rememberPosterCapture(): PosterCapture {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    return remember(graphicsLayer, scope) {
        PosterCapture(graphicsLayer, scope)
    }
}

/**
 * 海报捕获容器
 *
 * 将 Compose UI 海报包装在此容器中，自动记录到 GraphicsLayer
 */
@Composable
fun PosterCaptureBox(
    capture: PosterCapture,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                capture.updateSize(
                    IntSize(
                        coordinates.size.width,
                        coordinates.size.height
                    )
                )
            }
            .drawWithContent {
                // 将 Compose UI 内容记录到 GraphicsLayer（使用 capture 中的实例）
                capture.graphicsLayer.record(
                    size = IntSize(size.width.toInt(), size.height.toInt())
                ) {
                    this@drawWithContent.drawContent()
                }
                // 绘制 GraphicsLayer 内容（用于预览显示）
                drawLayer(capture.graphicsLayer)
            }
    ) {
        content()
    }
}
