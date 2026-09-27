package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HD_DarkPrimary,
    secondary = HD_DarkSecondary,
    tertiary = HD_DarkBorder,
    background = HD_DarkBg,
    surface = HD_DarkSurface,
    onPrimary = Color(0xFF001D36),
    onSecondary = HD_DarkTextPrimary,
    onBackground = HD_DarkTextPrimary,
    onSurface = HD_DarkTextPrimary,
    error = HD_DangerRed,
    outline = HD_DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = HD_LightPrimary,
    secondary = HD_LightSecondary,
    tertiary = HD_LightBorder,
    background = HD_LightBg,
    surface = HD_LightSurface,
    onPrimary = Color.White,
    onSecondary = HD_LightTextPrimary,
    onBackground = HD_LightTextPrimary,
    onSurface = HD_LightTextPrimary,
    error = HD_DangerRed,
    outline = HD_LightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
