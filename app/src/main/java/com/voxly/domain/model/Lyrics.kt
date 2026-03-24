package com.voxly.domain.model

/**
 * Represents song lyrics with optional synchronization data.
 */
data class Lyrics(
    val text: String,
    val isSynced: Boolean = false,
    val syncedLines: List<SyncedLyricLine> = emptyList(),
    val language: String? = null
) {
    /**
     * Returns the lyrics as plain text (without timestamps).
     */
    fun getPlainText(): String {
        return if (isSynced && syncedLines.isNotEmpty()) {
            syncedLines.joinToString("\n") { it.text }
        } else {
            text
        }
    }

    /**
     * Returns the lyrics in LRC format.
     */
    fun toLrcFormat(): String {
        return if (isSynced && syncedLines.isNotEmpty()) {
            syncedLines.joinToString("\n") { line ->
                "${line.formattedTimestamp}${line.text}"
            }
        } else {
            text
        }
    }

    /**
     * Gets the lyric line at a specific time (for synced lyrics).
     * @param timeMs Time in milliseconds
     * @return The current lyric line or null
     */
    fun getLineAtTime(timeMs: Long): SyncedLyricLine? {
        if (!isSynced || syncedLines.isEmpty()) return null

        return syncedLines.filter { it.timestampMs <= timeMs }
            .maxByOrNull { it.timestampMs }
    }

    /**
     * Gets the next lyric line after a specific time.
     * @param timeMs Time in milliseconds
     * @return The next lyric line or null
     */
    fun getNextLineAtTime(timeMs: Long): SyncedLyricLine? {
        if (!isSynced || syncedLines.isEmpty()) return null

        return syncedLines.filter { it.timestampMs > timeMs }
            .minByOrNull { it.timestampMs }
    }

    companion object {
        /**
         * Parses LRC format lyrics.
         * @param lrcText LRC formatted lyrics
         * @return Lyrics object
         */
        fun parseLrc(lrcText: String): Lyrics {
            val lines = lrcText.lines()
            val syncedLines = mutableListOf<SyncedLyricLine>()

            // LRC regex pattern: [mm:ss.xx] or [mm:ss.xxx]
            val timeRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

            lines.forEach { line ->
                val match = timeRegex.find(line)
                if (match != null) {
                    val minutes = match.groupValues[1].toInt()
                    val seconds = match.groupValues[2].toInt()
                    val millis = match.groupValues[3].toInt().let {
                        if (it < 100) it * 10 else it // Handle both xx and xxx formats
                    }
                    val text = match.groupValues[4].trim()

                    val timestampMs = (minutes * 60 + seconds) * 1000L + millis

                    syncedLines.add(
                        SyncedLyricLine(
                            timestampMs = timestampMs,
                            text = text,
                            formattedTimestamp = match.groupValues[0].substringBefore(']') + "]"
                        )
                    )
                }
            }

            return Lyrics(
                text = lrcText,
                isSynced = syncedLines.isNotEmpty(),
                syncedLines = syncedLines.sortedBy { it.timestampMs }
            )
        }

        /**
         * Creates unsynchronized lyrics.
         * @param text Plain text lyrics
         * @return Lyrics object
         */
        fun createUnsynced(text: String): Lyrics {
            return Lyrics(
                text = text,
                isSynced = false,
                syncedLines = emptyList()
            )
        }

        /**
         * Parses lyrics text and returns plain text lines.
         * @param lyricsText Raw lyrics text (LRC format or plain text)
         * @return List of non-blank text lines
         */
        fun parseToLines(lyricsText: String): List<String> {
            if (lyricsText.isBlank()) {
                return emptyList()
            }
            return try {
                val lyrics = parseLrc(lyricsText)
                if (lyrics.isSynced && lyrics.syncedLines.isNotEmpty()) {
                    lyrics.syncedLines.map { it.text }.filter { it.isNotBlank() }
                } else {
                    lyricsText.lines().filter { it.isNotBlank() }
                }
            } catch (e: Exception) {
                lyricsText.lines().filter { it.isNotBlank() }
            }
        }

        /**
         * Formats lyrics timestamps from [mm:ss.xxx] to [mm:ss.xx]
         * Converts 3-digit milliseconds to 2-digit (e.g., [01:23.456] -> [01:23.45])
         * @param lrcText LRC formatted lyrics text
         * @return Formatted lyrics text with 2-digit milliseconds
         */
        fun formatTimestamps(lrcText: String): String {
            // Match [mm:ss.xxx] format (3-digit milliseconds)
            val threeDigitRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{3})\]""")

            return threeDigitRegex.replace(lrcText) { matchResult ->
                val minutes = matchResult.groupValues[1]
                val seconds = matchResult.groupValues[2]
                val millis = matchResult.groupValues[3]
                // Take first 2 digits of milliseconds
                val twoDigitMillis = millis.take(2)
                "[$minutes:$seconds.$twoDigitMillis]"
            }
        }
    }
}

/**
 * Represents a single synchronized lyric line.
 */
data class SyncedLyricLine(
    val timestampMs: Long,
    val text: String,
    val formattedTimestamp: String = formatTimestamp(timestampMs)
) {
    companion object {
        /**
         * Formats timestamp in LRC format [mm:ss.xx].
         */
        fun formatTimestamp(timestampMs: Long): String {
            val totalSeconds = timestampMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val millis = (timestampMs % 1000) / 10

            return String.format("[%02d:%02d.%02d]", minutes, seconds, millis)
        }
    }
}
