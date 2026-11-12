package com.avenarius.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avenarius.app.resources.Res
import com.avenarius.app.resources.onest_bold
import com.avenarius.app.resources.onest_medium
import com.avenarius.app.resources.onest_regular
import com.avenarius.app.resources.onest_semibold
import org.jetbrains.compose.resources.Font

/** Set while a screen draws a dark photo under the status bar: the platform then shows light status-bar icons. */
internal val lightStatusBarIcons = mutableStateOf(false)

/** User-selectable theme; SYSTEM follows the OS setting. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Whether this mode renders dark, given the OS setting (shared by the theme and system bars). */
@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

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
        AccentTones(Color(0xFF2481CC), Color.White, Color(0xFFD6E8FF), Color(0xFF00264A)),
        AccentTones(Color(0xFF3E9BE9), Color.White, Color(0xFF1F3B57), Color(0xFFD6E8FF)),
    ),
    TEAL(
        "Бирюзовый",
        AccentTones(Color(0xFF00838F), Color.White, Color(0xFFC4EEF2), Color(0xFF001F24)),
        AccentTones(Color(0xFF2BA8B8), Color.White, Color(0xFF163F45), Color(0xFFC4EEF2)),
    ),
    VIOLET(
        "Фиолетовый",
        AccentTones(Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D)),
        AccentTones(Color(0xFF9575E6), Color.White, Color(0xFF3A2D63), Color(0xFFEADDFF)),
    ),
    GREEN(
        "Зелёный",
        AccentTones(Color(0xFF2E7D32), Color.White, Color(0xFFC8F0C8), Color(0xFF002106)),
        AccentTones(Color(0xFF4CAF50), Color.White, Color(0xFF1F4423), Color(0xFFC8F0C8)),
    ),
    ORANGE(
        "Оранжевый",
        AccentTones(Color(0xFFB3541E), Color.White, Color(0xFFFFDBCA), Color(0xFF3A0B00)),
        AccentTones(Color(0xFFE8833A), Color.White, Color(0xFF573016), Color(0xFFFFDBCA)),
    ),
    PINK(
        "Розовый",
        AccentTones(Color(0xFFB0306A), Color.White, Color(0xFFFFD9E2), Color(0xFF3E001D)),
        AccentTones(Color(0xFFE5679A), Color.White, Color(0xFF5A2440), Color(0xFFFFD9E2)),
    ),
    SYSTEM("Системный", BLUE.light, BLUE.dark),
    ;

    /** The neighbouring accent used as tertiary (secondary highlights). */
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

