package com.example.mockgps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background           = Dark900,
    onBackground         = White,
    surface              = Dark800,
    surfaceVariant       = Dark600,
    onSurface            = White,
    onSurfaceVariant     = TextSecondaryDark,
    primary              = Green400,
    onPrimary            = Dark900,
    primaryContainer     = GreenSurface900,
    onPrimaryContainer   = Light200,
    secondary            = TextTertiaryDark,
    onSecondary          = White,
    secondaryContainer   = Dark700,
    onSecondaryContainer = TextMutedDark,
    error                = Red500,
    onError              = White,
    outline              = Dark500,
    outlineVariant       = Dark600,
    scrim                = Dark900,
)

private val LightColorScheme = lightColorScheme(
    background           = Light100,
    onBackground         = Dark900,
    surface              = White,
    surfaceVariant       = Light50,
    onSurface            = Dark900,
    onSurfaceVariant     = TextSecondaryLight,
    primary              = Green400,
    onPrimary            = Dark900,
    primaryContainer     = Light200,
    onPrimaryContainer   = GreenSurface900,
    secondary            = TextSecondaryLight,
    onSecondary          = White,
    secondaryContainer   = Light50,
    onSecondaryContainer = TextTertiaryLight,
    error                = Red500,
    onError              = White,
    outline              = OutlineLight,
    outlineVariant       = Light300,
    scrim                = Dark900,
)

@Composable
fun MockGpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MockGpsTypography,
        content = content
    )
}
