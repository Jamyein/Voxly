package com.voxly.util

import android.icu.text.Transliterator
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * 排序工具类 - 提供中文转拼音等排序辅助功能
 */
object SortUtil {

    /**
     * 拼音转换缓存 - 避免重复转换相同文件名
     * 适用于几百到上千个文件的排序场景
     */
    private val pinyinCache = ConcurrentHashMap<String, String>(256)

    /**
     * 将输入字符串转换为可排序的拼音形式
     * - 中文 -> 拼音 (如 "张三" -> "zhangsan")
     * - 英文 -> 小写 (如 "Apple" -> "apple")
     * - 混合 -> 保留原样
     *
     * 性能: 首次转换会计算，后续从缓存读取
     *
     * @param input 要转换的字符串
     * @return 可排序的拼音字符串
     */
    @JvmStatic
    fun toSortablePinyin(input: String): String {
        if (input.isBlank()) return input.lowercase()

        // 缓存命中检查
        pinyinCache[input]?.let { return it }

        val result = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transliterator = Transliterator.getInstance("Han-Latin; Latin-Ascii")
                transliterator.transliterate(input).lowercase()
            } else {
                input.lowercase() // API < 29 回退
            }
        } catch (e: Exception) {
            input.lowercase()
        }

        // 存入缓存 (限制缓存大小防止内存溢出)
        if (pinyinCache.size < 10000) {
            pinyinCache[input] = result
        }

        return result
    }

    /**
     * 清除缓存 (可选 - 如需要手动清理时调用)
     */
    @JvmStatic
    fun clearPinyinCache() {
        pinyinCache.clear()
    }
}
