package com.moooo_works.letsgogps.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionTest {
    @Test fun `panel remains on screen after resize and drag`() {
        assertEquals(0, clampOverlayPosition(-20, 320, 300))
        assertEquals(20, clampOverlayPosition(400, 320, 300))
        assertEquals(0, clampOverlayPosition(100, 320, 600))
        assertEquals(100, clampOverlayPosition(100, 1080, 300))
    }
}
