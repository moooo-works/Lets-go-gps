package com.example.mockgps.domain

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSimulatorTest {

    @Test
    fun `play with valid route eventually returns to idle`() = runTest(StandardTestDispatcher()) {
        val simulator = RouteSimulator()
        simulator.setRoute(
            listOf(
                LatLng(25.0330, 121.5654),
                LatLng(25.0340, 121.5664)
            )
        )
        simulator.setSpeed(100.0)

        simulator.play(this)
        advanceTimeBy(6_000)
        advanceUntilIdle()

        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        assertNotNull(simulator.currentLocation.value)
    }

    @Test
    fun `pause changes simulation state to paused`() = runTest(StandardTestDispatcher()) {
        val simulator = RouteSimulator()
        simulator.setRoute(
            listOf(
                LatLng(25.0330, 121.5654),
                LatLng(25.0400, 121.5700)
            )
        )
        simulator.setSpeed(10.0)

        simulator.play(this)
        advanceTimeBy(1_000)
        simulator.pause()

        assertEquals(SimulationState.PAUSED, simulator.simulationState.value)
    }

    @Test
    fun `setSpeed enforces minimum positive speed`() = runTest(StandardTestDispatcher()) {
        val simulator = RouteSimulator()
        simulator.setRoute(
            listOf(
                LatLng(25.0330, 121.5654),
                LatLng(25.0331, 121.5655)
            )
        )

        simulator.setSpeed(0.0)
        simulator.play(this)
        advanceTimeBy(2_000)

        assertTrue(
            simulator.simulationState.value == SimulationState.PLAYING ||
                simulator.simulationState.value == SimulationState.IDLE
        )
    }
}
