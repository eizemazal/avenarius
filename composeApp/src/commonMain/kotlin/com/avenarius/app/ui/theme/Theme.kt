package com.avenarius.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

/**
 * Material You colors from the system wallpaper (Android 12+), or null where the
 * platform has none — then the brand schemes below are used.
 */
@Composable
internal expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

// Material 3 tonal schemes seeded from the logo: cyan primary, violet tertiary.
internal val AvenariusColorsDark =
    darkColorScheme(
        primary = Color(0xFF82D3E0),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF9EEFFD),
        inversePrimary = Color(0xFF006874),
        secondary = Color(0xFFB1CBD0),
        onSecondary = Color(0xFF1C3438),
        secondaryContainer = Color(0xFF334B4F),
        onSecondaryContainer = Color(0xFFCDE7EC),
        tertiary = Color(0xFFCFBCFF),
        onTertiary = Color(0xFF381E72),
        tertiaryContainer = Color(0xFF4F378A),
        onTertiaryContainer = Color(0xFFE9DDFF),
        background = Color(0xFF0E1415),
        onBackground = Color(0xFFDEE3E5),
        surface = Color(0xFF0E1415),
        onSurface = Color(0xFFDEE3E5),
        surfaceVariant = Color(0xFF3F484A),
        onSurfaceVariant = Color(0xFFBFC8CA),
        inverseSurface = Color(0xFFDEE3E5),
        inverseOnSurface = Color(0xFF2B3133),
        outline = Color(0xFF899294),
        outlineVariant = Color(0xFF3F484A),
        surfaceDim = Color(0xFF0E1415),
        surfaceBright = Color(0xFF343A3B),
        surfaceContainerLowest = Color(0xFF090F10),
        surfaceContainerLow = Color(0xFF171D1E),
        surfaceContainer = Color(0xFF1B2122),
        surfaceContainerHigh = Color(0xFF252B2C),
        surfaceContainerHighest = Color(0xFF303637),
    )

internal val AvenariusColorsLight =
    lightColorScheme(
        primary = Color(0xFF006874),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9EEFFD),
        onPrimaryContainer = Color(0xFF001F24),
        inversePrimary = Color(0xFF82D3E0),
        secondary = Color(0xFF4A6267),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE7EC),
        onSecondaryContainer = Color(0xFF051F23),
        tertiary = Color(0xFF6750A4),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE9DDFF),
        onTertiaryContainer = Color(0xFF22005D),
        background = Color(0xFFF5FAFB),
        onBackground = Color(0xFF171D1E),
        surface = Color(0xFFF5FAFB),
        onSurface = Color(0xFF171D1E),
        surfaceVariant = Color(0xFFDBE4E6),
        onSurfaceVariant = Color(0xFF3F484A),
        inverseSurface = Color(0xFF2B3133),
        inverseOnSurface = Color(0xFFECF2F3),
        outline = Color(0xFF6F797A),
        outlineVariant = Color(0xFFBFC8CA),
        surfaceDim = Color(0xFFD5DBDC),
        surfaceBright = Color(0xFFF5FAFB),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F6),
        surfaceContainer = Color(0xFFE9EFF0),
        surfaceContainerHigh = Color(0xFFE3E9EA),
        surfaceContainerHighest = Color(0xFFDEE3E5),
    )

// Kept for source compatibility; the dark scheme is the historical default.
internal val AvenariusColors = AvenariusColorsDark
