package com.moooo_works.letsgogps.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSpeedSliderConfigTest {

    @Test
    fun `route speed slider keeps one kilometer display bounds`() {
        assertEquals(1f, ROUTE_SPEED_MIN_KMH)
        assertEquals(100f, ROUTE_SPEED_MAX_KMH)
        assertEquals(0, ROUTE_SPEED_STEPS)
    }
}
