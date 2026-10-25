package com.mosquetracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = ForestDeep,
    inversePrimary = MintBright,
    secondary = Bronze,
    onSecondary = Color.White,
    secondaryContainer = Sand,
    onSecondaryContainer = Color(0xFF342707),
    tertiary = Gold,
    onTertiary = Color(0xFF2B2108),
    tertiaryContainer = Sand,
    onTertiaryContainer = Color(0xFF342707),
    background = Ivory,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF2EC),
    onSurfaceVariant = Color(0xFF4B5A50),
    surfaceTint = Forest,
    surfaceDim = Color(0xFFDCE3DA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5EF),
    surfaceContainer = Color(0xFFEDF2EC),
    surfaceContainerHigh = Color(0xFFE6EDE4),
    surfaceContainerHighest = Color(0xFFDFE7DD),
    inverseSurface = Ink,
    inverseOnSurface = Ivory,
    outline = Color(0xFF738176),
    outlineVariant = Color(0xFFCDD8CD),
    scrim = Color.Black,
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = MintBright,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF174E3D),
    onPrimaryContainer = Mint,
    inversePrimary = Forest,
    secondary = Gold,
    onSecondary = Color(0xFF2B2108),
    secondaryContainer = Color(0xFF4C3D1D),
    onSecondaryContainer = Sand,
    tertiary = Gold,
    onTertiary = Color(0xFF2B2108),
    tertiaryContainer = Color(0xFF4C3D1D),
    onTertiaryContainer = Sand,
    background = Color(0xFF0E1613),
    onBackground = Color(0xFFE1EAE1),
    surface = Color(0xFF151F1A),
    onSurface = Color(0xFFE1EAE1),
    surfaceVariant = Color(0xFF1D2B24),
    onSurfaceVariant = Color(0xFFB8C8BC),
    surfaceTint = MintBright,
    surfaceDim = Color(0xFF0E1613),
    surfaceBright = Color(0xFF344039),
    surfaceContainerLowest = Color(0xFF0B120E),
    surfaceContainerLow = Color(0xFF151F1A),
    surfaceContainer = Color(0xFF1A261F),
    surfaceContainerHigh = Color(0xFF243128),
    surfaceContainerHighest = Color(0xFF2E3C32),
    inverseSurface = Color(0xFFE1EAE1),
    inverseOnSurface = Ink,
    outline = Color(0xFF849A8A),
    outlineVariant = Color(0xFF405347),
    scrim = Color.Black,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF742C28),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MosqueTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Wallpaper colors would replace the app's green/gold identity.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
