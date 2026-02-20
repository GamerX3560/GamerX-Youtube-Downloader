package com.gamerx.downloader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Dark variant builder ──
private fun darkTheme(
    bg: Color, surface: Color, surfaceVariant: Color,
    primary: Color, primaryDark: Color, primaryLight: Color,
    secondary: Color, secondaryDark: Color,
) = darkColorScheme(
    primary = primary,
    onPrimary = bg,
    primaryContainer = primaryDark,
    onPrimaryContainer = primaryLight,
    secondary = secondary,
    onSecondary = bg,
    secondaryContainer = secondaryDark,
    onSecondaryContainer = secondary,
    tertiary = NeonGreen,
    onTertiary = bg,
    error = NeonRed,
    onError = bg,
    errorContainer = NeonRed.copy(alpha = 0.2f),
    onErrorContainer = NeonRed,
    background = bg,
    onBackground = TextPrimary,
    surface = surface,
    onSurface = TextPrimary,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    scrim = ScrimDark,
    inverseSurface = TextPrimary,
    inverseOnSurface = bg,
    inversePrimary = primaryDark,
    surfaceTint = primary,
)

// ── Light variant ──
private val lightTheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryLight,
    onPrimaryContainer = LightPrimaryDark,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryDark,
    onSecondaryContainer = LightSecondary,
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightDivider,
    scrim = Color(0x99000000),
    inverseSurface = Color(0xFF2A2A3E),
    inverseOnSurface = Color(0xFFEEEEF8),
    inversePrimary = LightPrimaryLight,
    surfaceTint = LightPrimary,
)

@Composable
fun GamerXTheme(
    themeMode: String = "dark",
    transparentBars: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()

    val (colorScheme, isLight) = resolveTheme(themeMode, isSystemDark)

    val statusBarColor = if (transparentBars) Color.Transparent else colorScheme.background
    val navBarColor = if (transparentBars) Color.Transparent else colorScheme.background

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = statusBarColor.toArgb()
            window.navigationBarColor = navBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GamerXTypography,
        content = content,
    )
}

private fun resolveTheme(themeMode: String, isSystemDark: Boolean): Pair<ColorScheme, Boolean> {
    return when (themeMode) {
        "amoled" -> darkTheme(
            AmoledBackground, AmoledSurface, AmoledSurfaceVariant,
            AmoledPrimary, AmoledPrimaryDark, AmoledPrimaryLight,
            AmoledSecondary, AmoledSecondaryDark,
        ) to false

        "light" -> lightTheme to true

        "midnight" -> darkTheme(
            MidnightBackground, MidnightSurface, MidnightSurfaceVariant,
            MidnightPrimary, MidnightPrimaryDark, MidnightPrimaryLight,
            MidnightSecondary, MidnightSecondaryDark,
        ) to false

        "ocean" -> darkTheme(
            OceanBackground, OceanSurface, OceanSurfaceVariant,
            OceanPrimary, OceanPrimaryDark, OceanPrimaryLight,
            OceanSecondary, OceanSecondaryDark,
        ) to false

        "system" -> {
            if (isSystemDark) {
                darkTheme(
                    DarkBackground, DarkSurface, DarkSurfaceVariant,
                    DarkPrimary, DarkPrimaryDark, DarkPrimaryLight,
                    DarkSecondary, DarkSecondaryDark,
                ) to false
            } else {
                lightTheme to true
            }
        }

        else -> darkTheme(
            DarkBackground, DarkSurface, DarkSurfaceVariant,
            DarkPrimary, DarkPrimaryDark, DarkPrimaryLight,
            DarkSecondary, DarkSecondaryDark,
        ) to false
    }
}
