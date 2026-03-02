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
    val mockError: MockError? = null,
    val hasMockPermission: Boolean = false,
    val savedLocations: List<SavedLocation> = emptyList(),
    val waypoints: List<LatLng> = emptyList(),
    val simulationState: SimulationState = SimulationState.IDLE,
    val speedKmh: Double = 5.0,
    val transportMode: TransportMode = TransportMode.WALKING,
    val currentLocation: LatLng? = null
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
        viewModelScope.launch {
            repository.getAllLocations().collect { locations ->
                _uiState.update { it.copy(savedLocations = locations) }
            }
        }

        viewModelScope.launch {
            routeSimulator.simulationState.collect { state ->
                _uiState.update { it.copy(simulationState = state) }
            }
        }

        viewModelScope.launch {
            routeSimulator.currentLocation.collect { location ->
                if (location == null) return@collect

                _uiState.update { it.copy(currentLocation = location) }
                if (_uiState.value.hasMockPermission) {
                    try {
                        mockEngine.setLocation(location.latitude, location.longitude)
                    } catch (e: Exception) {
                        setMockError(MockError.SetLocationFailed("Failed to update simulated location: ${e.message}"))
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
        _uiState.update { it.copy(centerLocation = latLng) }
    }

    fun startMocking() {
        checkMockPermission()
        if (!_uiState.value.hasMockPermission) {
            setMockError(MockError.NotMockAppSelected)
            return
        }

        runCatching {
            mockEngine.setupMockProvider()
            val target = _uiState.value.centerLocation
            mockEngine.setLocation(target.latitude, target.longitude)
            _uiState.update { it.copy(isMocking = true, mockError = null) }
            saveLocationIfNeeded(target)
        }.onFailure { error ->
            handleEngineError(error)
        }
    }

    fun stopMocking() {
        runCatching {
            routeSimulator.stop()
            mockEngine.teardownMockProvider()
            _uiState.update { it.copy(isMocking = false) }
        }.onFailure { error ->
            _uiState.update { it.copy(isMocking = false) }
            // For stopMocking, we specifically want to detect teardown issues if any
            setMockError(MockError.ProviderTeardownFailed(error.message ?: "Unknown teardown error"))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(mockError = null) }
    }

    private fun saveLocationIfNeeded(latLng: LatLng) {
        viewModelScope.launch {
            val epsilon = 0.0001
            val exists = _uiState.value.savedLocations.any {
                kotlin.math.abs(it.latitude - latLng.latitude) < epsilon &&
                    kotlin.math.abs(it.longitude - latLng.longitude) < epsilon
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

    fun addWaypoint() {
        val currentCenter = _uiState.value.centerLocation
        val newWaypoints = _uiState.value.waypoints + currentCenter
        _uiState.update { it.copy(waypoints = newWaypoints) }
        routeSimulator.setRoute(newWaypoints)
    }

    fun saveRoute(name: String) {
        viewModelScope.launch {
            val route = com.example.mockgps.data.model.Route(
                name = name,
                defaultSpeed = _uiState.value.speedKmh,
                transportMode = _uiState.value.transportMode.name
            )
            val points = _uiState.value.waypoints.mapIndexed { index, latLng ->
                com.example.mockgps.data.model.RoutePoint(
                    routeId = 0, // Assigned by DAO
                    orderIndex = index,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )
            }
            repository.createRoute(route, points)
        }
    }

    fun loadRoute(points: List<com.example.mockgps.data.model.RoutePoint>, speed: Double) {
        val latLngs = points.sortedBy { it.orderIndex }.map { LatLng(it.latitude, it.longitude) }
        _uiState.update { it.copy(waypoints = latLngs, speedKmh = speed) }
        routeSimulator.setRoute(latLngs)
        routeSimulator.setSpeed(speed / KMH_TO_MPS_DIVISOR)
        if (latLngs.isNotEmpty()) {
            onCameraMove(latLngs.first())
        }
    }

    fun clearRoute() {
        _uiState.update { it.copy(waypoints = emptyList()) }
        routeSimulator.stop()
    }

    fun setTransportMode(mode: TransportMode) {
        _uiState.update { it.copy(transportMode = mode, speedKmh = mode.speedKmh) }
        routeSimulator.setSpeed(mode.speedKmh / KMH_TO_MPS_DIVISOR)
    }

    fun setSpeed(speedKmh: Double) {
        if (speedKmh <= 0.0) {
            setMockError(MockError.InvalidInput("Speed must be greater than 0 km/h"))
            return
        }
        _uiState.update { it.copy(speedKmh = speedKmh) }
        routeSimulator.setSpeed(speedKmh / KMH_TO_MPS_DIVISOR)
    }

    fun playRoute() {
        checkMockPermission()
        if (!_uiState.value.hasMockPermission) {
            setMockError(MockError.NotMockAppSelected)
            return
        }

        runCatching {
            mockEngine.setupMockProvider()
            _uiState.update { it.copy(isMocking = true, mockError = null) }
            routeSimulator.play(viewModelScope)
        }.onFailure { error ->
            handleEngineError(error)
        }
    }

    fun pauseRoute() {
        routeSimulator.pause()
    }

    fun stopRoute() {
        routeSimulator.stop()
    }

    private fun setMockError(error: MockError) {
        _uiState.update { it.copy(mockError = error) }
    }

    private fun handleEngineError(error: Throwable) {
        val refinedError = when {
             error is SecurityException -> MockError.ProviderSetupFailed("System rejected mock provider: ${error.message}")
             error is IllegalArgumentException -> MockError.ProviderSetupFailed("Invalid provider args: ${error.message}")
             else -> MockError.Unknown("Operation failed: ${error.message}")
        }
        setMockError(refinedError)
    }

    private companion object {
        const val KMH_TO_MPS_DIVISOR = 3.6
    }
}
