package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.moooo_works.letsgogps.ui.theme.MockGpsTheme
import com.moooo_works.letsgogps.ui.theme.ThemePreference
import java.util.Locale

internal fun overlayConfiguration(base: Configuration, language: String): Configuration =
    Configuration(base).apply {
        if (language.isNotBlank()) setLocales(LocaleList(Locale.forLanguageTag(language)))
    }

internal fun floatingOverlayColors(base: ColorScheme, dark: Boolean): ColorScheme {
    val foreground = if (dark) Color(0xFFF8FAFC) else Color(0xFF202124)
    val secondaryText = if (dark) Color(0xFFCDD6E0) else Color(0xFF4B5563)
    val selected = if (dark) Color(0xFF573827) else Color(0xFFFFE4D6)
    val selectedText = if (dark) Color(0xFFFFE8D9) else Color(0xFF57250E)
    return base.copy(
        surface = if (dark) Color(0xFF18212C) else Color(0xFFFFF8F2),
        onSurface = foreground,
        surfaceVariant = if (dark) Color(0xFF263343) else Color(0xFFF3E6DD),
        onSurfaceVariant = secondaryText,
        primary = if (dark) Color(0xFFFFAB91) else Color(0xFFB83A10),
        onPrimary = if (dark) Color(0xFF3C1608) else Color.White,
        error = if (dark) Color(0xFFFFB4AB) else base.error,
        onError = if (dark) Color(0xFF690005) else base.onError,
        primaryContainer = selected, onPrimaryContainer = selectedText,
        secondaryContainer = selected, onSecondaryContainer = selectedText,
        outline = if (dark) Color(0xFF9EAFC4) else Color(0xFF6B7280),
        outlineVariant = if (dark) Color(0xFF9EAFC4) else Color(0xFF6B7280)
    )
}

/** 浮窗使用 App 的設定與語系；不修改 Application 的 Resources 或重建視窗。 */
@Composable
internal fun FloatingOverlayTheme(context: Context, content: @Composable () -> Unit) {
    val prefs = remember(context) { context.getSharedPreferences("mockgps_prefs", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_pref" || key == "language_pref") revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // 讀取與註冊之間若設定已改變，首次 composition 也重新讀取。
        revision++
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val appearance = remember(prefs, revision) {
        val theme = runCatching { ThemePreference.valueOf(prefs.getString("theme_pref", "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemePreference.SYSTEM)
        theme to (prefs.getString("language_pref", "") ?: "")
    }
    val base = LocalConfiguration.current
    val localized = remember(context, base, appearance.second) {
        context.createConfigurationContext(overlayConfiguration(base, appearance.second))
    }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration) {
        val dark = when (appearance.first) {
            ThemePreference.LIGHT -> false
            ThemePreference.DARK -> true
            ThemePreference.SYSTEM -> isSystemInDarkTheme()
        }
        MockGpsTheme(themePreference = appearance.first) {
            MaterialTheme(colorScheme = floatingOverlayColors(MaterialTheme.colorScheme, dark), content = content)
        }
    }
}
