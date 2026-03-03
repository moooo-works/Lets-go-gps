package com.example.mockgps.ui.map

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.MockPermissionStatus
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.LocationRepository
import com.example.mockgps.domain.repository.MockStateRepository
import com.example.mockgps.domain.repository.MockStatus
import com.example.mockgps.service.MockLocationService
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.mockgps.data.engine.MockEngineError

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
    val currentLocation: LatLng? = null,
    val currentMockLocation: LatLng? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mockEngine: LocationMockEngine,
    private val repository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val routeSimulator: RouteSimulator,
    @ApplicationContext private val context: Context
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
            mockStateRepository.mockStatus.collect { status ->
                _uiState.update {
                    it.copy(
                        isMocking = status != MockStatus.IDLE,
                        simulationState = when (status) {
                            MockStatus.ROUTE_PLAYING -> SimulationState.PLAYING
                            MockStatus.ROUTE_PAUSED -> SimulationState.PAUSED
                            else -> SimulationState.IDLE
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            mockStateRepository.currentMockLocation.collect { location ->
                _uiState.update { it.copy(currentMockLocation = location) }
            }
        }

        viewModelScope.launch {
            routeSimulator.currentLocation.collect { location ->
                if (location != null) {
                    _uiState.update { it.copy(currentLocation = location) }
                }
            }
        }

        viewModelScope.launch {
            mockStateRepository.mockError.collect { error ->
                if (error != null) {
                    handleEngineError(error)
                }
            }
        }
    }

    private fun checkMockPermission(): MockPermissionStatus {
        val permissionStatus = mockEngine.getMockPermissionStatus()
        _uiState.update { it.copy(hasMockPermission = permissionStatus is MockPermissionStatus.Allowed) }
        return permissionStatus
    }

    fun refreshMockPermission() {
        checkMockPermission()
    }

    fun onCameraMove(latLng: LatLng) {
        _uiState.update { it.copy(centerLocation = latLng) }
    }

    fun startMocking() {
        if (!ensurePermission()) return

        val target = _uiState.value.centerLocation
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_SINGLE
            putExtra(MockLocationService.EXTRA_LAT, target.latitude)
            putExtra(MockLocationService.EXTRA_LNG, target.longitude)
        }
        ContextCompat.startForegroundService(context, intent)

        saveLocationIfNeeded(target)
    }

    fun stopMocking() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        context.startService(intent)
        _uiState.update { it.copy(currentLocation = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(mockError = null) }
        mockStateRepository.clearError()
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

    fun clearRoute() {
        _uiState.update { it.copy(waypoints = emptyList(), currentMockLocation = null, currentLocation = null) }
        stopMocking()
        routeSimulator.stop()
    }

    fun saveCurrentRoute(name: String) {
        val normalizedName = name.trim()
        val points = _uiState.value.waypoints
        if (normalizedName.isBlank() || normalizedName.length > 40 || points.size < 2) {
            return
        }

        viewModelScope.launch {
            repository.insertRouteWithPoints(
                normalizedName,
                points.mapIndexed { index, point ->
                    RoutePoint(
                        routeId = 0,
                        orderIndex = index,
                        latitude = point.latitude,
                        longitude = point.longitude
                    )
                }
            )
        }
    }

    fun loadRoute(routeId: Int) {
        viewModelScope.launch {
            val route = repository.getRouteWithPoints(routeId) ?: return@launch
            val points = route.points
                .sortedBy { it.orderIndex }
                .map { LatLng(it.latitude, it.longitude) }

            _uiState.update {
                it.copy(
                    waypoints = points,
                    centerLocation = points.firstOrNull() ?: it.centerLocation
                )
            }
            routeSimulator.setRoute(points)
        }
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
        if (!ensurePermission()) return

        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_ROUTE
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseRoute() {
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_PAUSE_ROUTE
        }
        context.startService(intent)
    }

    fun stopRoute() {
        stopMocking()
    }

    private fun ensurePermission(): Boolean {
        return when (val permissionStatus = checkMockPermission()) {
            MockPermissionStatus.Allowed -> true
            MockPermissionStatus.NotAllowed -> {
                setMockError(MockError.NotMockAppSelected)
                false
            }
            is MockPermissionStatus.CheckFailed -> {
                setMockError(MockError.PermissionCheckFailed(permissionStatus.cause.message ?: "Permission check failed"))
                false
            }
        }
    }

    private fun setMockError(error: MockError) {
        _uiState.update { it.copy(mockError = error) }
    }

    private fun handleEngineError(error: MockEngineError) {
        val refinedError = when (error) {
            is MockEngineError.Setup -> {
                val cause = error.cause
                if (cause is SecurityException) MockError.ProviderSetupFailed("System rejected mock provider: ${cause.message}")
                else if (cause is IllegalArgumentException) MockError.ProviderSetupFailed("Invalid provider args: ${cause.message}")
                else MockError.ProviderSetupFailed("Mock engine setup failed: ${cause.message}")
            }
            is MockEngineError.SetLocation -> MockError.SetLocationFailed(error.cause.message ?: "Failed to push mock location")
            is MockEngineError.Teardown -> MockError.Unknown("Operation failed: ${error.cause.message}")
            is MockEngineError.PermissionCheck -> MockError.PermissionCheckFailed(error.cause.message ?: "Permission check failed")
        }
        setMockError(refinedError)
    }

    override fun onCleared() {
        super.onCleared()
    }

    private companion object {
        const val KMH_TO_MPS_DIVISOR = 3.6
    }
}
