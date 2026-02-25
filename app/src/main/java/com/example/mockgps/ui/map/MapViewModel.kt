package com.example.mockgps.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.LocationRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TransportMode(val speedKmh: Double) {
    WALKING(5.0),
    CYCLING(15.0),
    DRIVING(40.0)
}

data class MapUiState(
    val isMocking: Boolean = false,
    val centerLocation: LatLng = LatLng(25.0330, 121.5654),
    val mockError: String? = null,
    val hasMockPermission: Boolean = false,
    val savedLocations: List<SavedLocation> = emptyList(),

    // Route features
    val waypoints: List<LatLng> = emptyList(),
    val simulationState: SimulationState = SimulationState.IDLE,
    val speedKmh: Double = 5.0,
    val transportMode: TransportMode = TransportMode.WALKING,
    val currentLocation: LatLng? = null // Current simulated location
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mockEngine: LocationMockEngine,
    private val repository: LocationRepository,
    private val routeSimulator: RouteSimulator
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        // Collect saved locations
        viewModelScope.launch {
            repository.getAllLocations().collect { locations ->
                _uiState.update { it.copy(savedLocations = locations) }
            }
        }

        // Collect simulation state
        viewModelScope.launch {
            routeSimulator.simulationState.collect { state ->
                _uiState.update { it.copy(simulationState = state) }
            }
        }

        // Collect simulated location
        viewModelScope.launch {
            routeSimulator.currentLocation.collect { location ->
                if (location != null) {
                    _uiState.update { it.copy(currentLocation = location) }
                    // Update mock engine if allowed
                    if (_uiState.value.hasMockPermission) {
                         try {
                             mockEngine.setLocation(location.latitude, location.longitude)
                         } catch (e: Exception) {
                             // Handle error
                         }
                    }
                }
            }
        }
    }

    fun checkMockPermission() {
        val allowed = mockEngine.isMockingAllowed()
        _uiState.update { it.copy(hasMockPermission = allowed) }
    }

    fun onCameraMove(latLng: LatLng) {
        // If we are simulating, we might not want to update center location to camera?
        // Usually center location is just what the crosshair points to.
        _uiState.update { it.copy(centerLocation = latLng) }
    }

    fun startMocking() {
        checkMockPermission()
        if (!_uiState.value.hasMockPermission) {
            _uiState.update { it.copy(mockError = "Please set this app as Mock Location App in Developer Options") }
            return
        }

        try {
            mockEngine.setupMockProvider()
            val target = _uiState.value.centerLocation
            mockEngine.setLocation(target.latitude, target.longitude)
            _uiState.update { it.copy(isMocking = true, mockError = null) }

            saveLocationIfNeeded(target)
        } catch (e: SecurityException) {
            _uiState.update { it.copy(mockError = "Permission denied: ${e.message}") }
        } catch (e: Exception) {
            _uiState.update { it.copy(mockError = "Error: ${e.message}") }
        }
    }

    fun stopMocking() {
        try {
            mockEngine.teardownMockProvider()
            _uiState.update { it.copy(isMocking = false) }
        } catch (e: Exception) {
             // Ignore
        }
    }

    fun clearError() {
        _uiState.update { it.copy(mockError = null) }
    }

    private fun saveLocationIfNeeded(latLng: LatLng) {
        viewModelScope.launch {
            val epsilon = 0.0001
            val exists = _uiState.value.savedLocations.any {
                Math.abs(it.latitude - latLng.latitude) < epsilon &&
                Math.abs(it.longitude - latLng.longitude) < epsilon
            }

            if (!exists) {
                val newLocation = SavedLocation(
                    name = "Saved ${System.currentTimeMillis()}",
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
                repository.saveLocation(newLocation)
            }
        }
    }

    // Route methods
    fun addWaypoint() {
        val currentCenter = _uiState.value.centerLocation
        val newWaypoints = _uiState.value.waypoints + currentCenter
        _uiState.update { it.copy(waypoints = newWaypoints) }
        routeSimulator.setRoute(newWaypoints)
    }

    fun clearRoute() {
        _uiState.update { it.copy(waypoints = emptyList()) }
        routeSimulator.stop()
    }

    fun setTransportMode(mode: TransportMode) {
        _uiState.update { it.copy(transportMode = mode, speedKmh = mode.speedKmh) }
        routeSimulator.setSpeed(mode.speedKmh / 3.6)
    }

    fun setSpeed(speedKmh: Double) {
        _uiState.update { it.copy(speedKmh = speedKmh) }
        routeSimulator.setSpeed(speedKmh / 3.6)
    }

    fun playRoute() {
        checkMockPermission()
         if (!_uiState.value.hasMockPermission) {
            _uiState.update { it.copy(mockError = "Please set this app as Mock Location App in Developer Options") }
            return
        }

        // Setup mock provider if not already
        try {
            mockEngine.setupMockProvider()
            _uiState.update { it.copy(isMocking = true) } // Route simulation implies mocking is active
            routeSimulator.play(viewModelScope)
        } catch (e: Exception) {
            _uiState.update { it.copy(mockError = "Error: ${e.message}") }
        }
    }

    fun pauseRoute() {
        routeSimulator.pause()
    }

    fun stopRoute() {
        routeSimulator.stop()
        // Should we stop mocking entirely?
        // Maybe keep mocking but stop movement.
        // Let's keep provider active but stop simulator.
    }
}
