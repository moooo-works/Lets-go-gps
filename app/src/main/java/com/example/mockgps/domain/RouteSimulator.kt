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
import kotlin.math.*

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
    private var speedMps: Double = 5.0 // meters per second

    // State
    private var currentSegmentIndex = 0
    private var distanceCoveredInSegment = 0.0 // meters

    fun setRoute(points: List<LatLng>) {
        stop()
        this.waypoints = points
    }

    fun setSpeed(speed: Double) {
        this.speedMps = speed
    }

    fun play(scope: CoroutineScope) {
        if (waypoints.size < 2) return
        if (_simulationState.value == SimulationState.PLAYING) return

        _simulationState.value = SimulationState.PLAYING

        simulationJob = scope.launch {
            // Initial emission if starting fresh
            if (currentSegmentIndex == 0 && distanceCoveredInSegment == 0.0) {
                _currentLocation.emit(waypoints.first())
            }

            while (isActive && currentSegmentIndex < waypoints.size - 1) {
                val start = waypoints[currentSegmentIndex]
                val end = waypoints[currentSegmentIndex + 1]
                val segmentDistance = calculateDistance(start, end)

                if (distanceCoveredInSegment >= segmentDistance) {
                    // Move to next segment
                    distanceCoveredInSegment = 0.0
                    currentSegmentIndex++
                    if (currentSegmentIndex >= waypoints.size - 1) {
                        break // Finished
                    }
                    continue // Re-evaluate loop for next segment immediately? No, wait for next tick usually.
                    // But to be precise, we carry over excess distance?
                    // For simplicity MVP: just reset distanceCoveredInSegment and wait next tick.
                }

                // Calculate current position
                // Check if segment distance is 0 to avoid division by zero
                val fraction = if (segmentDistance > 0) distanceCoveredInSegment / segmentDistance else 1.0
                val newPos = interpolate(start, end, fraction)
                _currentLocation.emit(newPos)

                delay(1000) // 1 second tick

                distanceCoveredInSegment += speedMps
            }

            // Finished
            _simulationState.value = SimulationState.IDLE
            resetProgress()
        }
    }

    fun pause() {
        simulationJob?.cancel()
        if (_simulationState.value == SimulationState.PLAYING) {
            _simulationState.value = SimulationState.PAUSED
        }
    }

    fun stop() {
        simulationJob?.cancel()
        _simulationState.value = SimulationState.IDLE
        resetProgress()
        _currentLocation.value = null
    }

    private fun resetProgress() {
        currentSegmentIndex = 0
        distanceCoveredInSegment = 0.0
    }

    // Helper math functions
    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371000.0 // meters
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
}
