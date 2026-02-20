package com.voxly.data.remote.wangy.util

import com.voxly.domain.model.SyncedLyricLine

/**
 * Parser for NetEase Cloud Music YRC format lyrics.
 * YRC = Word-level Rich Content, contains word-level timing.
 */
object WangyLyricsParser {

    /**
     * Parses YRC format lyrics to list of synced lyric lines.
     * 
     * @param yrcContent YRC format lyrics content
     * @return List of SyncedLyricLine with word-level timing
     */
    fun parseYrc(yrcContent: String): List<SyncedLyricLine> {
        if (yrcContent.isBlank()) return emptyList()
        
        val result = mutableListOf<SyncedLyricLine>()
        
        // Split by lines
        val lines = yrcContent.split("\n")
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            // Extract line time [mm:ss.xx]
            val lineTimeMatch = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""").find(line)
            if (lineTimeMatch == null) continue
            
            val minutes = lineTimeMatch.groupValues[1].toInt()
            val seconds = lineTimeMatch.groupValues[2].toInt()
            val millis = lineTimeMatch.groupValues[3].toInt().let { if (it < 100) it * 10 else it }
            val lineTimeMs = (minutes * 60 + seconds) * 1000L + millis
            
            val content = lineTimeMatch.groupValues[4]
            
            // Parse word-level timing <mm:ss.xx,mm:ss.xx>文字
            val wordRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3}),(\d{2}):(\d{2})\.(\d{2,3})>([^<]*)""")
            val words = wordRegex.findAll(content).toList()
            
            if (words.isEmpty()) {
                // No word timing, use line timing
                result.add(SyncedLyricLine(lineTimeMs, content.trim()))
            } else {
                // Create lines for each word with its timing
                for (word in words) {
                    val wMin = word.groupValues[1].toInt()
                    val wSec = word.groupValues[2].toInt()
                    val wMs = word.groupValues[3].toInt().let { if (it < 100) it * 10 else it }
                    val wordStartMs = lineTimeMs + (wMin * 60 + wSec) * 1000L + wMs
                    val wordText = word.groupValues[7]
                    
                    if (wordText.isNotBlank()) {
                        result.add(SyncedLyricLine(wordStartMs, wordText))
                    }
                }
            }
        }
        
        return result.sortedBy { it.timestampMs }
    }

    /**
     * Converts YRC format to standard LRC format.
     * 
     * @param yrcContent YRC format lyrics
     * @return LRC format lyrics
     */
    fun yrcToLrc(yrcContent: String): String {
        val lines = yrcContent.split("\n")
        val lrcLines = mutableListOf<String>()
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            // Extract line time
            val lineTimeMatch = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""").find(line)
            if (lineTimeMatch == null) {
                if (line.isNotBlank()) lrcLines.add(line)
                continue
            }
            
            val minutes = lineTimeMatch.groupValues[1].toInt()
            val seconds = lineTimeMatch.groupValues[2].toInt()
            val millis = lineTimeMatch.groupValues[3].toInt().let { if (it < 100) it * 10 else it }
            val timeLabel = String.format("[%02d:%02d.%02d]", minutes, seconds, millis / 10)
            
            // Extract text without word timing tags
            val content = lineTimeMatch.groupValues[4]
            val cleanText = content.replace(Regex("""<\d{2}:\d{2}\.\d{2,3},\d{2}:\d{2}\.\d{2,3}>"""), "")
            
            lrcLines.add("$timeLabel$cleanText")
        }
        
        return lrcLines.joinToString("\n")
    }
}
