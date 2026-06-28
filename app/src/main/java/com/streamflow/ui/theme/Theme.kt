package com.streamflow.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme { DARK, AMOLED, LIGHT }

fun String.toAppTheme(): AppTheme = when (this) {
    "AMOLED" -> AppTheme.AMOLED
    "LIGHT"  -> AppTheme.LIGHT
    else     -> AppTheme.DARK
}

private val AppTypography = Typography(
    titleLarge  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 10.sp, letterSpacing = 0.4.sp),
)

private val DarkColors = darkColorScheme(
    primary            = PrimaryRed,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF2D0A0A),
    onPrimaryContainer = Color(0xFFFF8080),
    secondary          = PrimaryRedDim,
    background         = BackgroundDark,
    surface            = SurfaceDark,
    surfaceVariant     = SurfaceVariantDark,
    onBackground       = OnSurfaceDark,
    onSurface          = OnSurfaceDark,
    onSurfaceVariant   = SubtextDark,
    outline            = Color(0xFF2E2E40)
)

private val AmoledColors = darkColorScheme(
    primary            = PrimaryRed,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFF1A0000),
    onPrimaryContainer = Color(0xFFFF8080),
    secondary          = PrimaryRedDim,
    background         = BackgroundAmoled,
    surface            = SurfaceAmoled,
    surfaceVariant     = Color(0xFF141420),
    onBackground       = Color.White,
    onSurface          = Color.White,
    onSurfaceVariant   = Color(0xFF7777A0),
    outline            = Color(0xFF1A1A28)
)

private val LightColors = lightColorScheme(
    primary            = PrimaryRedLight,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFEAEA),
    onPrimaryContainer = Color(0xFF8B0000),
    secondary          = Color(0xFFAA1111),
    background         = BackgroundLight,
    surface            = SurfaceLight,
    surfaceVariant     = SurfaceVariantLight,
    onBackground       = OnSurfaceLight,
    onSurface          = OnSurfaceLight,
    onSurfaceVariant   = SubtextLight,
    outline            = Color(0xFFDDDDEA)
)

@Composable
fun StreamFlowTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) {
            AppTheme.DARK   -> DarkColors
            AppTheme.AMOLED -> AmoledColors
            AppTheme.LIGHT  -> LightColors
        },
        typography = AppTypography,
        content = content
    )
}
