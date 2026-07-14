package com.clearlens.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF087F6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F2DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF45645B),
    background = Color(0xFFF7FAF9),
    surface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFFDDE8E4),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EDBB9),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4945)
)

@Composable
fun ClearLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
