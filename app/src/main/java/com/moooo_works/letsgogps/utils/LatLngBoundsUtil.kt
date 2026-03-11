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

object LatLngBoundsUtil {

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
