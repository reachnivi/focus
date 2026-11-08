package com.reachnivi.focus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Palette.Sage700,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.Sage100,
    onPrimaryContainer = Palette.Sage900,
    secondary = Palette.InkSoft,
    onSecondary = Palette.Paper,
    secondaryContainer = Palette.PaperDeep,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Clay,
    onTertiary = Palette.Paper,
    tertiaryContainer = Palette.ClayLight,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperDeep,
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White,
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFBFAF7),
    surfaceContainer = androidx.compose.ui.graphics.Color.White,
    surfaceContainerHigh = Palette.PaperDeep,
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFE3DED3),
    outline = androidx.compose.ui.graphics.Color(0xFFCAC5BA),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFE2DED4),
    error = androidx.compose.ui.graphics.Color(0xFFB3412E),
)

private val DarkColors = darkColorScheme(
    primary = Palette.Sage300,
    onPrimary = Palette.Sage900,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF2C4331),
    onPrimaryContainer = Palette.Sage100,
    secondary = Palette.NightTextSoft,
    onSecondary = Palette.Night,
    secondaryContainer = Palette.NightHigh,
    onSecondaryContainer = Palette.NightText,
    tertiary = androidx.compose.ui.graphics.Color(0xFFE4A389),
    onTertiary = Palette.Night,
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF4A2E24),
    onTertiaryContainer = Palette.ClayLight,
    background = Palette.Night,
    onBackground = Palette.NightText,
    surface = Palette.Night,
    onSurface = Palette.NightText,
    surfaceVariant = Palette.NightHigh,
    onSurfaceVariant = Palette.NightTextSoft,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0C0E0D),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF151815),
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightHigh,
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF2C322D),
    outline = androidx.compose.ui.graphics.Color(0xFF3E453F),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF2B302C),
    error = androidx.compose.ui.graphics.Color(0xFFF0A08E),
)

val FocusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FocusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FocusTypography,
        shapes = FocusShapes,
        content = content,
    )
}
