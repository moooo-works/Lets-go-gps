package com.moooo_works.letsgogps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemePreference { SYSTEM, LIGHT, DARK }

private val DarkColorScheme = darkColorScheme(
    background           = V2BgDark,
    onBackground         = V2TextPrimaryDark,
    surface              = V2SurfaceDark,
    surfaceVariant       = V2SurfaceVariantDark,
    onSurface            = V2TextPrimaryDark,
    onSurfaceVariant     = V2TextSecondaryDark,
    primary              = Accent500,
    onPrimary            = V2BgLight, // Usually white or opposite bg
    primaryContainer     = V2SurfaceVariantDark,
    onPrimaryContainer   = V2TextPrimaryDark,
    secondary            = V2TextSecondaryDark,
    onSecondary          = V2TextPrimaryDark,
    secondaryContainer   = V2SurfaceDark,
    onSecondaryContainer = V2TextSecondaryDark,
    error                = Red500,
    onError              = V2BgLight,
    outline              = V2DividerDark,
    outlineVariant       = V2DividerDark,
    scrim                = V2BgDark,
)

private val LightColorScheme = lightColorScheme(
    background           = V2BgLight,
    onBackground         = V2TextPrimaryLight,
    surface              = V2SurfaceLight,
    surfaceVariant       = V2SurfaceVariantLight,
    onSurface            = V2TextPrimaryLight,
    onSurfaceVariant     = V2TextSecondaryLight,
    primary              = Accent500,
    onPrimary            = V2SurfaceLight,
    primaryContainer     = V2SurfaceVariantLight,
    onPrimaryContainer   = V2TextPrimaryLight,
    secondary            = V2TextSecondaryLight,
    onSecondary          = V2SurfaceLight,
    secondaryContainer   = V2SurfaceVariantLight,
    onSecondaryContainer = V2TextTertiaryLight,
    error                = Red500,
    onError              = V2SurfaceLight,
    outline              = V2TextTertiaryLight,
    outlineVariant       = V2DividerLight,
    scrim                = V2BgDark,
)

@Composable
fun MockGpsTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MockGpsTypography,
        content = content
    )
}
