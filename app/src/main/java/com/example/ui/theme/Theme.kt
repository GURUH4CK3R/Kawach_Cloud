package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KawachPrimary,
    onPrimary = Color.Black,
    primaryContainer = KawachPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = KawachAccent,
    onSecondary = Color.Black,
    secondaryContainer = KawachOrangeDark,
    onSecondaryContainer = Color.White,
    tertiary = KawachOrangeLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSurfaceBorder,
    error = KawachError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = KawachPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = KawachPrimaryLight,
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = KawachOrangeDark,
    onSecondary = Color.White,
    secondaryContainer = KawachOrangeLight,
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = KawachAccent,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightSurfaceBorder,
    error = KawachError,
    onError = Color.White
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun KawachCloudTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
