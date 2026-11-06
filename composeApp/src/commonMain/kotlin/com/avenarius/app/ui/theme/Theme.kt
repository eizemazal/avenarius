package com.avenarius.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
    ;

    /** The neighbouring accent used as tertiary (wallpaper gradient, highlights). */
    val partner: Accent
        get() =
            when (this) {
                BLUE, SYSTEM, PINK -> VIOLET
                TEAL -> BLUE
                VIOLET -> PINK
                GREEN -> TEAL
                ORANGE -> PINK
            }
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
    val partner = if (dark) accent.partner.dark else accent.partner.light
    val base = if (dark) AvenariusColorsDark else AvenariusColorsLight

    // Surfaces lean toward the accent's container, text/lines toward the accent itself.
    fun surf(c: Color) = lerp(c, t.container, if (dark) 0.16f else 0.3f)

    fun ink(c: Color) = lerp(c, t.primary, 0.12f)
    return base.copy(
        primary = t.primary,
        onPrimary = t.onPrimary,
        primaryContainer = t.container,
        onPrimaryContainer = t.onContainer,
        inversePrimary = if (dark) accent.light.primary else accent.dark.primary,
        secondary = lerp(ink(base.onSurfaceVariant), t.primary, 0.3f),
        onSecondary = t.onPrimary,
        secondaryContainer = t.container,
        onSecondaryContainer = t.onContainer,
        tertiary = partner.primary,
        onTertiary = partner.onPrimary,
        tertiaryContainer = partner.container,
        onTertiaryContainer = partner.onContainer,
        background = surf(base.background),
        surface = surf(base.surface),
        surfaceVariant = surf(base.surfaceVariant),
        surfaceDim = surf(base.surfaceDim),
        surfaceBright = surf(base.surfaceBright),
        surfaceContainerLowest = surf(base.surfaceContainerLowest),
        surfaceContainerLow = surf(base.surfaceContainerLow),
        surfaceContainer = surf(base.surfaceContainer),
        surfaceContainerHigh = surf(base.surfaceContainerHigh),
        surfaceContainerHighest = surf(base.surfaceContainerHighest),
        inverseSurface = surf(base.inverseSurface),
        onSurfaceVariant = ink(base.onSurfaceVariant),
        outline = ink(base.outline),
        outlineVariant = surf(base.outlineVariant),
        surfaceTint = t.primary,
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

// Neutral greys; [appColorScheme] tints them toward the chosen accent, so the accent
// colors every surface. The accent roles here are only placeholders (overwritten).
internal val AvenariusColorsDark =
    darkColorScheme(
        background = Color(0xFF0E0F11),
        onBackground = Color(0xFFE4E6E9),
        surface = Color(0xFF0E0F11),
        onSurface = Color(0xFFE4E6E9),
        surfaceVariant = Color(0xFF2A2D31),
        onSurfaceVariant = Color(0xFFAAB0B7),
        inverseSurface = Color(0xFFE4E6E9),
        inverseOnSurface = Color(0xFF2A2D31),
        outline = Color(0xFF7E838A),
        outlineVariant = Color(0xFF30343A),
        surfaceDim = Color(0xFF0E0F11),
        surfaceBright = Color(0xFF2F3236),
        surfaceContainerLowest = Color(0xFF090A0B),
        surfaceContainerLow = Color(0xFF151719),
        surfaceContainer = Color(0xFF191B1E),
        surfaceContainerHigh = Color(0xFF212327),
        surfaceContainerHighest = Color(0xFF2A2D31),
    )

internal val AvenariusColorsLight =
    lightColorScheme(
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF16181B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF16181B),
        surfaceVariant = Color(0xFFE4E6E9),
        onSurfaceVariant = Color(0xFF5D636B),
        inverseSurface = Color(0xFF2B2E32),
        inverseOnSurface = Color(0xFFEFF1F3),
        outline = Color(0xFF7E838A),
        outlineVariant = Color(0xFFD7DADE),
        surfaceDim = Color(0xFFDADCDF),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F7F8),
        surfaceContainer = Color(0xFFF0F1F3),
        surfaceContainerHigh = Color(0xFFEAEBEE),
        surfaceContainerHighest = Color(0xFFE4E6E9),
    )

/** Glyphs drawn over photos/video and their dark [ColorScheme.scrim]: light in every theme. */
val ColorScheme.onScrim: Color get() = Color.White

/** This color with its hue turned by [degrees]; saturation and brightness are kept. */
fun Color.rotateHue(degrees: Float): Color {
    val max = maxOf(red, green, blue)
    val d = max - minOf(red, green, blue)
    val hue =
        when {
            d == 0f -> 0f
            max == red -> 60f * (((green - blue) / d) % 6f)
            max == green -> 60f * ((blue - red) / d + 2f)
            else -> 60f * ((red - green) / d + 4f)
        }
    val turned = ((hue + degrees) % 360f + 360f) % 360f
    return Color.hsv(turned, if (max == 0f) 0f else d / max, max, alpha)
}

// Kept for source compatibility; the dark scheme is the historical default.
internal val AvenariusColors = AvenariusColorsDark
