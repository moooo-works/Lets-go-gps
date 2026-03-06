package com.example.mockgps.utils

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LatLngBoundsUtilTest {

    @Test
    fun `calculateFitTarget returns min max bounds for two points`() {
        val points = listOf(
            LatLng(25.0, 121.6),
            LatLng(24.5, 121.2)
        )

        val result = LatLngBoundsUtil.calculateFitTarget(points)

        assertNotNull(result.bounds)
        assertNull(result.fallbackCenter)
        assertEquals(24.5, result.bounds?.minLat ?: 0.0, 0.0)
        assertEquals(25.0, result.bounds?.maxLat ?: 0.0, 0.0)
        assertEquals(121.2, result.bounds?.minLng ?: 0.0, 0.0)
        assertEquals(121.6, result.bounds?.maxLng ?: 0.0, 0.0)
    }

    @Test
    fun `calculateFitTarget returns min max bounds for multiple points`() {
        val points = listOf(
            LatLng(25.1, 121.5),
            LatLng(25.5, 121.9),
            LatLng(24.8, 121.3),
            LatLng(25.0, 121.7)
        )

        val result = LatLngBoundsUtil.calculateFitTarget(points)

        assertNotNull(result.bounds)
        assertNull(result.fallbackCenter)
        assertEquals(24.8, result.bounds?.minLat ?: 0.0, 0.0)
        assertEquals(25.5, result.bounds?.maxLat ?: 0.0, 0.0)
        assertEquals(121.3, result.bounds?.minLng ?: 0.0, 0.0)
        assertEquals(121.9, result.bounds?.maxLng ?: 0.0, 0.0)
    }

    @Test
    fun `calculateFitTarget returns fallback center for identical points`() {
        val identical = LatLng(25.033, 121.5654)
        val points = listOf(identical, identical)

        val result = LatLngBoundsUtil.calculateFitTarget(points)

        assertNull(result.bounds)
        assertEquals(identical, result.fallbackCenter)
    }
}
