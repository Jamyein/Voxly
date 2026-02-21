package com.voxly.data.remote.wangy.ne

import com.voxly.domain.model.Lyrics
import com.voxly.domain.model.SyncedLyricLine
import java.util.regex.Pattern

/**
 * Parser for Netease Cloud Music lyrics formats.
 * Supports YRC (word-level synced), LRC (line-level synced),
 * translated lyrics, and romanization lyrics.
 * 
 * Ported from Lyrico's YrcParser implementation.
 */
object YrcParser {

    // YRC line pattern: [mm:ss.xx]content
    private val YRC_LINE_PATTERN: Pattern = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")
    
    // YRC word pattern: (mm:ss.xx,duration,flag)text
    private val YRC_WORD_PATTERN: Pattern = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)")

    // LRC time tag pattern: [mm:ss.xx] or [mm:ss.xxx]
    private val LRC_TIME_TAG_PATTERN: Pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    /**
     * Parses lyrics from all available formats.
     * 
     * @param yrc YRC format lyrics (word-level timing) - may be null
     * @param lrc LRC format lyrics (line-level timing) - may be null
     * @param tlyric Translated lyrics - may be null
     * @param romalrc Romanized lyrics - may be null
     * @return Parsed Lyrics object, or null if no lyrics available
     */
    fun parse(
        yrc: String?,
        lrc: String?,
        tlyric: String?,
        romalrc: String?
    ): Lyrics? {
        if (yrc.isNullOrEmpty() && lrc.isNullOrEmpty()) return null

        // Prefer YRC if available for synced lyrics
        val syncedLines = if (!yrc.isNullOrEmpty()) {
            parseYrc(yrc)
        } else {
            parseLrc(lrc!!)
        }.sortedBy { it.timestampMs }

        // Get plain text from original lyrics
        val plainText = if (!yrc.isNullOrEmpty()) {
            yrcToPlainText(yrc)
        } else {
            lrc!!
        }

        return Lyrics(
            text = plainText,
            isSynced = syncedLines.isNotEmpty(),
            syncedLines = syncedLines
        )
    }

    /**
     * Converts YRC format to plain text.
     */
    private fun yrcToPlainText(yrc: String): String {
        return parseYrc(yrc).joinToString("\n") { it.text }
    }

    /**
     * Parses YRC format lyrics (word-level timing).
     * 
     * YRC format example:
     * [00:12.345, 5000]<00:00.500,00:01.200,0>第一<00:01.700,00:02.500,0>句<00:02.800,00:03.500,0>歌词
     */
    private fun parseYrc(yrc: String): List<SyncedLyricLine> {
        val lines = mutableListOf<SyncedLyricLine>()
        
        yrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            
            val lineMatcher = YRC_LINE_PATTERN.matcher(trimmedLine)
            if (lineMatcher.find()) {
                val lineStart = lineMatcher.group(1)?.toLongOrNull() ?: 0L
                val content = lineMatcher.group(3) ?: ""

                val words = mutableListOf<Pair<Long, String>>()
                val wordMatcher = YRC_WORD_PATTERN.matcher(content)
                
                while (wordMatcher.find()) {
                    val wordStart = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                    val wordText = wordMatcher.group(3) ?: ""
                    if (wordText.isNotBlank()) {
                        words.add(wordStart to wordText)
                    }
                }

                // If no words parsed but content exists, add entire line as one word
                if (words.isEmpty() && content.isNotBlank()) {
                    words.add(lineStart to content)
                }

                if (words.isNotEmpty()) {
                    words.sortBy { it.first }
                    words.forEach { (start, text) ->
                        lines.add(SyncedLyricLine(timestampMs = start, text = text))
                    }
                }
            }
        }
        return lines
    }

    /**
     * Parses LRC format lyrics (line-level timing).
     * 
     * LRC format example:
     * [00:12.34]歌词内容
     * [00:15.67]第二句歌词
     */
    private fun parseLrc(lrc: String): List<SyncedLyricLine> {
        val timedLines = mutableListOf<Pair<Long, String>>()

        lrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            val timeTagMatcher = LRC_TIME_TAG_PATTERN.matcher(trimmedLine)

            val timestamps = mutableListOf<Long>()
            var contentStart = 0

            while (timeTagMatcher.find()) {
                val min = timeTagMatcher.group(1)?.toLongOrNull() ?: 0L
                val sec = timeTagMatcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = (timeTagMatcher.group(3) ?: "0").padEnd(3, '0')
                val ms = msPart.toLongOrNull() ?: 0L
                
                timestamps.add(min * 60000 + sec * 1000 + ms)
                contentStart = timeTagMatcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val content = trimmedLine.substring(contentStart).trim()
                timestamps.forEach { time ->
                    timedLines.add(time to content)
                }
            }
        }

        timedLines.sortBy { it.first }

        return timedLines.map { (startTime, text) ->
            SyncedLyricLine(timestampMs = startTime, text = text)
        }
    }
}
