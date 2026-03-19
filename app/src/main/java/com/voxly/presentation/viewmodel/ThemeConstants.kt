package com.voxly.presentation.viewmodel

/**
 * Theme mode constants for settings.
 * Eliminates magic strings for theme mode values.
 */
object ThemeConstants {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    val VALID_MODES = setOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)
}