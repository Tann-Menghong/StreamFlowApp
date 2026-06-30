package com.streamflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme { DARK, AMOLED, LIGHT, SYSTEM }

fun String.toAppTheme(): AppTheme = when (this) {
    "AMOLED" -> AppTheme.AMOLED
    "LIGHT"  -> AppTheme.LIGHT
    "SYSTEM" -> AppTheme.SYSTEM
    else     -> AppTheme.DARK
}

private val AppTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.Bold,    fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge   = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall   = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 10.sp, letterSpacing = 0.5.sp),
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
)

private fun buildDarkColors(p: AccentPalette) = darkColorScheme(
    primary            = p.darkPrimary,
    onPrimary          = Color.White,
    primaryContainer   = p.darkContainer,
    onPrimaryContainer = p.darkOnContainer,
    secondary          = p.darkSecondary,
    background         = BackgroundDark,
    surface            = SurfaceDark,
    surfaceVariant     = SurfaceVariantDark,
    onBackground       = OnSurfaceDark,
    onSurface          = OnSurfaceDark,
    onSurfaceVariant   = SubtextDark,
    outline            = Color(0xFF282838),
    outlineVariant     = Color(0xFF1E1E2C),
)

private fun buildAmoledColors(p: AccentPalette) = darkColorScheme(
    primary            = p.darkPrimary,
    onPrimary          = Color.White,
    primaryContainer   = p.darkContainer,
    onPrimaryContainer = p.darkOnContainer,
    secondary          = p.darkSecondary,
    background         = BackgroundAmoled,
    surface            = SurfaceAmoled,
    surfaceVariant     = Color(0xFF12121C),
    onBackground       = Color.White,
    onSurface          = Color.White,
    onSurfaceVariant   = Color(0xFF6A6A88),
    outline            = Color(0xFF18181E),
    outlineVariant     = Color(0xFF10101A),
)

private fun buildLightColors(p: AccentPalette) = lightColorScheme(
    primary            = p.lightPrimary,
    onPrimary          = Color.White,
    primaryContainer   = p.lightContainer,
    onPrimaryContainer = p.lightOnContainer,
    secondary          = p.lightPrimary,
    background         = BackgroundLight,
    surface            = SurfaceLight,
    surfaceVariant     = SurfaceVariantLight,
    onBackground       = OnSurfaceLight,
    onSurface          = OnSurfaceLight,
    onSurfaceVariant   = SubtextLight,
    outline            = Color(0xFFD8D8E8),
    outlineVariant     = Color(0xFFEAEAF2),
)

@Composable
fun StreamFlowTheme(theme: AppTheme = AppTheme.DARK, accent: String = "RED", content: @Composable () -> Unit) {
    val isDark   = when (theme) {
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        else -> true
    }
    val context = LocalContext.current
    val palette = accentPalettes[accent] ?: accentPalettes["RED"]!!
    val colors  = when {
        accent == "DYNAMIC" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> when (theme) {
            AppTheme.DARK   -> buildDarkColors(palette)
            AppTheme.AMOLED -> buildAmoledColors(palette)
            AppTheme.LIGHT  -> buildLightColors(palette)
            AppTheme.SYSTEM -> if (isDark) buildDarkColors(palette) else buildLightColors(palette)
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography  = AppTypography,
        content     = content
    )
}
