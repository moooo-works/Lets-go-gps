package com.moooo_works.letsgogps.ui.map

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingOverlayColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }
    @Test fun `enabled labels and outlines remain readable in both themes`() {
        for (dark in listOf(false, true)) {
            val colors = floatingOverlayColors(if (dark) darkColorScheme() else lightColorScheme(), dark)
            for ((text, background) in listOf(colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surface, colors.primary to colors.surface,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onPrimary to colors.primary)) {
                assertTrue("dark=$dark contrast=${contrast(text, background)}", contrast(text, background) >= 4.5f)
            }
            assertTrue(contrast(colors.outline, colors.surface) >= 3f)
        }
    }
}
