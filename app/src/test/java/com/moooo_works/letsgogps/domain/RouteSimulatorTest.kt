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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSimulatorTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setup() {
        every { settingsRepository.observeAltitude() } returns flowOf(15.0)
        every { settingsRepository.observeRandomAltitude() } returns flowOf(false)
        every { settingsRepository.observeCoordinateJitter() } returns flowOf(false)
        every { settingsRepository.observeRouteCornerSlowdown() } returns flowOf(false)
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

        val routeNorth = listOf(LatLng(0.0, 0.0), LatLng(0.000001, 0.0))
        simulator.setRoute(routeNorth)
        simulator.setSpeed(1000.0)

        val emittedPoints = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emittedPoints.add(it) }
        }

        simulator.play(this)
        advanceUntilIdle()

        val point = emittedPoints.filterNotNull().first()
        assertEquals(0f, point.bearing, 0.1f)

        val routeEast = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.000001))
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

    @Test
    fun `route playback advances at selected speed through corners`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val route = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.01, 0.0),
            LatLng(0.01, 0.01)
        )
        simulator.setRoute(route)
        simulator.setSpeed(5.0) // 18 km/h

        simulator.play(this)
        testScheduler.advanceTimeBy(10_250)

        val progress = simulator.routeProgress.value
        assertNotNull(progress)
        assertEquals(0.05, progress!!.coveredKm, 0.003)

        simulator.stop()
    }

    @Test
    fun `route corner slowdown reduces progress when enabled`() = runTest {
        every { settingsRepository.observeRouteCornerSlowdown() } returns flowOf(true)
        val simulator = RouteSimulator(settingsRepository)
        val route = listOf(
            LatLng(0.0, 0.0),
            LatLng(0.01, 0.0),
            LatLng(0.01, 0.01)
        )
        simulator.setRoute(route)
        simulator.setSpeed(5.0) // 18 km/h

        simulator.play(this)
        testScheduler.advanceTimeBy(10_250)

        val progress = simulator.routeProgress.value
        assertNotNull(progress)
        assertTrue("coveredKm=${progress!!.coveredKm}", progress.coveredKm < 0.04)

        simulator.stop()
    }

    // ─── New: LoopMode.NONE tests ─────────────────────────────────────────────

    @Test
    fun `NONE mode simulator is IDLE and progress null after route completes`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val end = LatLng(0.0001, 0.0)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), end))
        simulator.setLoopMode(LoopMode.NONE)
        simulator.setSpeed(100.0)
        simulator.play(this)
        advanceUntilIdle()

        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        assertNull(simulator.routeProgress.value)
        assertEquals(end, simulator.currentLocation.value?.latLng)
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

    // ─── RoutePlaybackMode.JUMP ───────────────────────────────────────────────

    @Test
    fun `JUMP mode teleports waypoint-to-waypoint at the set interval`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.01, 0.0)
        val c = LatLng(0.01, 0.01)
        simulator.setRoute(listOf(a, b, c))
        simulator.setPlaybackMode(RoutePlaybackMode.JUMP)
        simulator.setJumpIntervalSec(2)

        val emitted = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emitted.add(it) }
        }

        simulator.play(this)
        testScheduler.advanceTimeBy(1_000)
        // Before the first interval elapses only the start point exists — no interpolation.
        assertEquals(listOf(a), emitted.filterNotNull().map { it.latLng })

        testScheduler.advanceTimeBy(1_100) // t≈2.1s → first jump landed on b
        assertEquals(b, emitted.filterNotNull().last().latLng)

        advanceUntilIdle()
        assertEquals(listOf(a, b, c), emitted.filterNotNull().map { it.latLng })
        assertEquals(SimulationState.IDLE, simulator.simulationState.value)
        job.cancel()
    }

    @Test
    fun `JUMP mode with LOOP restarts from the first waypoint and stays PLAYING`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.01, 0.0)
        simulator.setRoute(listOf(a, b))
        simulator.setPlaybackMode(RoutePlaybackMode.JUMP)
        simulator.setJumpIntervalSec(1)
        simulator.setLoopMode(LoopMode.LOOP)

        val positions = mutableListOf<LatLng>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { it?.let { p -> positions.add(p.latLng) } }
        }

        simulator.play(this)
        testScheduler.advanceTimeBy(3_500)

        assertEquals(SimulationState.PLAYING, simulator.simulationState.value)
        assertTrue("expected a second pass, got $positions", positions.size >= 3)
        assertEquals(listOf(a, b, a), positions.take(3))

        simulator.stop()
        job.cancel()
    }

    @Test
    fun `jump interval is clamped to its bounds`() {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setJumpIntervalSec(99)
        assertEquals(RouteSimulator.MAX_JUMP_INTERVAL_SEC, simulator.currentJumpIntervalSec())
        simulator.setJumpIntervalSec(0)
        assertEquals(RouteSimulator.MIN_JUMP_INTERVAL_SEC, simulator.currentJumpIntervalSec())
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

    // ─── Progress snapshot / restore (process-death resume) ───────────────────

    @Test
    fun `restoreProgress makes play resume from the given segment`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.01, 0.0)
        val c = LatLng(0.01, 0.01)
        simulator.setRoute(listOf(a, b, c))
        simulator.setSpeed(5.0)

        // Resume from the start of segment 1 (waypoint b), not from a.
        simulator.restoreProgress(RouteProgressSnapshot(segmentIndex = 1, distanceCoveredInSegment = 0.0, isReturning = false))

        val emitted = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { emitted.add(it) }
        }

        simulator.play(this)
        testScheduler.advanceTimeBy(100)

        val firstPoint = emitted.filterNotNull().first()
        assertEquals("should resume at waypoint b latitude", b.latitude, firstPoint.latLng.latitude, 1e-6)
        assertEquals("should resume at waypoint b longitude", b.longitude, firstPoint.latLng.longitude, 1e-6)

        simulator.stop()
        job.cancel()
    }

    @Test
    fun `snapshotProgress reflects advancing playback`() = runTest {
        val simulator = RouteSimulator(settingsRepository)
        simulator.setRoute(listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0), LatLng(0.02, 0.0)))
        simulator.setSpeed(50.0)

        simulator.play(this)
        testScheduler.advanceTimeBy(5_000)

        val snap = simulator.snapshotProgress()
        simulator.stop()

        assertTrue(
            "snapshot should have advanced past the start (seg=${snap.segmentIndex}, dist=${snap.distanceCoveredInSegment})",
            snap.segmentIndex > 0 || snap.distanceCoveredInSegment > 0.0
        )
    }

    @Test
    fun `snapshot then restore on a fresh simulator resumes mid-route`() = runTest {
        // Simulates process death: snapshot progress, throw the simulator away,
        // rebuild a fresh one, restore, and confirm it does NOT restart at the origin.
        val route = listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.0), LatLng(0.01, 0.01))

        val first = RouteSimulator(settingsRepository)
        first.setRoute(route)
        first.setSpeed(50.0)
        first.play(this)
        testScheduler.advanceTimeBy(3_000)
        val snap = first.snapshotProgress()
        first.stop()
        assertTrue("precondition: progress advanced", snap.segmentIndex > 0 || snap.distanceCoveredInSegment > 0.0)

        val fresh = RouteSimulator(settingsRepository)
        fresh.setRoute(route)
        fresh.setSpeed(50.0)
        fresh.restoreProgress(snap)

        val emitted = mutableListOf<SimulationPoint?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fresh.currentLocation.collect { emitted.add(it) }
        }
        fresh.play(this)
        testScheduler.advanceTimeBy(50)

        val firstPoint = emitted.filterNotNull().first()
        assertTrue(
            "fresh simulator must resume past origin, got ${firstPoint.latLng}",
            firstPoint.latLng.latitude > 0.0
        )

        fresh.stop()
        job.cancel()
    }

    // ─── Spiral position math (exploration / teleport-exploration) ───────────

    @Test
    fun `brakingSpeed caps mid corners at 18 kmh`() {
        assertTrue(RouteSimulator.calculateBrakingSpeed(60f, 80f) <= 18f)
        assertTrue(RouteSimulator.calculateBrakingSpeed(100f, 80f) <= 18f)
    }

    @Test
    fun `brakingSpeed caps sharp turns at 5 kmh`() {
        assertTrue(RouteSimulator.calculateBrakingSpeed(60f, 140f) <= 5f)
        assertTrue(RouteSimulator.calculateBrakingSpeed(100f, 180f) <= 5f)
    }

    @Test
    fun `spiralPosition at zero radius is the centre`() {
        val out = RouteSimulator.calculateSpiralPosition(25.0, 121.0, 0.0, 0.0)
        assertEquals(25.0, out.latitude, 1e-9)
        assertEquals(121.0, out.longitude, 1e-9)
    }

    @Test
    fun `spiralPosition at angle 0 advances north by ~radius_meters_111320`() {
        // 100m offset due north should be 100/111320 ≈ 0.0008983° in latitude.
        val out = RouteSimulator.calculateSpiralPosition(0.0, 0.0, 100.0, 0.0)
        assertEquals(100.0 / 111_320.0, out.latitude, 1e-9)
        // East offset at angle 0 is zero — sin(0) = 0.
        assertEquals(0.0, out.longitude, 1e-12)
    }

    @Test
    fun `spiralPosition longitude scales with cos of latitude`() {
        // 100m east at the equator vs at lat=60°: longitude offset at 60°
        // should be exactly 2× the equator offset (1/cos(60°) = 2).
        val equator = RouteSimulator.calculateSpiralPosition(0.0, 0.0, 100.0, Math.PI / 2)
        val sixtyN = RouteSimulator.calculateSpiralPosition(60.0, 0.0, 100.0, Math.PI / 2)
        // Sanity: latitude offset is ~zero at angle = π/2.
        assertEquals(0.0, equator.latitude, 1e-12)
        // The longitude shift at 60°N is twice the shift at the equator.
        assertEquals(2.0, sixtyN.longitude / equator.longitude, 1e-6)
    }

    @Test
    fun `spiralPosition full revolution returns to ~origin offset`() {
        // Going round 360° at the same radius should land back where we started
        // (offset wise) — sin/cos return to start.
        val a = RouteSimulator.calculateSpiralPosition(25.0, 121.0, 50.0, 0.0)
        val b = RouteSimulator.calculateSpiralPosition(25.0, 121.0, 50.0, 2.0 * Math.PI)
        assertEquals(a.latitude, b.latitude, 1e-12)
        assertEquals(a.longitude, b.longitude, 1e-12)
    }

    // ─── Spiral loop continuity (regression: no 200m wraparound jump) ─────────

    @Test
    fun `playExploration never jumps more than one tick of travel between emits`() = runTest {
        val simulator = RouteSimulator(settingsRepository)

        val emittedPositions = mutableListOf<LatLng>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect { it?.let { p -> emittedPositions.add(p.latLng) } }
        }

        val center = LatLng(25.0, 121.0)
        simulator.playExploration(this, center, maxRadiusMeters = 200.0)

        // Spiral reaches max radius around tick 240 (~60s). Pre-fix, the next
        // tick resets radius to 0 — Pikmin Bloom flags the resulting 200m
        // position jump as teleportation and freezes the character.
        // Run for ~300 ticks to cover the wrap.
        testScheduler.advanceTimeBy(75_000)

        simulator.stop()
        job.cancel()

        val jumps = emittedPositions.zipWithNext { a, b -> haversineMeters(a, b) }
        val maxJump = jumps.maxOrNull() ?: 0.0

        // Normal max tick-to-tick chord at r=200m, angularStep=2π/60 is ~21m.
        // The wraparound bug produces a single ~200m jump.
        assertTrue(
            "Max tick-to-tick jump was ${"%.1f".format(maxJump)}m (out of ${jumps.size} ticks); " +
                "spiral radius reset causes a 200m teleport. Expected < 30m.",
            maxJump < 30.0
        )
    }

    @Test
    fun `playExploration radius contracts smoothly without resetting to centre`() = runTest {
        val simulator = RouteSimulator(settingsRepository)

        val center = LatLng(25.0, 121.0)
        val distancesFromCenter = mutableListOf<Double>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            simulator.currentLocation.collect {
                it?.let { p -> distancesFromCenter.add(haversineMeters(center, p.latLng)) }
            }
        }

        simulator.playExploration(this, center, maxRadiusMeters = 200.0)
        testScheduler.advanceTimeBy(75_000)

        simulator.stop()
        job.cancel()

        // Pre-fix: radius resets 200m → 0 in one tick. Post-fix: radius shrinks
        // by ~radialStep (~0.83m) per tick during the contract phase.
        val biggestDropPerTick = distancesFromCenter
            .zipWithNext { a, b -> a - b }
            .maxOrNull() ?: 0.0

        assertTrue(
            "Largest single-tick drop in distance-from-centre was " +
                "${"%.1f".format(biggestDropPerTick)}m. The wraparound bug drops ~200m " +
                "in one tick; smooth contract should drop < 5m.",
            biggestDropPerTick < 5.0
        )
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(dLng / 2) * sin(dLng / 2)
        return earthRadius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
