package com.moooo_works.letsgogps.utils

import com.google.android.gms.maps.model.LatLng

data class RouteFitBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

data class RouteFitTarget(
    val bounds: RouteFitBounds?,
    val fallbackCenter: LatLng?
)

/**
 * How to move the camera to show a route's bounds.
 * [FitBounds] carries the exact args for `newLatLngBounds(bounds, widthPx, heightPx, paddingPx)`;
 * [FallbackZoom] means the viewport is too small to fit safely — center + default zoom instead.
 */
sealed interface RouteFitPlan {
    data class FitBounds(val widthPx: Int, val heightPx: Int, val paddingPx: Int) : RouteFitPlan
    object FallbackZoom : RouteFitPlan
}

object LatLngBoundsUtil {

    /**
     * Decides whether a bounds-fit is geometrically safe. The Maps SDK's
     * `newLatLngBounds(bounds, w, h, pad)` subtracts `pad` from every side of the
     * (w, h) you hand it, then throws "View size is too small after padding is
     * applied" if what's left is non-positive. We reserve extra space at the
     * bottom for the route panel, so guard against that second subtraction too —
     * otherwise the SDK assertion escapes every try/catch and kills the process.
     */
    fun planRouteFit(
        widthPx: Int,
        heightPx: Int,
        mapPaddingPx: Int,
        bottomPanelReservePx: Int
    ): RouteFitPlan {
        if (widthPx <= 2 || heightPx <= 2) return RouteFitPlan.FallbackZoom

        val maxPadX = (widthPx / 2) - 1
        val maxPadY = (heightPx / 2) - 1
        if (maxPadX <= 0 || maxPadY <= 0) return RouteFitPlan.FallbackZoom

        val safeLeft = minOf(mapPaddingPx, maxPadX)
        val safeRight = minOf(mapPaddingPx, maxPadX)
        val safeTop = minOf(mapPaddingPx, maxPadY)
        val safeBottom = minOf(mapPaddingPx + bottomPanelReservePx, maxPadY)

        val viewportW = widthPx - safeLeft - safeRight
        val viewportH = heightPx - safeTop - safeBottom
        val uniformPad = minOf(minOf(safeLeft, safeRight), minOf(safeTop, safeBottom))

        // The SDK subtracts uniformPad from both sides again; require what remains > 0.
        if (viewportW - 2 * uniformPad <= 0 || viewportH - 2 * uniformPad <= 0) {
            return RouteFitPlan.FallbackZoom
        }
        return RouteFitPlan.FitBounds(viewportW, viewportH, uniformPad)
    }

    fun calculateFitTarget(points: List<LatLng>): RouteFitTarget {
        if (points.size < 2) {
            return RouteFitTarget(bounds = null, fallbackCenter = null)
        }

        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val isZeroDistance = minLat == maxLat && minLng == maxLng
        if (isZeroDistance) {
            return RouteFitTarget(bounds = null, fallbackCenter = points.first())
        }

        return RouteFitTarget(
            bounds = RouteFitBounds(
                minLat = minLat,
                maxLat = maxLat,
                minLng = minLng,
                maxLng = maxLng
            ),
            fallbackCenter = null
        )
    }
}
