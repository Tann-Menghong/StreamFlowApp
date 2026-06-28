package com.streamflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme { DARK, AMOLED, LIGHT }

fun String.toAppTheme(): AppTheme = when (this) {
    "AMOLED" -> AppTheme.AMOLED
    "LIGHT"  -> AppTheme.LIGHT
    else     -> AppTheme.DARK
}

private val DarkColors = darkColorScheme(
    primary = Red80,
    onPrimary = Dark,
    secondary = Red40,
    background = Dark,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

private val AmoledColors = darkColorScheme(
    primary = Red80,
    onPrimary = Color.Black,
    secondary = Red40,
    background = AmoledBg,
    surface = AmoledSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = RedLight,
    onPrimary = Color.White,
    secondary = Red40,
    background = LightBg,
    surface = LightSurface,
    onBackground = LightOnBg,
    onSurface = LightOnBg
)

@Composable
fun StreamFlowTheme(theme: AppTheme = AppTheme.DARK, content: @Composable () -> Unit) {
    val colorScheme = when (theme) {
        AppTheme.DARK   -> DarkColors
        AppTheme.AMOLED -> AmoledColors
        AppTheme.LIGHT  -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
