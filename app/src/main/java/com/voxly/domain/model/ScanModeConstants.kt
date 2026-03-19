package com.voxly.domain.model

/**
 * Scan mode constants for replay gain scanning.
 * Compatible with foobar2000 scan modes.
 */
object ScanModeConstants {
    const val TRACK_ONLY = "TRACK_ONLY"
    const val SINGLE_ALBUM = "SINGLE_ALBUM"
    const val ALBUMS = "ALBUMS"

    val VALID_MODES = setOf(TRACK_ONLY, SINGLE_ALBUM, ALBUMS)
}