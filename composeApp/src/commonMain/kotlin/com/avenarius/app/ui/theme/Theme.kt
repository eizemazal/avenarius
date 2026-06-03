package com.avenarius.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/** User-selectable theme; SYSTEM follows the OS setting. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Parses a stored preference string ("system"/"dark"/"light") into a [ThemeMode]. */
fun themeModeOf(value: String): ThemeMode =
    when (value) {
        "dark" -> ThemeMode.DARK
        "light" -> ThemeMode.LIGHT
        else -> ThemeMode.SYSTEM
    }

/** The stored-preference string for a [ThemeMode]. */
fun ThemeMode.prefValue(): String =
    when (this) {
        ThemeMode.SYSTEM -> "system"
        ThemeMode.DARK -> "dark"
        ThemeMode.LIGHT -> "light"
    }

internal val AvenariusColorsDark = darkColorScheme()
internal val AvenariusColorsLight = lightColorScheme()

// Kept for source compatibility; the dark scheme is the historical default.
internal val AvenariusColors = AvenariusColorsDark
