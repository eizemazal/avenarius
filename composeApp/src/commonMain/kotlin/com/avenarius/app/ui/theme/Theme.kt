package com.avenarius.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

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

/** Accent tones for one mode: the M3 primary pair and its container pair. */
data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

/** User-selectable accent color. SYSTEM = Material You (wallpaper) where available, else blue. */
enum class Accent(
    val label: String,
    val light: AccentTones,
    val dark: AccentTones,
) {
    BLUE(
        "Синий",
        AccentTones(Color(0xFF1F74C8), Color.White, Color(0xFFD6E8FF), Color(0xFF00264A)),
        AccentTones(Color(0xFF7CC0FF), Color(0xFF00315A), Color(0xFF23507E), Color(0xFFD6E8FF)),
    ),
    TEAL(
        "Бирюзовый",
        AccentTones(Color(0xFF006874), Color.White, Color(0xFF9EEFFD), Color(0xFF001F24)),
        AccentTones(Color(0xFF82D3E0), Color(0xFF00363D), Color(0xFF004F58), Color(0xFF9EEFFD)),
    ),
    VIOLET(
        "Фиолетовый",
        AccentTones(Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D)),
        AccentTones(Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)),
    ),
    GREEN(
        "Зелёный",
        AccentTones(Color(0xFF2E7D32), Color.White, Color(0xFFC8F0C8), Color(0xFF002106)),
        AccentTones(Color(0xFF8FD88A), Color(0xFF003909), Color(0xFF1B5E20), Color(0xFFC8F0C8)),
    ),
    ORANGE(
        "Оранжевый",
        AccentTones(Color(0xFFB3541E), Color.White, Color(0xFFFFDBCA), Color(0xFF3A0B00)),
        AccentTones(Color(0xFFFFB68F), Color(0xFF571E00), Color(0xFF7A3410), Color(0xFFFFDBCA)),
    ),
    PINK(
        "Розовый",
        AccentTones(Color(0xFFB0306A), Color.White, Color(0xFFFFD9E2), Color(0xFF3E001D)),
        AccentTones(Color(0xFFFFB1C8), Color(0xFF5E1133), Color(0xFF7B2949), Color(0xFFFFD9E2)),
    ),
    SYSTEM("Системный", BLUE.light, BLUE.dark),
}

/** Parses a stored accent name; unknown or missing values fall back to [Accent.BLUE]. */
fun accentOf(value: String?): Accent = Accent.entries.firstOrNull { it.name == value } ?: Accent.BLUE

/**
 * Material You colors from the system wallpaper (Android 12+), or null where the
 * platform has none.
 */
@Composable
internal expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

/** The app's scheme: shared neutrals with the chosen [accent] swapped in (or Material You). */
@Composable
internal fun appColorScheme(
    dark: Boolean,
    accent: Accent,
): ColorScheme {
    if (accent == Accent.SYSTEM) dynamicColorScheme(dark)?.let { return it }
    val t = if (dark) accent.dark else accent.light
    return (if (dark) AvenariusColorsDark else AvenariusColorsLight).copy(
        primary = t.primary,
        onPrimary = t.onPrimary,
        primaryContainer = t.container,
        onPrimaryContainer = t.onContainer,
        secondaryContainer = t.container,
        onSecondaryContainer = t.onContainer,
        inversePrimary = if (dark) accent.light.primary else accent.dark.primary,
    )
}

/** Applies the appearance settings: color scheme plus whole-UI and text-only scaling. */
@Composable
internal fun AvenariusTheme(
    dark: Boolean,
    accent: Accent,
    uiScale: Float,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * uiScale, base.fontScale * fontScale)) {
        MaterialTheme(colorScheme = appColorScheme(dark, accent), content = content)
    }
}

/** True when the active scheme is light — for the few colors M3 has no role for (chat wallpaper, bubbles). */
@Composable
@ReadOnlyComposable
internal fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

// Messenger-style palette: clear blue accent, blue-tinted "night" neutrals in dark mode,
// violet tertiary from the logo.
internal val AvenariusColorsDark =
    darkColorScheme(
        primary = Color(0xFF7CC0FF),
        onPrimary = Color(0xFF00315A),
        primaryContainer = Color(0xFF23507E),
        onPrimaryContainer = Color(0xFFD6E8FF),
        inversePrimary = Color(0xFF1F74C8),
        secondary = Color(0xFFB2CBEA),
        onSecondary = Color(0xFF1B3450),
        secondaryContainer = Color(0xFF234264),
        onSecondaryContainer = Color(0xFFD6E8FF),
        tertiary = Color(0xFFD0BCFF),
        onTertiary = Color(0xFF381E72),
        tertiaryContainer = Color(0xFF4F378A),
        onTertiaryContainer = Color(0xFFEADDFF),
        background = Color(0xFF0F151C),
        onBackground = Color(0xFFE3E8EE),
        surface = Color(0xFF0F151C),
        onSurface = Color(0xFFE3E8EE),
        surfaceVariant = Color(0xFF26313D),
        onSurfaceVariant = Color(0xFFA9B6C4),
        inverseSurface = Color(0xFFE3E8EE),
        inverseOnSurface = Color(0xFF2A3038),
        outline = Color(0xFF7A8796),
        outlineVariant = Color(0xFF2C3744),
        surfaceDim = Color(0xFF0F151C),
        surfaceBright = Color(0xFF2C3744),
        surfaceContainerLowest = Color(0xFF0A0F14),
        surfaceContainerLow = Color(0xFF141B23),
        surfaceContainer = Color(0xFF18202A),
        surfaceContainerHigh = Color(0xFF1E2833),
        surfaceContainerHighest = Color(0xFF26313D),
    )

internal val AvenariusColorsLight =
    lightColorScheme(
        primary = Color(0xFF1F74C8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E8FF),
        onPrimaryContainer = Color(0xFF00264A),
        inversePrimary = Color(0xFF9FCAFF),
        secondary = Color(0xFF4A6585),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E8FF),
        onSecondaryContainer = Color(0xFF00264A),
        tertiary = Color(0xFF6B4FB8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEADDFF),
        onTertiaryContainer = Color(0xFF23005C),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF151A20),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF151A20),
        surfaceVariant = Color(0xFFE3E8EE),
        onSurfaceVariant = Color(0xFF5B6672),
        inverseSurface = Color(0xFF2A3038),
        inverseOnSurface = Color(0xFFEEF2F7),
        outline = Color(0xFF7A8591),
        outlineVariant = Color(0xFFD5DCE4),
        surfaceDim = Color(0xFFDAE0E7),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F7FA),
        surfaceContainer = Color(0xFFEFF2F6),
        surfaceContainerHigh = Color(0xFFE9EDF2),
        surfaceContainerHighest = Color(0xFFE3E8EE),
    )

// Kept for source compatibility; the dark scheme is the historical default.
internal val AvenariusColors = AvenariusColorsDark
