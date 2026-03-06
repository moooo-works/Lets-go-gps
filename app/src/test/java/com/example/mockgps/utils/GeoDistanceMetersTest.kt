package com.example.mockgps.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceMetersTest {

    @Test
    fun haversineMeters_samePoint_returnsZero() {
        val lat = 25.0330
        val lng = 121.5654
        val distance = GeoDistanceMeters.haversineMeters(lat, lng, lat, lng)
        assertEquals(0.0, distance, 0.01)
    }

    @Test
    fun haversineMeters_knownDistance_returnsApproximateMeters() {
        val lat1 = 25.0330
        val lng1 = 121.5654
        val lat2 = 25.0478
        val lng2 = 121.5170

        val distance = GeoDistanceMeters.haversineMeters(lat1, lng1, lat2, lng2)
        assertEquals(5130.0, distance, 100.0)
    }

    @Test
    fun haversineMeters_veryClosePoints_returnsSmallDistance() {
        val lat1 = 25.0000
        val lng1 = 121.0000
        val lat2 = 25.0001
        val lng2 = 121.0000

        val distance = GeoDistanceMeters.haversineMeters(lat1, lng1, lat2, lng2)
        assertEquals(11.1, distance, 1.0)
    }
}
