package com.elyacoub9.flowsy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FlowsyColorScheme = darkColorScheme(
    primary = LeftColor,
    onPrimary = Color(0xFF1F1A0A),
    primaryContainer = Color(0xFF4A3D14),
    onPrimaryContainer = LeftColor,
    secondary = SavingsColor,
    onSecondary = Color(0xFF0E1236),
    secondaryContainer = Color(0xFF2B3168),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = IncomeColor,
    onTertiary = Color(0xFF00281A),
    error = OverColor,
    onError = Color(0xFF3A0010),
    background = Background,
    onBackground = OnBackground,
    surface = Background,
    onSurface = OnBackground,
    surfaceVariant = CardSurface,
    onSurfaceVariant = OnSurfaceMuted,
    surfaceContainerLowest = Background,
    surfaceContainerLow = CardSurface,
    surfaceContainer = CardSurface,
    surfaceContainerHigh = CardSurfaceHigh,
    surfaceContainerHighest = CardSurfaceHigh,
    outline = Outline,
    outlineVariant = Outline,
)

/** Dark theme only. */
@Composable
fun FlowsyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlowsyColorScheme,
        typography = Typography,
        content = content,
    )
}
