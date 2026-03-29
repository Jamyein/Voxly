package com.voxly.presentation.components.scrollbar

/**
 * Get the first letter for indexing purposes.
 * Returns uppercase letter for alphabetic characters (supports Chinese pinyin),
 * '0' for digits, '#' for symbols and empty strings.
 *
 * @param text The text to extract the first letter from
 * @return The first letter for indexing ('A'-'Z', '0', or '#')
 */
fun getFirstLetter(text: String): Char {
    if (text.isBlank()) return '#'

    val firstChar = text.trim().first()

    return when {
        firstChar.isLetter() && firstChar.code < 128 -> firstChar.uppercaseChar()
        firstChar.code > 127 -> getPinyinInitial(firstChar)
        firstChar.isDigit() -> '0'
        else -> '#'
    }
}

/**
 * Get pinyin initial for a Chinese character using ICU Transliterator.
 * Falls back to '#' if conversion fails.
 */
private fun getPinyinInitial(char: Char): Char {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin")
            val pinyin = transliterator.transliterate(char.toString())
            pinyin.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
        } else {
            if (char.isLetter()) char.uppercaseChar() else '#'
        }
    } catch (e: Exception) {
        if (char.isLetter()) char.uppercaseChar() else '#'
    }
}
