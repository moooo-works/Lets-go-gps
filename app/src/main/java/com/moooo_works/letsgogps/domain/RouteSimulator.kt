package com.moooo_works.letsgogps.domain

import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class SimulationState {
    IDLE, PLAYING, PAUSED
}

/**
 * Playback loop behaviour after reaching the end of a route.
 *
 * NONE   – stop at the final waypoint (default)
 * LOOP   – jump back to the first waypoint and replay
 * BOUNCE – reverse direction and traverse the route back to the start,
 *          then repeat indefinitely
 */
enum class LoopMode { NONE, LOOP, BOUNCE }

data class SimulationPoint(
    val latLng: LatLng,
    val bearing: Float,
    val speed: Float,
    val altitude: Double
)

/**
 * Represents the current playback progress of a route simulation.
 * [fraction] is in 0.0..1.0 for the *current leg* (forward or return).
 */
data class RouteProgress(
    val fraction: Float,
    val coveredKm: Double,
    val totalKm: Double
)

@Singleton
class RouteSimulator @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _currentLocation = MutableStateFlow<SimulationPoint?>(null)
    val currentLocation: StateFlow<SimulationPoint?> = _currentLocation.asStateFlow()

    private val _simulationState = MutableStateFlow(SimulationState.IDLE)
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private val _routeProgress = MutableStateFlow<RouteProgress?>(null)
    val routeProgress: StateFlow<RouteProgress?> = _routeProgress.asStateFlow()

    private var simulationJob: Job? = null
    private var waypoints: List<LatLng> = emptyList()
    private var speedMps: Double = DEFAULT_SPEED_MPS

    // Persisted for pause/resume across coroutine restarts
    private var currentSegmentIndex = 0
    private var distanceCoveredInSegment = 0.0

    /** Tracks whether we are on the return leg of a BOUNCE pass. */
    private var isReturning: Boolean = false

    private var loopMode: LoopMode = LoopMode.NONE

    // ─── Public API ───────────────────────────────────────────────────────────

    fun setRoute(points: List<LatLng>) {
        stop()
        waypoints = points
    }

    fun setSpeed(speed: Double) {
        speedMps = speed.coerceAtLeast(MIN_SPEED_MPS)
    }

    fun setLoopMode(mode: LoopMode) {
        loopMode = mode
    }

    fun play(scope: CoroutineScope) {
        if (waypoints.size < 2 || _simulationState.value == SimulationState.PLAYING) return

        _simulationState.value = SimulationState.PLAYING
        simulationJob = scope.launch {
            val baseAltitude = settingsRepository.observeAltitude().first()
            val useRandomAltitude = settingsRepository.observeRandomAltitude().first()

            fun getAltitude() = if (useRandomAltitude)
                baseAltitude + Random.nextDouble(-0.5, 0.5) else baseAltitude

            // Determine the effective waypoint list for this play() call.
            // If we are resuming a BOUNCE return leg, start from the reversed list.
            var effectiveWaypoints =
                if (isReturning) waypoints.reversed() else waypoints.toList()

            do {
                val segDistances = effectiveWaypoints
                    .zipWithNext { a, b -> calculateDistance(a, b) }
                val totalDistance = segDistances.sum()

                // Emit the very first point only at the start of each pass
                if (currentSegmentIndex == 0 && distanceCoveredInSegment == 0.0) {
                    val bearing = calculateBearing(effectiveWaypoints[0], effectiveWaypoints[1])
                    _currentLocation.emit(
                        SimulationPoint(effectiveWaypoints[0], bearing, speedMps.toFloat(), getAltitude())
                    )
                }

                // ── Main tick loop ────────────────────────────────────────────
                while (isActive && currentSegmentIndex < effectiveWaypoints.size - 1) {
                    val start = effectiveWaypoints[currentSegmentIndex]
                    val end = effectiveWaypoints[currentSegmentIndex + 1]
                    val segDist = segDistances.getOrElse(currentSegmentIndex) { 0.0 }

                    if (segDist <= 0.0) {
                        currentSegmentIndex++
                        distanceCoveredInSegment = 0.0
                        continue
                    }

                    val fraction = (distanceCoveredInSegment / segDist).coerceIn(0.0, 1.0)
                    val interpolated = interpolate(start, end, fraction)
                    val bearing = calculateBearing(start, end)

                    _currentLocation.emit(
                        SimulationPoint(interpolated, bearing, speedMps.toFloat(), getAltitude())
                    )

                    // Update progress
                    val covered = segDistances.take(currentSegmentIndex).sum() + distanceCoveredInSegment
                    _routeProgress.value = RouteProgress(
                        fraction = (covered / totalDistance).coerceIn(0.0, 1.0).toFloat(),
                        coveredKm = covered / 1000.0,
                        totalKm = totalDistance / 1000.0
                    )

                    delay(TICK_DELAY_MS)

                    distanceCoveredInSegment += speedMps * TICK_DELAY_MS / 1000.0
                    while (
                        currentSegmentIndex < effectiveWaypoints.size - 1 &&
                        distanceCoveredInSegment >= segDistances.getOrElse(currentSegmentIndex) { Double.MAX_VALUE }
                    ) {
                        distanceCoveredInSegment -= segDistances[currentSegmentIndex]
                        currentSegmentIndex++
                    }
                }

                // ── Emit final point of this pass ─────────────────────────────
                val lastPoint = effectiveWaypoints.lastOrNull()
                if (lastPoint != null) {
                    val bearing = if (effectiveWaypoints.size >= 2)
                        calculateBearing(effectiveWaypoints[effectiveWaypoints.size - 2], lastPoint)
                    else 0f
                    _currentLocation.emit(
                        SimulationPoint(lastPoint, bearing, 0f, getAltitude())
                    )
                    _routeProgress.value = RouteProgress(1f, totalDistance / 1000.0, totalDistance / 1000.0)
                }

                // ── Handle loop mode ──────────────────────────────────────────
                when (loopMode) {
                    LoopMode.LOOP -> {
                        // Restart from the beginning (original direction)
                        effectiveWaypoints = waypoints.toList()
                        isReturning = false
                        currentSegmentIndex = 0
                        distanceCoveredInSegment = 0.0
                    }
                    LoopMode.BOUNCE -> {
                        // Reverse for the next pass
                        effectiveWaypoints = effectiveWaypoints.reversed()
                        isReturning = !isReturning
                        currentSegmentIndex = 0
                        distanceCoveredInSegment = 0.0
                    }
                    LoopMode.NONE -> { /* fall through to end */ }
                }

            } while (isActive && loopMode != LoopMode.NONE && _simulationState.value == SimulationState.PLAYING)

            // Simulation finished (NONE mode or coroutine cancelled)
            _simulationState.value = SimulationState.IDLE
            resetProgress()
            _routeProgress.value = null
        }
    }

    fun pause() {
        simulationJob?.cancel()
        simulationJob = null
        if (_simulationState.value == SimulationState.PLAYING) {
            _simulationState.value = SimulationState.PAUSED
        }
        // routeProgress is intentionally kept so the UI shows the paused position
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        _simulationState.value = SimulationState.IDLE
        resetProgress()
        _currentLocation.value = null
        _routeProgress.value = null
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun resetProgress() {
        currentSegmentIndex = 0
        distanceCoveredInSegment = 0.0
        isReturning = false
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun interpolate(start: LatLng, end: LatLng, fraction: Double): LatLng {
        val lat = start.latitude + (end.latitude - start.latitude) * fraction
        val lng = start.longitude + (end.longitude - start.longitude) * fraction
        return LatLng(lat, lng)
    }

    private fun calculateBearing(start: LatLng, end: LatLng): Float {
        val startLat = Math.toRadians(start.latitude)
        val startLng = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLng = Math.toRadians(end.longitude)
        val dLng = endLng - startLng
        val y = sin(dLng) * cos(endLat)
        val x = cos(startLat) * sin(endLat) - sin(startLat) * cos(endLat) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return ((bearing + 360) % 360).toFloat()
    }

    private companion object {
        const val TICK_DELAY_MS = 250L
        const val DEFAULT_SPEED_MPS = 1.3888889 // ~5 km/h
        const val MIN_SPEED_MPS = 0.1
    }
}
