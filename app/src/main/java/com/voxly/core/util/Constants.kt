package com.voxly.core.util

/**
 * Application-wide constants for magic numbers and configuration values.
 */
object Constants {

    // ==================== Time & Duration ====================
    
    /** Default minimum duration filter threshold in milliseconds (1 minute) */
    const val MIN_DURATION_FILTER_THRESHOLD_MS = 60_000L
    
    /** Default cover art fetch timeout in milliseconds (5 seconds) */
    const val COVER_ART_TIMEOUT_MS = 5_000L
    
    /** Default debounce time for search in milliseconds */
    const val SEARCH_DEBOUNCE_MS = 500L
    
    /** SAF recreate delay in milliseconds */
    const val SAF_RECREATE_DELAY_MS = 500L

    // ==================== Buffer & Size ====================
    
    /** Default buffer size for file operations (8KB) */
    const val FILE_BUFFER_SIZE = 8192
    
    /** Default online search limit */
    const val DEFAULT_ONLINE_SEARCH_LIMIT = 25
    
    /** Minimum online search limit */
    const val MIN_ONLINE_SEARCH_LIMIT = 5
    
    /** Maximum online search limit */
    const val MAX_ONLINE_SEARCH_LIMIT = 200

    // ==================== ReplayGain ====================
    
    /** Standard ReplayGain reference loudness in LUFS */
    const val REPLAYGAIN_REFERENCE_LOUDNESS_LUFS = -14.0
    
    /** RMS reference value derived from -14 LUFS */
    const val REPLAYGAIN_RMS_REFERENCE = 0.1995262314968879

    // ==================== Network ====================
    
    /** Default API timeout in milliseconds */
    const val DEFAULT_API_TIMEOUT_MS = 30_000L
}
