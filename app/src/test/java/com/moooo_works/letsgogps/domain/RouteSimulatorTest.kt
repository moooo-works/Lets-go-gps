package com.moooo_works.letsgogps.domain

import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ─── Existing tests (preserved) ──────────────────────────────────────────

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
        assertEquals(0f, point.bearing, 0.1f)

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
        assertEquals(start.latitude, firstPoint.latLng.latitude, 0.0)
        assertEquals(start.longitude, firstPoint.latLng.longitude, 0.0)

        job.cancel()
    }

    // ─── New: RouteProgress tests ─────────────────────────────────────────────

    @Test
    fun `initial state is IDLE with null routeProgress`() {
        val simulator = RouteSimulator(settingsRepository)
        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        assertNull(simulator.routeProgress.value)
    }

    @Test
    fun `stop resets routeProgress to null`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0)))
        simulator.play(this)
        advanceUntilIdle()

        simulator.stop()

        assertNull(simulator.routeProgress.value)
        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
    }

    @Test
    fun `routeProgress fraction stays in 0 to 1 during playback`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0))
        simulator.setRoute(route)
        simulator.setSpeed(5.0)

        val fractions = mutableListOf<Float>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.routeProgress.collect { progress ->
                progress?.let { fractions.add(it.fraction) }
            }
        }

        simulator.play(this)
        advanceUntilIdle()

        assertTrue("Should have emitted progress", fractions.isNotEmpty())
        fractions.forEach { fraction ->
            assertTrue("fraction=$fraction must be in 0..1", fraction in 0f..1f)
        }
        job.cancel()
    }

    // ─── New: LoopMode.NONE tests ─────────────────────────────────────────────

    @Test
    fun `NONE mode simulator is IDLE and progress null after route completes`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0)))
        simulator.setLoopMode(LoopMode.NONE)
        simulator.setSpeed(100.0)
        simulator.play(this)
        advanceUntilIdle()

        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        assertNull(simulator.routeProgress.value)
    }

    // ─── New: LoopMode.LOOP tests ─────────────────────────────────────────────

    @Test
    fun `LOOP mode emits progress multiple times and stays PLAYING`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0)))
        simulator.setLoopMode(LoopMode.LOOP)
        simulator.setSpeed(100.0)

        val progressValues = mutableListOf<RouteProgress>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.routeProgress.collect { p -> p?.let { progressValues.add(it) } }
        }

        simulator.play(this)

        // Advance enough for two full passes
        repeat(20) { testScheduler.advanceTimeBy(300) }

        assertEquals(SimulationState.PLAYING, simulator.simulationState.value)
        assertTrue("LOOP should emit progress more than once", progressValues.size > 1)

        simulator.stop()
        job.cancel()
    }

    // ─── New: LoopMode.BOUNCE tests ───────────────────────────────────────────

    @Test
    fun `BOUNCE mode stays PLAYING and emits progress after first pass`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0)))
        simulator.setLoopMode(LoopMode.BOUNCE)
        simulator.setSpeed(100.0)

        val progressValues = mutableListOf<RouteProgress>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.routeProgress.collect { p -> p?.let { progressValues.add(it) } }
        }

        simulator.play(this)
        repeat(20) { testScheduler.advanceTimeBy(300) }

        assertEquals(SimulationState.PLAYING, simulator.simulationState.value)
        assertNotNull(simulator.routeProgress.value)

        simulator.stop()
        job.cancel()
    }

    @Test
    fun `BOUNCE stop clears progress`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.0001, 0.0)))
        simulator.setLoopMode(LoopMode.BOUNCE)
        simulator.setSpeed(100.0)
        simulator.play(this)
        repeat(5) { testScheduler.advanceTimeBy(300) }

        simulator.stop()

        assertNull(simulator.routeProgress.value)
        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
    }

    // ─── setRoute resets ──────────────────────────────────────────────────────

    @Test
    fun `setRoute while playing stops previous simulation`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0)))
        simulator.play(this)
        testScheduler.advanceTimeBy(200)

        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0)))

        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        assertNull(simulator.routeProgress.value)
    }

    // ─── Curvature-aware deceleration & jitter helpers ───────────────────────

    @Test
    fun `calculateAngleDiff handles wraparound`() {
        assertEquals(0f, RouteSimulator.calculateAngleDiff(0f, 0f), 0.001f)
        assertEquals(90f, RouteSimulator.calculateAngleDiff(0f, 90f), 0.001f)
        assertEquals(180f, RouteSimulator.calculateAngleDiff(0f, 180f), 0.001f)
        // 350° vs 10° is 20° apart, not 340°.
        assertEquals(20f, RouteSimulator.calculateAngleDiff(350f, 10f), 0.001f)
        assertEquals(20f, RouteSimulator.calculateAngleDiff(10f, 350f), 0.001f)
    }

    @Test
    fun `brakingSpeed leaves gentle bends untouched`() {
        // Below the 15° threshold there's no slowdown.
        assertEquals(60f, RouteSimulator.calculateBrakingSpeed(60f, 0f), 0.001f)
        assertEquals(60f, RouteSimulator.calculateBrakingSpeed(60f, 15f), 0.001f)
    }

    @Test
    fun `brakingSpeed caps mid corners at 18 kmh`() {
        // angleDiff > 70° caps at 18.
        assertTrue(RouteSimulator.calculateBrakingSpeed(60f, 80f) <= 18f)
        assertTrue(RouteSimulator.calculateBrakingSpeed(100f, 80f) <= 18f)
    }

    @Test
    fun `brakingSpeed caps sharp turns at 5 kmh`() {
        assertTrue(RouteSimulator.calculateBrakingSpeed(60f, 140f) <= 5f)
        assertTrue(RouteSimulator.calculateBrakingSpeed(100f, 180f) <= 5f)
    }

    @Test
    fun `brakingSpeed never falls below 3 kmh floor`() {
        // Even a U-turn at very low base speed should leave 3 km/h.
        assertEquals(3f, RouteSimulator.calculateBrakingSpeed(10f, 180f), 0.001f)
        assertEquals(3f, RouteSimulator.calculateBrakingSpeed(2f, 80f), 0.001f)
    }

    @Test
    fun `brakingSpeed cosine rolloff is monotonically decreasing in angle`() {
        val baseKmh = 100f
        val a = RouteSimulator.calculateBrakingSpeed(baseKmh, 20f)
        val b = RouteSimulator.calculateBrakingSpeed(baseKmh, 40f)
        val c = RouteSimulator.calculateBrakingSpeed(baseKmh, 60f)
        assertTrue("$a should be > $b at sharper angle", a > b)
        assertTrue("$b should be > $c at sharper angle", b > c)
    }

    @Test
    fun `jitterMultiplier stays in band`() {
        // Drift beyond the band must be clamped.
        assertEquals(1.15, RouteSimulator.calculateNextJitter(1.0, 0.5), 0.001)
        assertEquals(0.85, RouteSimulator.calculateNextJitter(1.0, -0.5), 0.001)
        // Already at the floor — pushing further down must stay at 0.85.
        assertEquals(0.85, RouteSimulator.calculateNextJitter(0.85, -0.1), 0.001)
        // Already at the ceiling — pushing further up must stay at 1.15.
        assertEquals(1.15, RouteSimulator.calculateNextJitter(1.15, 0.1), 0.001)
    }

    @Test
    fun `jitterMultiplier walks within step`() {
        val next = RouteSimulator.calculateNextJitter(1.0, 0.03)
        assertEquals(1.03, next, 0.001)
    }
}
