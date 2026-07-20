package com.moooo_works.letsgogps.utils

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ── planRouteFit ──────────────────────────────────────────────────────────
    // 80dp + 140dp bottom reserve, converted at ~3x density (≈ px used on a phone).
    private val mapPadPx = 240
    private val bottomReservePx = 420

    @Test
    fun `planRouteFit fits bounds on a normal phone viewport`() {
        val plan = LatLngBoundsUtil.planRouteFit(1080, 2160, mapPadPx, bottomReservePx)

        assertTrue(plan is RouteFitPlan.FitBounds)
        plan as RouteFitPlan.FitBounds
        // width minus left+right map padding; padding usable after the SDK's 2x subtraction.
        assertEquals(1080 - mapPadPx - mapPadPx, plan.widthPx)
        assertTrue(plan.widthPx - 2 * plan.paddingPx > 0)
        assertTrue(plan.heightPx - 2 * plan.paddingPx > 0)
    }

    @Test
    fun `planRouteFit falls back on a tiny viewport`() {
        assertEquals(RouteFitPlan.FallbackZoom, LatLngBoundsUtil.planRouteFit(2, 2, mapPadPx, bottomReservePx))
        assertEquals(RouteFitPlan.FallbackZoom, LatLngBoundsUtil.planRouteFit(0, 1000, mapPadPx, bottomReservePx))
    }

    @Test
    fun `planRouteFit falls back when padding would collapse the viewport`() {
        // Short viewport: after reserving the bottom panel and the SDK's second
        // padding subtraction, usable height goes non-positive — must NOT hand
        // this to the SDK (it would throw "View size is too small after padding").
        val plan = LatLngBoundsUtil.planRouteFit(1080, 600, mapPadPx, bottomReservePx)

        assertEquals(RouteFitPlan.FallbackZoom, plan)
    }
}
