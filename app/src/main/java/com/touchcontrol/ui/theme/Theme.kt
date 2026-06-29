package com.touchcontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlue.copy(alpha = 0.2f),
    onPrimaryContainer = AccentBlueLight,
    secondary = AccentGreen,
    onSecondary = Color.White,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = AccentRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlue.copy(alpha = 0.1f),
    onPrimaryContainer = Color(0xFF3A35A0),
    secondary = Color(0xFF00875A),
    onSecondary = Color.White,
    background = Color(0xFFF8F8FF),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFF0F0FF),
    onSurfaceVariant = Color(0xFF555577),
    outline = Color(0xFFD0D0E0),
    error = Color(0xFFCC3344),
    onError = Color.White,
)

@Composable
fun TouchControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
