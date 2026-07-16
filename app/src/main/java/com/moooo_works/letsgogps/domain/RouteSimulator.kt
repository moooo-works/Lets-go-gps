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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
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

/**
 * How a route is traversed.
 *
 * WALK – interpolate along segments at the selected speed (default)
 * JUMP – teleport waypoint-to-waypoint every [RouteSimulator.currentJumpIntervalSec] seconds
 *        (for GPX tracks where the user wants to hop through points, not walk them)
 */
enum class RoutePlaybackMode { WALK, JUMP }

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

/**
 * Minimal playback progress needed to resume a route after process death.
 * Mirrors the private [RouteSimulator] fields that [RouteSimulator.play] reads
 * to continue from a mid-route position.
 */
data class RouteProgressSnapshot(
    val segmentIndex: Int,
    val distanceCoveredInSegment: Double,
    val isReturning: Boolean
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

    private var playbackMode: RoutePlaybackMode = RoutePlaybackMode.WALK
    private var jumpIntervalSec: Int = DEFAULT_JUMP_INTERVAL_SEC

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

    /** Current loop mode — exposed so the service can persist it for resume. */
    fun currentLoopMode(): LoopMode = loopMode

    fun setPlaybackMode(mode: RoutePlaybackMode) {
        playbackMode = mode
    }

    fun setJumpIntervalSec(sec: Int) {
        jumpIntervalSec = sec.coerceIn(MIN_JUMP_INTERVAL_SEC, MAX_JUMP_INTERVAL_SEC)
    }

    /** Current playback mode — exposed so the service can persist it for resume. */
    fun currentPlaybackMode(): RoutePlaybackMode = playbackMode

    /** Current jump interval (s) — exposed so the service can persist it for resume. */
    fun currentJumpIntervalSec(): Int = jumpIntervalSec

    /** Current speed (m/s) — exposed so the service can persist it for resume. */
    fun currentSpeedMps(): Double = speedMps

    /**
     * Snapshot the mid-route playback position so it can be persisted and later
     * fed back via [restoreProgress] after the process is killed.
     */
    fun snapshotProgress(): RouteProgressSnapshot =
        RouteProgressSnapshot(currentSegmentIndex, distanceCoveredInSegment, isReturning)

    /**
     * Restore a previously captured playback position. MUST be called AFTER
     * [setRoute] (which resets progress) and BEFORE [play] — [play] reads these
     * fields to continue from the mid-route position instead of the origin.
     */
    fun restoreProgress(snapshot: RouteProgressSnapshot) {
        currentSegmentIndex = snapshot.segmentIndex
        distanceCoveredInSegment = snapshot.distanceCoveredInSegment
        isReturning = snapshot.isReturning
    }

    /**
     * Walk an Archimedean spiral around [center] — radius grows from 0 toward
     * [maxRadiusMeters] over time, restarting at the inner ring when it reaches
     * the outer edge. Used by location-based games where the user wants to
     * "stay around home" but still register movement (egg hatching etc.).
     *
     * Emits SimulationPoint into [currentLocation] like [play] so MockLocationService
     * can pick them up via the same flow without any service-side change.
     */
    fun playExploration(
        scope: CoroutineScope,
        center: LatLng,
        maxRadiusMeters: Double = DEFAULT_EXPLORATION_RADIUS_M,
    ) {
        if (_simulationState.value == SimulationState.PLAYING) return
        _simulationState.value = SimulationState.PLAYING
        simulationJob = scope.launch {
            val baseAltitude = settingsRepository.observeAltitude().first()
            val useRandomAltitude = settingsRepository.observeRandomAltitude().first()
            fun getAltitude() = if (useRandomAltitude)
                baseAltitude + Random.nextDouble(-0.5, 0.5) else baseAltitude

            spiralLoop(center, maxRadiusMeters, durationMs = null) { pos, bearing ->
                _currentLocation.emit(SimulationPoint(pos, bearing, speedMps.toFloat(), getAltitude()))
            }
            _simulationState.value = SimulationState.IDLE
        }
    }

    /**
     * Teleport between [targets] and spiral-explore each for [dwellSec] seconds,
     * with a [cooldownSec] pause after each teleport (lets some anti-cheat heuristics
     * settle on "user just landed here, GPS warming up"). Loops indefinitely.
     */
    fun playTeleportExploration(
        scope: CoroutineScope,
        targets: List<LatLng>,
        dwellSec: Int = DEFAULT_TELEPORT_DWELL_SEC,
        cooldownSec: Int = DEFAULT_TELEPORT_COOLDOWN_SEC,
        radiusMeters: Double = DEFAULT_EXPLORATION_RADIUS_M,
    ) {
        if (targets.isEmpty()) return
        if (_simulationState.value == SimulationState.PLAYING) return
        _simulationState.value = SimulationState.PLAYING
        simulationJob = scope.launch {
            val baseAltitude = settingsRepository.observeAltitude().first()
            val useRandomAltitude = settingsRepository.observeRandomAltitude().first()
            fun getAltitude() = if (useRandomAltitude)
                baseAltitude + Random.nextDouble(-0.5, 0.5) else baseAltitude

            while (isActive && _simulationState.value == SimulationState.PLAYING) {
                for (target in targets) {
                    if (!isActive) break
                    // Teleport — single point at the new location, speed 0 to
                    // signal "we just arrived, no movement for the moment".
                    _currentLocation.emit(SimulationPoint(target, 0f, 0f, getAltitude()))
                    delay(cooldownSec * 1000L)

                    // Dwell-spiral for dwellSec.
                    spiralLoop(target, radiusMeters, durationMs = dwellSec * 1000L) { pos, bearing ->
                        _currentLocation.emit(SimulationPoint(pos, bearing, speedMps.toFloat(), getAltitude()))
                    }
                }
            }
            _simulationState.value = SimulationState.IDLE
        }
    }

    /**
     * Inline helper — runs a spiral around [center] until [durationMs] elapses
     * (or forever if null). Each tick computes the next position via
     * [calculateSpiralPosition] and emits via [emit].
     */
    private suspend inline fun spiralLoop(
        center: LatLng,
        maxRadiusMeters: Double,
        durationMs: Long?,
        crossinline emit: suspend (LatLng, Float) -> Unit,
    ) {
        val deadline = durationMs?.let { System.currentTimeMillis() + it }
        // Tunables: a full revolution every ~15s; one expand-then-contract
        // cycle takes ~2 min (4 min for full coverage repeat).
        val angularStep = 2.0 * Math.PI / 60.0
        val radialStep = maxRadiusMeters / 240.0
        var angleRadians = 0.0
        var radius = 0.0
        // Triangle-wave radius: expand 0→max, then contract max→0, then repeat.
        // Avoids the 200m teleport an instant reset would create — location-based
        // games (Pikmin Bloom etc.) flag that jump as cheating and freeze the avatar.
        var expanding = true
        var lastPos: LatLng? = null
        while (true) {
            if (deadline != null && System.currentTimeMillis() >= deadline) break
            val pos = calculateSpiralPosition(center.latitude, center.longitude, radius, angleRadians)
            val bearing = lastPos?.let { calculateBearing(it, pos) } ?: 0f
            emit(pos, bearing)
            lastPos = pos
            // delay() throws CancellationException when the parent Job is
            // cancelled (e.g. user calls stop()) — that escapes the loop
            // naturally without an isActive check on every iteration.
            delay(TICK_DELAY_MS)
            angleRadians += angularStep
            if (expanding) {
                radius += radialStep
                if (radius >= maxRadiusMeters) {
                    radius = maxRadiusMeters
                    expanding = false
                }
            } else {
                radius -= radialStep
                if (radius <= 0.0) {
                    radius = 0.0
                    expanding = true
                }
            }
        }
    }

    fun play(scope: CoroutineScope) {
        if (waypoints.size < 2 || _simulationState.value == SimulationState.PLAYING) return

        _simulationState.value = SimulationState.PLAYING
        simulationJob = scope.launch {
            val baseAltitude = settingsRepository.observeAltitude().first()
            val useRandomAltitude = settingsRepository.observeRandomAltitude().first()
            val routeCornerSlowdown = settingsRepository.observeRouteCornerSlowdown().first()

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
                if (playbackMode == RoutePlaybackMode.JUMP) {
                    // Teleport waypoint-to-waypoint every jumpIntervalSec.
                    // ponytail: no keep-alive re-emit inside the interval — capped at
                    // MAX_JUMP_INTERVAL_SEC (10s), a fix every ≤10s is fine downstream.
                    while (isActive && currentSegmentIndex < effectiveWaypoints.size - 1) {
                        delay(jumpIntervalSec * 1000L)
                        val from = effectiveWaypoints[currentSegmentIndex]
                        currentSegmentIndex++
                        distanceCoveredInSegment = 0.0
                        val point = effectiveWaypoints[currentSegmentIndex]
                        // speed 0 — we just teleported (mirrors playTeleportExploration)
                        _currentLocation.emit(
                            SimulationPoint(point, calculateBearing(from, point), 0f, getAltitude())
                        )
                        if (totalDistance > 0.0) {
                            val covered = segDistances.take(currentSegmentIndex).sum()
                            _routeProgress.value = RouteProgress(
                                fraction = (covered / totalDistance).coerceIn(0.0, 1.0).toFloat(),
                                coveredKm = covered / 1000.0,
                                totalKm = totalDistance / 1000.0
                            )
                        }
                    }
                    // Dwell on the final point for one interval before a LOOP/BOUNCE
                    // pass resets — without this the reset re-emits the first waypoint
                    // in the same tick and the StateFlow conflates the final point
                    // away entirely (2-waypoint routes looked stuck on the start).
                    if (loopMode != LoopMode.NONE) delay(jumpIntervalSec * 1000L)
                } else while (isActive && currentSegmentIndex < effectiveWaypoints.size - 1) {
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

                    val effectiveMps = if (routeCornerSlowdown) {
                        val nextBearing = if (currentSegmentIndex + 2 < effectiveWaypoints.size) {
                            calculateBearing(
                                effectiveWaypoints[currentSegmentIndex + 1],
                                effectiveWaypoints[currentSegmentIndex + 2]
                            )
                        } else {
                            null
                        }
                        val angleDiff = if (nextBearing != null) calculateAngleDiff(bearing, nextBearing) else 0f
                        calculateBrakingSpeed((speedMps * 3.6).toFloat(), angleDiff) / 3.6
                    } else {
                        speedMps
                    }

                    distanceCoveredInSegment += effectiveMps * TICK_DELAY_MS / 1000.0
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

    companion object {
        const val TICK_DELAY_MS = 250L
        const val DEFAULT_SPEED_MPS = 1.3888889 // ~5 km/h
        const val MIN_SPEED_MPS = 0.1

        // Exploration / teleport-exploration defaults — kept here so they
        // are easy to tweak from one place and to surface as future settings.
        const val DEFAULT_JUMP_INTERVAL_SEC = 3
        const val MIN_JUMP_INTERVAL_SEC = 1
        const val MAX_JUMP_INTERVAL_SEC = 10

        const val DEFAULT_EXPLORATION_RADIUS_M = 200.0
        const val DEFAULT_TELEPORT_DWELL_SEC = 60
        const val DEFAULT_TELEPORT_COOLDOWN_SEC = 5

        /**
         * Translates a polar offset (radius_meters, angle_radians) anchored at
         * (centerLat, centerLng) to a LatLng. 111320m ≈ 1° latitude; longitude
         * is scaled by cos(lat) since meridians converge at the poles.
         */
        fun calculateSpiralPosition(
            centerLat: Double,
            centerLng: Double,
            radiusMeters: Double,
            angleRadians: Double,
        ): LatLng {
            val dLat = (radiusMeters * cos(angleRadians)) / 111_320.0
            val dLng = (radiusMeters * sin(angleRadians)) /
                (cos(Math.toRadians(centerLat)) * 111_320.0)
            return LatLng(centerLat + dLat, centerLng + dLng)
        }

        /** Smallest absolute angular difference between two bearings (deg, 0..180). */
        fun calculateAngleDiff(a: Float, b: Float): Float {
            val diff = abs(a - b)
            return if (diff > 180f) 360f - diff else diff
        }

        /**
         * Optional slowdown for upcoming corners. The default route playback path
         * bypasses this so selected km/h remains accurate unless the setting is on.
         */
        fun calculateBrakingSpeed(baseKmh: Float, angleDiff: Float): Float {
            if (angleDiff <= 15f) return baseKmh
            val clampedAngle = angleDiff.coerceIn(0f, 180f)
            val rad = (clampedAngle * Math.PI / 180.0) / 2.0
            var speed = baseKmh * cos(rad).pow(2.0).toFloat()
            if (angleDiff > 130f) speed = speed.coerceAtMost(5f)
            else if (angleDiff > 70f) speed = speed.coerceAtMost(18f)
            return speed.coerceAtLeast(3f)
        }

    }
}
