package com.streamflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme { DARK, AMOLED, LIGHT, SYSTEM, MIDNIGHT, CINEMA, GRAPHITE, CONTRAST, PAPER, NORDIC }

/**
 * Every theme, in the order they are offered to the user, with their labels.
 *
 * Single source of truth on purpose. The picker in Settings, the summary line on
 * the Settings row and the onboarding chooser each used to carry their own
 * hand-written copy of this list, so adding a theme meant editing three
 * unrelated `when` blocks and the app would happily ship having missed one —
 * showing the new theme in the picker but labelling it "Dark" on the row above.
 */
val appThemeOptions: List<Pair<String, String>> = listOf(
    "SYSTEM"   to "Follow system",
    "DARK"     to "Dark",
    "AMOLED"   to "AMOLED Black",
    "LIGHT"    to "Light",
    "PAPER"    to "Warm Paper",
    "NORDIC"   to "Nordic Frost",
    "MIDNIGHT" to "Midnight Blue",
    "CINEMA"   to "Cinema Purple",
    "GRAPHITE" to "Minimal Graphite",
    "CONTRAST" to "High Contrast",
)

/** Display name for a stored theme code; falls back to Dark for unknown values. */
fun themeLabelFor(code: String): String =
    appThemeOptions.firstOrNull { it.first == code }?.second ?: "Dark"

// Bundled brand typeface (Manrope, variable weight). Consistent, geometric,
// and much more "designed" than the system default on every device.
val ManropeFamily = FontFamily(
    androidx.compose.ui.text.font.Font(com.streamflow.R.font.manrope, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(com.streamflow.R.font.manrope, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(com.streamflow.R.font.manrope, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.streamflow.R.font.manrope, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(com.streamflow.R.font.manrope, FontWeight.ExtraBold)
)

fun String.toAppTheme(): AppTheme = when (this) {
    "AMOLED"   -> AppTheme.AMOLED
    "LIGHT"    -> AppTheme.LIGHT
    "PAPER"    -> AppTheme.PAPER
    "NORDIC"   -> AppTheme.NORDIC
    "SYSTEM"   -> AppTheme.SYSTEM
    "MIDNIGHT" -> AppTheme.MIDNIGHT
    "CINEMA"   -> AppTheme.CINEMA
    "GRAPHITE" -> AppTheme.GRAPHITE
    "CONTRAST" -> AppTheme.CONTRAST
    else       -> AppTheme.DARK
}

/**
 * Whether a theme paints dark surfaces — drives the status-bar icon colour.
 *
 * Kept next to the enum rather than inline in MainActivity so a new theme cannot
 * be added without this being the obvious place to declare which way it goes.
 */
fun AppTheme.isDarkSurface(systemInDark: Boolean): Boolean = when (this) {
    // Every light-surface theme must be listed here. Missing one does not fail
    // to compile -- it falls into `else` and paints white status-bar icons onto
    // a white background, leaving the clock and battery invisible.
    AppTheme.LIGHT, AppTheme.PAPER, AppTheme.NORDIC -> false
    AppTheme.SYSTEM -> systemInDark
    else            -> true
}

// Typography scaled by the user's font-size preference, in their chosen face.
// Headings carry slight negative tracking (tighter letter-spacing) — the editorial
// look that reads as "designed" rather than default — while body text keeps neutral
// tracking and small labels keep positive tracking for legibility at size.
private fun appTypography(s: Float, f: FontFamily?) = Typography(
    displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold,     fontSize = 28.sp * s, lineHeight = 34.sp * s, letterSpacing = (-0.5).sp),
    headlineMedium= TextStyle(fontFamily = f, fontWeight = FontWeight.Bold,    fontSize = 22.sp * s, lineHeight = 28.sp * s, letterSpacing = (-0.4).sp),
    titleLarge   = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold,     fontSize = 20.sp * s, lineHeight = 26.sp * s, letterSpacing = (-0.3).sp),
    titleMedium  = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp * s, lineHeight = 22.sp * s, letterSpacing = (-0.2).sp),
    titleSmall   = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * s, lineHeight = 20.sp * s, letterSpacing = (-0.1).sp),
    bodyLarge    = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal,   fontSize = 15.sp * s, lineHeight = 22.sp * s),
    bodyMedium   = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal,   fontSize = 13.sp * s, lineHeight = 19.sp * s),
    bodySmall    = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal,   fontSize = 12.sp * s, lineHeight = 16.sp * s),
    labelLarge   = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 13.sp * s, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium,   fontSize = 11.sp * s, letterSpacing = 0.3.sp),
    labelSmall   = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium,   fontSize = 10.sp * s, letterSpacing = 0.5.sp),
)

