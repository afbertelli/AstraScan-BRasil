package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AstraCockpitColorScheme = darkColorScheme(
    primary = GmAmber,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF332000),
    onPrimaryContainer = GmAmberLight,
    secondary = AstraCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00363A),
    onSecondaryContainer = AstraCyan,
    tertiary = AstraRedline,
    onTertiary = Color.White,
    background = CarbonDark,
    onBackground = TextPrimary,
    surface = CarbonCard,
    onSurface = TextPrimary,
    surfaceVariant = CarbonElevated,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder,
    error = AstraRedline,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AstraCockpitColorScheme,
        typography = Typography,
        content = content
    )
}
