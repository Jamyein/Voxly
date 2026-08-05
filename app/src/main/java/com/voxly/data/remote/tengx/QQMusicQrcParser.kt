package com.voxly.data.remote.tengx

/**
 * Converts QQ Music QRC (word-level timing lyrics) to plain LRC format.
 *
 * QRC format example:
 *   <Lyric_1 LyricType="1" LyricContent="[0,3000]你(0,300)好(300,500)[4000,2000]世(0,200)界(200,300)"/>
 *
 * Output LRC format:
 *   [00:00.00]你好
 *   [00:04.00]世界
 *
 * Based on Lyrico-Plugins qq/source.js parseQrcFormat.
 */
object QQMusicQrcParser {

    private val QRC_LINE = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    private val QRC_WORD_MARKER = Regex("\\(\\d+,\\d+\\)")
    private val TAG_LINE = Regex("^\\[\\w+:[^\\]]*\\]$")
    private val XML_WRAPPER = Regex("<Lyric_1 LyricType=\"1\" LyricContent=\"([\\s\\S]*?)\"/>")

    /**
     * Parses QRC lyrics text and returns plain LRC text.
     *
     * @return LRC formatted string, or empty string if no valid lines found.
     */
    fun qrcToLrc(qrcText: String): String {
        var content = qrcText.trim()
        if (content.isEmpty()) return ""

        // Extract from XML wrapper if present
        val xmlMatch = XML_WRAPPER.find(content)
        if (xmlMatch != null) {
            content = decodeXmlEntities(xmlMatch.groupValues[1])
        }

        val lines = mutableListOf<String>()
        for (rawLine in content.split(Regex("\\r?\\n"))) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (TAG_LINE.matches(line)) continue // skip [ti:xxx] etc.

            val match = QRC_LINE.find(line) ?: continue
            val startMs = match.groupValues[1].toLongOrNull() ?: continue
            val lineContent = match.groupValues[3]

            // Strip word timing markers to get plain text
            val lineText = lineContent.replace(QRC_WORD_MARKER, "")

            // Some lines have only markers (e.g. roma), produce empty-text line
            // which is valid for merge alignment
            val ts = formatLrcTimestamp(startMs)
            lines.add("$ts$lineText")
        }

        return lines.joinToString("\n")
    }

    /**
     * Parses plain text (LRC or unsynced) and returns it as-is.
     * Used for trans lyrics which are already in LRC format.
     */
    fun plainTextToLrc(text: String): String {
        val clean = text.trim()
        if (clean.isBlank()) return ""
        // If it's already LRC-formatted, return as-is
        if (clean.contains("[") && clean.contains("]")) return clean
        // Unsynced text — return as-is
        return clean
    }

    // --- timestamp formatting ---

    private fun formatLrcTimestamp(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val cs = (ms % 1000) / 10
        return String.format("[%02d:%02d.%02d]", min, sec, cs)
    }

    // --- XML entity decoding (matches Lyrico decodeXmlEntities) ---

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("&#(\\d+);")) { mr ->
                mr.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: mr.value
            }
    }
}