private data class AccentPalette(
    val darkPrimary: Color, val darkSecondary: Color,
    val darkContainer: Color, val darkOnContainer: Color,
    val lightPrimary: Color, val lightContainer: Color, val lightOnContainer: Color
)

private val accentPalettes: Map<String, AccentPalette> = mapOf(
    "RED"    to AccentPalette(Color(0xFFFF3B3B), Color(0xFFB51818), Color(0xFF2A0808), Color(0xFFFFAAAA), Color(0xFFCC0F0F), Color(0xFFFFE8E8), Color(0xFF7A0000)),
    "BLUE"   to AccentPalette(Color(0xFF448AFF), Color(0xFF1A4BAA), Color(0xFF0A1A3A), Color(0xFF90C0FF), Color(0xFF1565C0), Color(0xFFE3F2FD), Color(0xFF0A2960)),
    "GREEN"  to AccentPalette(Color(0xFF00C853), Color(0xFF00832E), Color(0xFF092210), Color(0xFF90EE90), Color(0xFF2E7D32), Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    "PURPLE" to AccentPalette(Color(0xFFA855F7), Color(0xFF6B21A8), Color(0xFF1E0A3A), Color(0xFFD4A0FF), Color(0xFF6B21A8), Color(0xFFF3E8FF), Color(0xFF3B1060)),
    "ORANGE" to AccentPalette(Color(0xFFFF7722), Color(0xFFCC4400), Color(0xFF2A1000), Color(0xFFFFB870), Color(0xFFE65100), Color(0xFFFFF3E0), Color(0xFF7A2A00)),
    "PINK"   to AccentPalette(Color(0xFFF472B6), Color(0xFFBE185D), Color(0xFF2A0818), Color(0xFFFFC0E0), Color(0xFFC2185B), Color(0xFFFCE4EC), Color(0xFF7A0040)),
    "TEAL"   to AccentPalette(Color(0xFF2DD4BF), Color(0xFF0D9488), Color(0xFF082520), Color(0xFF80ECD8), Color(0xFF00695C), Color(0xFFE0F2F1), Color(0xFF003B35)),
    "YELLOW" to AccentPalette(Color(0xFFFACC15), Color(0xFFB45309), Color(0xFF1A1500), Color(0xFFFFF0A0), Color(0xFFF59E0B), Color(0xFFFFFDE7), Color(0xFF7A4500)),
    "INDIGO" to AccentPalette(Color(0xFF818CF8), Color(0xFF4338CA), Color(0xFF121038), Color(0xFFC0C4FF), Color(0xFF4F46E5), Color(0xFFEEF2FF), Color(0xFF1E1B6E)),
    "CYAN"   to AccentPalette(Color(0xFF22D3EE), Color(0xFF0E7490), Color(0xFF06222A), Color(0xFF9AEBF8), Color(0xFF0891B2), Color(0xFFECFEFF), Color(0xFF064E5E)),
)

// Derives a full palette from a single user-picked color (custom accent)
private fun blend(a: Color, b: Color, t: Float) = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t
)

private fun paletteFromColor(c: Color) = AccentPalette(
    darkPrimary      = c,
    darkSecondary    = blend(c, Color.Black, 0.35f),
    darkContainer    = blend(c, Color.Black, 0.82f),
    darkOnContainer  = blend(c, Color.White, 0.55f),
    lightPrimary     = blend(c, Color.Black, 0.18f),
    lightContainer   = blend(c, Color.White, 0.86f),
    lightOnContainer = blend(c, Color.Black, 0.55f)
)

/**
 * The neutral half of a theme: everything that is NOT the accent.
 *
 * Separating this from [AccentPalette] is what makes "10 accents x 7 themes"
 * work as 17 declarations instead of 70 hand-written colour schemes — and it
 * means a new theme cannot accidentally ship with, say, no outlineVariant,
 * because the type requires every slot.
 */
private data class SurfacePalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val subtext: Color,
    val outline: Color,
    val outlineVariant: Color,
    val light: Boolean = false
)

