package com.mosquetracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = MasjidGreen800,
    onPrimary = Neutral0,
    primaryContainer = MasjidGreen100,
    onPrimaryContainer = MasjidGreen900,
    secondary = Gold500,
    onSecondary = Neutral0,
    secondaryContainer = Gold100,
    onSecondaryContainer = Color(0xFF3F2E00),
    tertiary = Gold400,
    background = Neutral0,
    onBackground = Neutral90,
    surface = Neutral0,
    onSurface = Neutral90,
    surfaceVariant = Neutral10,
    onSurfaceVariant = Color(0xFF44474A),
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = MasjidGreen700,
    onPrimary = Neutral0,
    primaryContainer = MasjidGreen900,
    onPrimaryContainer = MasjidGreen100,
    secondary = Gold400,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Gold400,
    tertiary = Gold400,
    background = Neutral95,
    onBackground = Color(0xFFE2E2E5),
    surface = Neutral95,
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Neutral90,
    onSurfaceVariant = Color(0xFFC4C7CA),
)

@Composable
fun MosqueTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Brand-first: dynamic (wallpaper) colors would replace the MasjidSync
    // green/gold identity, so they stay off.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
