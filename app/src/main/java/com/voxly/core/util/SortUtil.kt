package com.voxly.core.util

import android.os.Build
import java.text.Collator
import java.util.Locale

/**
 * Utility for sorting strings with Chinese pinyin support.
 *
 * Provides methods to convert Chinese characters to sortable pinyin strings,
 * enabling proper alphabetical sorting of mixed Chinese-English content.
 */
object SortUtil {

    private val chineseCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }

    /**
     * Converts a string to a sortable pinyin representation.
     *
     * - English letters: kept as-is (lowercased)
     * - Chinese characters: converted to pinyin using ICU Transliterator (API 29+)
     * - Other characters: removed
     *
     * @param input The input string to convert
     * @return A string suitable for sorting (lowercase, pinyin for Chinese)
     */
    @JvmStatic
    fun toSortablePinyin(input: String): String {
        if (input.isBlank()) return ""

        val sb = StringBuilder()

        for (char in input) {
            when {
                char.isLetter() -> {
                    // English letter - keep as lowercase
                    sb.append(char.lowercaseChar())
                }
                char.code in 0x4E00..0x9FFF -> {
                    // Chinese character - convert to pinyin
                    sb.append(getPinyin(char))
                }
                // Skip other characters (numbers, symbols, etc.)
            }
        }

        return sb.toString()
    }

    /**
     * Gets the pinyin representation of a Chinese character.
     * Uses native ICU Transliterator for API 29+, falls back to Collator for older APIs.
     */
    private fun getPinyin(char: Char): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin")
                transliterator.transliterate(char.toString()).lowercase()
            } else {
                fallbackPinyin(char)
            }
        } catch (e: Exception) {
            fallbackPinyin(char)
        }
    }

    /**
     * Fallback pinyin lookup using Collator for API < 29.
     */
    private fun fallbackPinyin(char: Char): String {
        val charString = char.toString()

        // Reference characters sorted by pinyin order
        val pinyinReference = listOf(
            'a' to "啊", 'b' to "八", 'c' to "嚓", 'd' to "大",
            'e' to "额", 'f' to "发", 'g' to "嘎", 'h' to "哈",
            'j' to "鸡", 'k' to "咖", 'l' to "拉", 'm' to "妈",
            'n' to "那", 'o' to "哦", 'p' to "七", 'q' to "七",
            'r' to "日", 's' to "思", 't' to "特", 'w' to "乌",
            'x' to "西", 'y' to "呀", 'z' to "资"
        )

        for ((initial, reference) in pinyinReference) {
            if (chineseCollator.compare(charString, reference) <= 0) {
                return initial.toString()
            }
        }

        // Default to 'z' if not found
        return "z"
    }

    /**
     * Compares two strings using Chinese pinyin sorting.
     * Returns negative if str1 < str2, positive if str1 > str2, 0 if equal.
     */
    @JvmStatic
    fun compareByPinyin(str1: String, str2: String): Int {
        return toSortablePinyin(str1).compareTo(toSortablePinyin(str2))
    }
}