private val surfacePalettes: Map<AppTheme, SurfacePalette> = mapOf(
    AppTheme.DARK to SurfacePalette(
        BackgroundDark, SurfaceDark, SurfaceVariantDark,
        OnSurfaceDark, SubtextDark, Color(0xFF2B2C38), Color(0xFF1C1D26)
    ),
    AppTheme.AMOLED to SurfacePalette(
        BackgroundAmoled, SurfaceAmoled, Color(0xFF16161E),
        Color.White, Color(0xFF7C7E8E), Color(0xFF1C1D24), Color(0xFF121218)
    ),
    AppTheme.LIGHT to SurfacePalette(
        BackgroundLight, SurfaceLight, SurfaceVariantLight,
        OnSurfaceLight, SubtextLight, Color(0xFFDDDFE8), Color(0xFFEBECF2),
        light = true
    ),
    AppTheme.PAPER to SurfacePalette(
        BackgroundPaper, SurfacePaper, SurfaceVariantPaper,
        OnSurfacePaper, SubtextPaper, OutlinePaper, OutlineVariantPaper,
        light = true
    ),
    AppTheme.NORDIC to SurfacePalette(
        BackgroundNordic, SurfaceNordic, SurfaceVariantNordic,
        OnSurfaceNordic, SubtextNordic, OutlineNordic, OutlineVariantNordic,
        light = true
    ),
    AppTheme.MIDNIGHT to SurfacePalette(
        BackgroundMidnight, SurfaceMidnight, SurfaceVariantMidnight,
        OnSurfaceMidnight, SubtextMidnight, OutlineMidnight, OutlineVariantMidnight
    ),
    AppTheme.CINEMA to SurfacePalette(
        BackgroundCinema, SurfaceCinema, SurfaceVariantCinema,
        OnSurfaceCinema, SubtextCinema, OutlineCinema, OutlineVariantCinema
    ),
    AppTheme.GRAPHITE to SurfacePalette(
        BackgroundGraphite, SurfaceGraphite, SurfaceVariantGraphite,
        OnSurfaceGraphite, SubtextGraphite, OutlineGraphite, OutlineVariantGraphite
    ),
    AppTheme.CONTRAST to SurfacePalette(
        BackgroundContrast, SurfaceContrast, SurfaceVariantContrast,
        OnSurfaceContrast, SubtextContrast, OutlineContrast, OutlineVariantContrast
    ),
)

private fun buildColors(s: SurfacePalette, p: AccentPalette): ColorScheme =
    if (s.light) lightColorScheme(
        primary            = p.lightPrimary,
        onPrimary          = Color.White,
        primaryContainer   = p.lightContainer,
        onPrimaryContainer = p.lightOnContainer,
        secondary          = p.lightPrimary,
        background         = s.background,
        surface            = s.surface,
        surfaceVariant     = s.surfaceVariant,
        onBackground       = s.onSurface,
        onSurface          = s.onSurface,
        onSurfaceVariant   = s.subtext,
        outline            = s.outline,
        outlineVariant     = s.outlineVariant,
    ) else darkColorScheme(
        primary            = p.darkPrimary,
        onPrimary          = Color.White,
        primaryContainer   = p.darkContainer,
        onPrimaryContainer = p.darkOnContainer,
        secondary          = p.darkSecondary,
        background         = s.background,
        surface            = s.surface,
        surfaceVariant     = s.surfaceVariant,
        onBackground       = s.onSurface,
        onSurface          = s.onSurface,
        onSurfaceVariant   = s.subtext,
        outline            = s.outline,
        outlineVariant     = s.outlineVariant,
    )

@Composable
fun StreamFlowTheme(
    theme: AppTheme = AppTheme.DARK,
    accent: String = "RED",
    fontScale: Float = 1f,
    fontFamilyPref: String = "DEFAULT",
    /**
     * When "TERMINAL", the phosphor design system replaces the palette, type and
     * shapes wholesale. It intentionally ignores theme/accent/font-family: a
     * light-mode or pink-accent terminal is not a terminal, and the whole point
     * of the aesthetic is that there is exactly one look.
     */
    designStyle: String = "MODERN",
    content: @Composable () -> Unit
) {
    if (designStyle == "TERMINAL") {
        MaterialTheme(
            colorScheme = terminalColorScheme(),
            typography  = terminalTypography(fontScale),
            shapes      = TerminalShapes,
            content     = content
        )
        return
    }
    val fontFamily = when (fontFamilyPref) {
        "SERIF" -> FontFamily.Serif
        "MONO"  -> FontFamily.Monospace
        else    -> ManropeFamily // bundled brand typeface
    }
    val isDark = theme.isDarkSurface(isSystemInDarkTheme())
    val context = LocalContext.current
    val palette = when {
        accent.startsWith("CUSTOM:") ->
            accent.removePrefix("CUSTOM:").toLongOrNull(16)
                ?.let { paletteFromColor(Color((it or 0xFF000000L).toInt())) }
                ?: accentPalettes["RED"]!!
        else -> accentPalettes[accent] ?: accentPalettes["RED"]!!
    }
    val colors  = when {
        accent == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // SYSTEM has no palette of its own — it resolves to the standard Dark or
        // Light set. Every other theme names its own surface palette directly.
        theme == AppTheme.SYSTEM ->
            buildColors(surfacePalettes[if (isDark) AppTheme.DARK else AppTheme.LIGHT]!!, palette)
        else -> buildColors(surfacePalettes[theme] ?: surfacePalettes[AppTheme.DARK]!!, palette)
    }
    MaterialTheme(
        colorScheme = colors,
        typography  = appTypography(fontScale, fontFamily),
        content     = content
    )
}
