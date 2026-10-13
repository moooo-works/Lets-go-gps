package com.moooo_works.letsgogps.ui.map

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import com.moooo_works.letsgogps.ui.theme.Red500
import com.moooo_works.letsgogps.ui.theme.V2BgLight
import com.moooo_works.letsgogps.ui.theme.V2SurfaceLight
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
    @Test fun `enabled labels and outlines remain readable in both themes`() {
        for (dark in listOf(false, true)) {
            val base = (if (dark) darkColorScheme() else lightColorScheme()).copy(
                error = Red500, onError = if (dark) V2BgLight else V2SurfaceLight)
            val colors = floatingOverlayColors(base, dark)
            for ((text, background) in listOf(colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface, colors.primary to colors.surface,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onPrimary to colors.primary,
                colors.onSurfaceVariant to colors.surfaceVariant,
                colors.error to colors.surface, colors.onError to colors.error)) {
                assertTrue("dark=$dark contrast=${contrast(text, background)}", contrast(text, background) >= 4.5f)
            }
            for (underlay in listOf(Color.Black, Color.White)) {
                val translucentBackground = colors.surface.copy(alpha = .82f).compositeOver(underlay)
                for (text in listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary, colors.error)) {
                    assertTrue("translucent dark=$dark contrast=${contrast(text, translucentBackground)}",
                        contrast(text, translucentBackground) >= 4.5f)
                }
            }
            assertTrue(contrast(colors.outline, colors.surface) >= 3f)
            assertTrue(contrast(colors.outline, colors.surfaceVariant) >= 3f)
        }
    }
}
