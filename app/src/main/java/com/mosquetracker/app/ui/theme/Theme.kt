package com.mosquetracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Neutral0,
    primaryContainer = Teal50,
    onPrimaryContainer = Teal900,
    secondary = Gold600,
    onSecondary = Neutral0,
    secondaryContainer = Gold400,
    onSecondaryContainer = Color(0xFF3F2E00),
    background = Neutral0,
    onBackground = Neutral90,
    surface = Neutral0,
    onSurface = Neutral90,
    surfaceVariant = Neutral10,
    onSurfaceVariant = Color(0xFF44474A),
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Teal600,
    onPrimary = Neutral0,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal50,
    secondary = Gold400,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Gold400,
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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