/** The app's scheme: neutral greys with the chosen [accent] swapped in (or Material You). */
@Composable
internal fun appColorScheme(
    dark: Boolean,
    accent: Accent,
): ColorScheme {
    if (accent == Accent.SYSTEM) dynamicColorScheme(dark)?.let { return it }
    val t = if (dark) accent.dark else accent.light
    val partner = if (dark) accent.partner.dark else accent.partner.light
    val base = if (dark) AvenariusColorsDark else AvenariusColorsLight
    // Only the accent roles change: surfaces and text stay neutral whatever the accent.
    return base.copy(
        primary = t.primary,
        onPrimary = t.onPrimary,
        primaryContainer = t.container,
        onPrimaryContainer = t.onContainer,
        inversePrimary = if (dark) accent.light.primary else accent.dark.primary,
        secondary = lerp(base.onSurfaceVariant, t.primary, 0.3f),
        onSecondary = t.onPrimary,
        secondaryContainer = t.container,
        onSecondaryContainer = t.onContainer,
        tertiary = partner.primary,
        onTertiary = partner.onPrimary,
        tertiaryContainer = partner.container,
        onTertiaryContainer = partner.onContainer,
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
        MaterialTheme(
            colorScheme = appColorScheme(dark, accent),
            typography = appTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The spacing scale: every padding and gap in the UI is one of these. */
internal object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii: [small] for tiles and chips, [medium] for cards, bubbles and inputs; full round is CircleShape. */
internal object Radius {
    val small = 8.dp
    val medium = 16.dp
}

/** One corner family for sheets, cards, dialogs and menus. */
private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(Radius.small),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(Radius.medium),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Onest (SIL OFL, full Cyrillic) in place of the platform face, on the M3 type scale. */
@Composable
private fun appTypography(): Typography {
    val onest =
        FontFamily(
            Font(Res.font.onest_regular, FontWeight.Normal),
            Font(Res.font.onest_medium, FontWeight.Medium),
            Font(Res.font.onest_semibold, FontWeight.SemiBold),
            Font(Res.font.onest_bold, FontWeight.Bold),
        )
    val base = Typography()

    fun TextStyle.onest(weight: FontWeight? = null) = copy(fontFamily = onest, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = base.displayLarge.onest(FontWeight.SemiBold),
        displayMedium = base.displayMedium.onest(FontWeight.SemiBold),
        displaySmall = base.displaySmall.onest(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.onest(FontWeight.SemiBold).copy(fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = base.headlineMedium.onest(FontWeight.SemiBold).copy(fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = base.headlineSmall.onest(FontWeight.SemiBold).copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = base.titleLarge.onest(FontWeight.SemiBold),
        titleMedium = base.titleMedium.onest(FontWeight.Medium).copy(letterSpacing = 0.sp),
        titleSmall = base.titleSmall.onest(FontWeight.Medium),
        bodyLarge = base.bodyLarge.onest(FontWeight.Normal).copy(letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.onest(FontWeight.Normal).copy(letterSpacing = 0.sp),
        bodySmall = base.bodySmall.onest(FontWeight.Normal).copy(letterSpacing = 0.sp),
        labelLarge = base.labelLarge.onest(FontWeight.Medium),
        labelMedium = base.labelMedium.onest(FontWeight.Medium).copy(letterSpacing = 0.sp),
        labelSmall = base.labelSmall.onest(FontWeight.Medium).copy(letterSpacing = 0.sp),
    )
}

/**
 * The accent fill (own bubbles, the compose button, unread badges) as two equal stops: the
 * look is flat; the list form is kept for callers that build their own brush.
 */
@Composable
@ReadOnlyComposable
internal fun accentGradientColors(): List<Color> = MaterialTheme.colorScheme.primary.let { listOf(it, it) }

/** The flat accent fill as a [Brush], for places that take one. */
@Composable
@ReadOnlyComposable
internal fun accentGradient(): Brush = SolidColor(MaterialTheme.colorScheme.primary)

/** Content color on [accentGradient]. */
@Composable
@ReadOnlyComposable
internal fun onAccentGradient(): Color = MaterialTheme.colorScheme.onPrimary

/**
 * The plain backdrop behind conversations and profile headers: just the background color.
 * [strength] is unused and kept so existing callers compile.
 */
@Composable
internal fun Modifier.aurora(
    @Suppress("UNUSED_PARAMETER") strength: Float = 1f,
): Modifier {
    val base = MaterialTheme.colorScheme.background
    return drawBehind { drawRect(base) }
}

/** The chat backgrounds, in the picker's order: a faint accent wash, a plain background, four faint patterns. */
internal val WALLPAPERS = listOf("Аврора", "Без фона", "Волна", "Рассвет", "Сумерки", "Глубина")

/**
 * The chat background [preset] (an index into [WALLPAPERS]); unknown values draw the plain one.
 * Every preset stays a barely tinted background so opaque bubbles always read; the patterned ones
 * carry faint dots or diagonal lines, built once per size and only redrawn.
 */
@Composable
internal fun Modifier.chatWallpaper(preset: Int): Modifier {
    val cs = MaterialTheme.colorScheme
    val base = cs.background
    if (preset !in WALLPAPERS.indices || preset == 1) return drawBehind { drawRect(base) }
    val light = isLightTheme()
    val tint = cs.primary.copy(alpha = if (light) 0.08f else 0.06f)
    if (preset == 0) {
        return drawBehind {
            drawRect(base)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, tint)))
        }
    }
    val ink = (if (light) Color.Black else Color.White).copy(alpha = 0.05f)
    val dots = preset % 2 == 0
    return drawWithCache {
        val w = size.width
        val h = size.height
        val step = 24.dp.toPx()
        val pattern =
            Path().apply {
                if (dots) {
                    var y = step / 2
                    var row = 0
                    while (y < h) {
                        var x = if (row++ % 2 == 0) step / 2 else step
                        while (x < w) {
                            addOval(Rect(Offset(x, y), 1.5.dp.toPx()))
                            x += step
                        }
                        y += step
                    }
                } else {
                    var x = -h
                    while (x < w) {
                        moveTo(x, h)
                        lineTo(x + h, 0f)
                        x += step
                    }
                }
            }
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            drawRect(base)
            drawRect(tint)
            if (dots) drawPath(pattern, ink) else drawPath(pattern, ink, style = stroke)
        }
    }
}

/**
 * Own bubbles: the accent, deepened (or lightened, when its text is dark) just until
 * [ColorScheme.onPrimary] body text reaches 4.5:1, so any accent or Material You scheme stays legible.
 */
@Composable
@ReadOnlyComposable
internal fun outgoingBubbleColor(): Color = MaterialTheme.colorScheme.let { bubbleTone(it.primary, it.onPrimary) }

internal fun bubbleTone(
    accent: Color,
    text: Color,
): Color {
    val toward = if (text.luminance() > 0.5f) Color.Black else Color.White
    var step = 0f
    var c = accent
    while (step < 0.6f && contrast(c, text) < 4.5f) {
        step += 0.05f
        c = lerp(accent, toward, step)
    }
    return c
}

private fun contrast(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** True when the active scheme is light — for the few colors M3 has no role for (chat wallpaper, bubbles). */
@Composable
@ReadOnlyComposable
internal fun isLightTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

// Neutral near-black / graphite greys, never tinted by the accent. The accent roles here are
// only placeholders ([appColorScheme] overwrites them).
internal val AvenariusColorsDark =
    darkColorScheme(
        background = Color(0xFF0F0F0F),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF0F0F0F),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF2A2A2C),
        onSurfaceVariant = Color(0xFF9A9A9E),
        inverseSurface = Color(0xFFEDEDED),
        inverseOnSurface = Color(0xFF1C1C1E),
        outline = Color(0xFF6E6E72),
        outlineVariant = Color(0xFF2C2C2E),
        surfaceDim = Color(0xFF0F0F0F),
        surfaceBright = Color(0xFF333335),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF161618),
        surfaceContainer = Color(0xFF1C1C1E),
        surfaceContainerHigh = Color(0xFF2A2A2C),
        surfaceContainerHighest = Color(0xFF333335),
    )

// Light is grouped: a grey page (and sheet) with white cards on it.
internal val AvenariusColorsLight =
    lightColorScheme(
        background = Color(0xFFF0F0F3),
        onBackground = Color(0xFF141414),
        surface = Color(0xFFF0F0F3),
        onSurface = Color(0xFF141414),
        surfaceVariant = Color(0xFFE6E6E8),
        onSurfaceVariant = Color(0xFF6B6B70),
        inverseSurface = Color(0xFF2A2A2C),
        inverseOnSurface = Color(0xFFF1F1F3),
        outline = Color(0xFF8A8A8E),
        outlineVariant = Color(0xFFDCDCDE),
        surfaceDim = Color(0xFFDADADC),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F0F3),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFEAEAEC),
        surfaceContainerHighest = Color(0xFFE4E4E6),
    )

/** Glyphs drawn over photos/video and their dark [ColorScheme.scrim]: light in every theme. */
val ColorScheme.onScrim: Color get() = Color.White

/** Hue (degrees), saturation and value of this color. */
private fun Color.toHsv(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val d = max - minOf(red, green, blue)
    val hue =
        when {
            d == 0f -> 0f
            max == red -> 60f * (((green - blue) / d) % 6f)
            max == green -> 60f * ((blue - red) / d + 2f)
            else -> 60f * ((red - green) / d + 4f)
        }
    return Triple(hue, if (max == 0f) 0f else d / max, max)
}

/** This color with its hue turned by [degrees]; saturation and brightness are kept. */
fun Color.rotateHue(degrees: Float): Color {
    val (hue, sat, value) = toHsv()
    return Color.hsv(((hue + degrees) % 360f + 360f) % 360f, sat, value, alpha)
}

// Kept for source compatibility; the dark scheme is the historical default.
internal val AvenariusColors = AvenariusColorsDark
