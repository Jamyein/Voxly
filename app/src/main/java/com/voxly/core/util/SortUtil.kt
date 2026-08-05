package com.voxly.core.util

import android.icu.text.Transliterator
import android.os.Build
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for sorting strings with Chinese pinyin support.
 *
 * Provides methods to convert Chinese characters to sortable pinyin strings,
 * enabling proper alphabetical sorting of mixed Chinese-English content.
 */
object SortUtil {

    /**
     * 拼音转换缓存 - 避免重复转换相同文件名
     * 适用于几百到上千个文件的排序场景
     */
    private val pinyinCache = ConcurrentHashMap<String, String>(256)

    @JvmStatic
    val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }

    /**
     * ICU transliterator for Han→Latin conversion. Creating one per call is
     * expensive (parses rule tables); hoisting to a singleton makes the 1000+
     * pinyin sorts during initial aggregation cheap.
     */
    private val hanLatinTransliterator: Transliterator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { Transliterator.getInstance("Han-Latin; Latin-Ascii") } catch (e: Exception) { null }
        } else {
            null
        }

    /**
     * Converts a string to a sortable pinyin representation.
     *
     * - English letters: kept as-is (lowercased)
     * - Chinese characters: converted to pinyin using ICU Transliterator (API 29+)
     * - Other characters: removed
     *
     * Performance: 首次转换会计算，后续从缓存读取
     *
     * @param input The input string to convert
     * @return A string suitable for sorting (lowercase, pinyin for Chinese)
     */
    @JvmStatic
    fun toSortablePinyin(input: String): String {
        if (input.isBlank()) return ""

        // 缓存命中检查
        pinyinCache[input]?.let { return it }

        val result = try {
            if (hanLatinTransliterator != null) {
                hanLatinTransliterator.transliterate(input).lowercase()
            } else {
                input.lowercase() // API < 29 或初始化失败回退
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