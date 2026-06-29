package com.streamflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

private val DarkColors = darkColorScheme(
    primary            = PrimaryRed,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF2A0808),
    onPrimaryContainer = Color(0xFFFFAAAA),
    secondary          = PrimaryRedDim,
    background         = BackgroundDark,
    surface            = SurfaceDark,
    surfaceVariant     = SurfaceVariantDark,
    onBackground       = OnSurfaceDark,
    onSurface          = OnSurfaceDark,
    onSurfaceVariant   = SubtextDark,
    outline            = Color(0xFF282838),
    outlineVariant     = Color(0xFF1E1E2C),
)

private val AmoledColors = darkColorScheme(
    primary            = PrimaryRed,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF180000),
    onPrimaryContainer = Color(0xFFFFAAAA),
    secondary          = PrimaryRedDim,
    background         = BackgroundAmoled,
    surface            = SurfaceAmoled,
    surfaceVariant     = Color(0xFF12121C),
    onBackground       = Color.White,
    onSurface          = Color.White,
    onSurfaceVariant   = Color(0xFF6A6A88),
    outline            = Color(0xFF18181E),
    outlineVariant     = Color(0xFF10101A),
)

private val LightColors = lightColorScheme(
    primary            = PrimaryRedLight,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFE8E8),
    onPrimaryContainer = Color(0xFF7A0000),
    secondary          = Color(0xFF9B0B0B),
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
fun StreamFlowTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) {
            AppTheme.DARK   -> DarkColors
            AppTheme.AMOLED -> AmoledColors
            AppTheme.LIGHT  -> LightColors
            AppTheme.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
        },
        typography = AppTypography,
        content = content
    )
}
