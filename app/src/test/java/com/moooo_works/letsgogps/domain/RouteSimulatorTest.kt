package com.moooo_works.letsgogps.domain

import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSimulatorTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setup() {
        every { settingsRepository.observeAltitude() } returns flowOf(15.0)
        every { settingsRepository.observeRandomAltitude() } returns flowOf(false)
        every { settingsRepository.observeCoordinateJitter() } returns flowOf(false)
    }

    @Test
    fun `play emits final waypoint`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val start = LatLng(0.0, 0.0)
        val end = LatLng(0.0001, 0.0001)
        val route = listOf(start, end)

        simulator.setRoute(route)
        simulator.setSpeed(100.0)

        val emittedLocations = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedLocations.add(it) }
        }

        simulator.play(this)
        advanceUntilIdle()

        val lastPoint = emittedLocations.filterNotNull().last()
        assertEquals(end, lastPoint.latLng)
        job.cancel()
    }

    @Test
    fun `bearing is calculated correctly for cardinal directions`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        
        // North: (0,0) -> (1,0)
        val routeNorth = listOf(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        simulator.setRoute(routeNorth)
        simulator.setSpeed(1.0)
        
        val emittedPoints = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedPoints.add(it) }
        }
        
        simulator.play(this)
        advanceUntilIdle()
        
        val point = emittedPoints.filterNotNull().first()
        // Bearing to North should be 0
        assertEquals(0f, point.bearing, 0.1f)
        
        // East: (0,0) -> (0,1)
        val routeEast = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        simulator.setRoute(routeEast)
        simulator.play(this)
        advanceUntilIdle()
        val pointEast = emittedPoints.filterNotNull().last { it.latLng.longitude > 0 }
        assertEquals(90f, pointEast.bearing, 0.1f)
        
        job.cancel()
    }

    @Test
    fun `jitter is disabled during route simulation for smooth movement`() = runTest {
        every { settingsRepository.observeCoordinateJitter() } returns flowOf(true)
        val simulator = RouteSimulator(settingsRepository)

        val start = LatLng(25.0, 121.0)
        val end = LatLng(25.1, 121.1)
        simulator.setRoute(listOf(start, end))
        simulator.setSpeed(1.0)

        val emittedPoints = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedPoints.add(it) }
        }

        simulator.play(this)
        advanceUntilIdle()

        val firstPoint = emittedPoints.filterNotNull().first()
        // Jitter is intentionally disabled during route simulation to prevent map icon shaking.
        // The emitted lat/lng must exactly match the waypoint.
        assertEquals(start.latitude, firstPoint.latLng.latitude, 0.0)
        assertEquals(start.longitude, firstPoint.latLng.longitude, 0.0)

        job.cancel()
    }
}
