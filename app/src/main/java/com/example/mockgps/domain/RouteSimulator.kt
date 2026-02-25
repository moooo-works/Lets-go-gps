package com.example.mockgps.domain

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SimulationState {
    IDLE, PLAYING, PAUSED
}

@Singleton
class RouteSimulator @Inject constructor() {
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

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
            if (currentSegmentIndex == 0 && distanceCoveredInSegment == 0.0) {
                _currentLocation.emit(waypoints.first())
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
                _currentLocation.emit(interpolate(start, end, fraction))

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

            _currentLocation.emit(waypoints.lastOrNull())
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

    private companion object {
        const val TICK_DELAY_MS = 1_000L
        const val DEFAULT_SPEED_MPS = 5.0
        const val MIN_SPEED_MPS = 0.1
    }
}
