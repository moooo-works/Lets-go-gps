package com.example.mockgps.domain

import com.example.mockgps.domain.repository.SettingsRepository
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

data class SimulationPoint(
    val latLng: LatLng,
    val bearing: Float,
    val speed: Float,
    val altitude: Double
)

@Singleton
class RouteSimulator @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val _currentLocation = MutableStateFlow<SimulationPoint?>(null)
    val currentLocation: StateFlow<SimulationPoint?> = _currentLocation.asStateFlow()

    private val _simulationState = MutableStateFlow(SimulationState.IDLE)
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private var simulationJob: Job? = null
    private var waypoints: List<LatLng> = emptyList()
    private var speedMps: Double = DEFAULT_SPEED_MPS

    private var currentSegmentIndex = 0
    private var distanceCoveredInSegment = 0.0

    fun setRoute(points: List<LatLng>) {
        stop()
        waypoints = points
    }

    fun setSpeed(speed: Double) {
        speedMps = speed.coerceAtLeast(MIN_SPEED_MPS)
    }

    fun play(scope: CoroutineScope) {
        if (waypoints.size < 2 || _simulationState.value == SimulationState.PLAYING) return

        _simulationState.value = SimulationState.PLAYING
        simulationJob = scope.launch {
            val baseAltitude = settingsRepository.observeAltitude().first()
            val useRandomAltitude = settingsRepository.observeRandomAltitude().first()
            val useJitter = settingsRepository.observeCoordinateJitter().first()

            fun getAltitude(): Double {
                return if (useRandomAltitude) {
                    baseAltitude + Random.nextDouble(-0.5, 0.5)
                } else {
                    baseAltitude
                }
            }

            fun applyJitter(point: LatLng): LatLng {
                return if (useJitter) {
                    // Approx 1-5 meters jitter (0.00001 deg is ~1.1m)
                    val latOffset = Random.nextDouble(-0.00003, 0.00003)
                    val lngOffset = Random.nextDouble(-0.00003, 0.00003)
                    LatLng(point.latitude + latOffset, point.longitude + lngOffset)
                } else {
                    point
                }
            }

            if (currentSegmentIndex == 0 && distanceCoveredInSegment == 0.0) {
                val start = waypoints[0]
                val next = waypoints[1]
                val bearing = calculateBearing(start, next)
                _currentLocation.emit(SimulationPoint(applyJitter(start), bearing, speedMps.toFloat(), getAltitude()))
            }

            while (isActive && currentSegmentIndex < waypoints.size - 1) {
                val start = waypoints[currentSegmentIndex]
                val end = waypoints[currentSegmentIndex + 1]
                val segmentDistance = calculateDistance(start, end)

                if (segmentDistance <= 0.0) {
                    currentSegmentIndex++
                    distanceCoveredInSegment = 0.0
                    continue
                }

                val fraction = (distanceCoveredInSegment / segmentDistance).coerceIn(0.0, 1.0)
                val interpolated = interpolate(start, end, fraction)
                val bearing = calculateBearing(start, end)
                
                _currentLocation.emit(SimulationPoint(applyJitter(interpolated), bearing, speedMps.toFloat(), getAltitude()))

                delay(TICK_DELAY_MS)

                distanceCoveredInSegment += speedMps
                while (distanceCoveredInSegment >= segmentDistance && currentSegmentIndex < waypoints.size - 1) {
                    distanceCoveredInSegment -= segmentDistance
                    currentSegmentIndex++
                    if (currentSegmentIndex >= waypoints.size - 1) {
                        break
                    }
                }
            }

            val lastPoint = waypoints.lastOrNull()
            if (lastPoint != null) {
                val bearing = if (waypoints.size >= 2) {
                    calculateBearing(waypoints[waypoints.size - 2], lastPoint)
                } else 0f
                _currentLocation.emit(SimulationPoint(applyJitter(lastPoint), bearing, 0f, getAltitude()))
            }
            
            _simulationState.value = SimulationState.IDLE
            resetProgress()
        }
    }

    fun pause() {
        simulationJob?.cancel()
        simulationJob = null
        if (_simulationState.value == SimulationState.PLAYING) {
            _simulationState.value = SimulationState.PAUSED
        }
    }

    fun stop() {
        simulationJob?.cancel()
        simulationJob = null
        _simulationState.value = SimulationState.IDLE
        resetProgress()
        _currentLocation.value = null
    }

    private fun resetProgress() {
        currentSegmentIndex = 0
        distanceCoveredInSegment = 0.0
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
        const val TICK_DELAY_MS = 1_000L
        const val DEFAULT_SPEED_MPS = 1.3888889 // ~5 km/h
        const val MIN_SPEED_MPS = 0.1
    }
}
