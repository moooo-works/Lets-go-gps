package com.example.mockgps.domain

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val simulator = RouteSimulator()
        val start = LatLng(0.0, 0.0)
        val end = LatLng(0.0001, 0.0001) // Small distance
        val route = listOf(start, end)

        simulator.setRoute(route)
        simulator.setSpeed(100.0) // High speed to finish quickly

        val emittedLocations = mutableListOf<LatLng?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedLocations.add(it) }
        }

        simulator.play(this)
        advanceUntilIdle()

        // Verify the last emitted non-null location is the end point
        val lastLocation = emittedLocations.filterNotNull().last()
        assertEquals(end, lastLocation)

        job.cancel()
    }
}
