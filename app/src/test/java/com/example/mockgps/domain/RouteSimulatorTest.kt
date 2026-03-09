package com.example.mockgps.domain

import com.example.mockgps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSimulatorTest {

    @Test
    fun `play emits final waypoint`() = runTest {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.observeAltitude() } returns flowOf(15.0)
        every { settingsRepository.observeRandomAltitude() } returns flowOf(false)
        every { settingsRepository.observeCoordinateJitter() } returns flowOf(false)

        val simulator = RouteSimulator(settingsRepository)
        val start = LatLng(0.0, 0.0)
        val end = LatLng(0.0001, 0.0001) // Small distance
        val route = listOf(start, end)

        simulator.setRoute(route)
        simulator.setSpeed(100.0) // High speed to finish quickly

        val emittedLocations = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedLocations.add(it) }
        }

        simulator.play(this)
        advanceUntilIdle()

        // Verify the last emitted non-null location is the end point
        val lastPoint = emittedLocations.filterNotNull().last()
        assertEquals(end, lastPoint.latLng)

        job.cancel()
    }
}
