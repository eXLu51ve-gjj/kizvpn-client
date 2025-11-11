package com.kizvpn.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Темная неоновая цветовая схема
private val DarkColorScheme = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = Color.White,
    secondary = NeonAccent,
    onSecondary = Color.White,
    tertiary = NeonGreen,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Color(0xFFFF6B6B),
    onError = Color.White
)

// Светлая цветовая схема (Material 3 Light с мягкими тенями)
private val LightColorScheme = lightColorScheme(
    primary = NeonPrimary,
    onPrimary = Color.White,
    secondary = NeonAccent,
    onSecondary = Color.White,
    tertiary = NeonGreen,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = TextSecondaryLight
)

// Типографика
private val KizVpnTypography = Typography(
    // Используем системные шрифты Material 3
)

@Composable
fun KizVpnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KizVpnTypography,
        content = content
    )
}

