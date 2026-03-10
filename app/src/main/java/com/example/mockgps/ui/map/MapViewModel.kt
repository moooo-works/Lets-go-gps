package com.example.mockgps.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import com.example.mockgps.domain.repository.SettingsRepository
import com.example.mockgps.domain.repository.SearchRepository
import com.example.mockgps.domain.repository.GeocodedLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

enum class MapMode {
    SINGLE,
    ROUTE
}

data class MapUiState(
    val mapMode: MapMode = MapMode.SINGLE,
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
    val currentMockLocation: LatLng? = null,
    val routeFitRequestToken: Long? = null,
    val isSearching: Boolean = false,
    val searchResults: List<GeocodedLocation> = emptyList(),
    val searchError: String? = null,
    val isJoystickEnabled: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mockEngine: LocationMockEngine,
    private val repository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val searchRepository: SearchRepository,
    private val routeSimulator: RouteSimulator,
    private val joystickOverlayManager: JoystickOverlayManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var saveCenterJob: Job? = null
    private var joystickTickerJob: Job? = null
    private var isFirstLoad = true

    private var currentJoystickX = 0f
    private var currentJoystickY = 0f

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun setMapMode(mode: MapMode) {
        if (_uiState.value.mapMode == mode) return

        if (mode == MapMode.SINGLE) {
            // When switching to SINGLE, stop any active route simulation
            if (_uiState.value.simulationState != SimulationState.IDLE) {
                stopRoute()
            }
        }
        _uiState.update { it.copy(mapMode = mode) }
    }

    init {
        viewModelScope.launch {
            settingsRepository.observeLastCenter().collect { center ->
                if (isFirstLoad && center != null) {
                    _uiState.update { it.copy(centerLocation = center) }
                    isFirstLoad = false
                }
            }
        }

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
            routeSimulator.currentLocation.collect { point ->
                if (point != null) {
                    _uiState.update { it.copy(currentLocation = point.latLng) }
                }
            }
        }

        viewModelScope.launch {
            mockStateRepository.activeRouteWaypoints.collect { points ->
                _uiState.update { it.copy(waypoints = points) }
                // Also update simulator if not playing
                if (routeSimulator.simulationState.value == SimulationState.IDLE) {
                    routeSimulator.setRoute(points)
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

        saveCenterJob?.cancel()
        saveCenterJob = viewModelScope.launch {
            delay(500)
            settingsRepository.setLastCenter(latLng)
        }
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
fun toggleJoystick() {
    if (!_uiState.value.isJoystickEnabled) {
        if (!ensureFloatingWindowPermission()) return
        _uiState.update { it.copy(isJoystickEnabled = true) }
        startJoystickTicker()
        joystickOverlayManager.show {
            JoystickOverlayView(
                onMove = { dx, dy -> 
                    currentJoystickX = dx
                    currentJoystickY = dy
                },
                onWindowDrag = { dx, dy ->
                    joystickOverlayManager.updatePosition(dx, dy)
                }
            )
        }
    } else {
...

            _uiState.update { it.copy(isJoystickEnabled = false) }
            stopJoystickTicker()
            joystickOverlayManager.hide()
        }
    }

    private fun startJoystickTicker() {
        joystickTickerJob?.cancel()
        joystickTickerJob = viewModelScope.launch {
            while (true) {
                if (currentJoystickX != 0f || currentJoystickY != 0f) {
                    applyJoystickMovement(currentJoystickX, currentJoystickY)
                }
                delay(100) // 10Hz update rate
            }
        }
    }

    private fun stopJoystickTicker() {
        joystickTickerJob?.cancel()
        joystickTickerJob = null
        currentJoystickX = 0f
        currentJoystickY = 0f
    }

    private fun applyJoystickMovement(dx: Float, dy: Float) {
        val uiStateValue = _uiState.value
        val currentCenter = uiStateValue.centerLocation
        val speedKmh = uiStateValue.speedKmh
        
        // 1 km/h = 1000m / 3600s = 0.277m/s.
        // For 100ms (0.1s tick): 0.0277m per km/h.
        val metersPerTick = (speedKmh * 1000.0 / 3600.0) * 0.1
        val degreesPerTick = metersPerTick / 111000.0
        
        val latDelta = -dy * degreesPerTick
        val lngDelta = dx * degreesPerTick / kotlin.math.cos(Math.toRadians(currentCenter.latitude))
        
        val newCenter = LatLng(currentCenter.latitude + latDelta, currentCenter.longitude + lngDelta)
        
        // Use onCameraMove to ensure persistence and proper camera updates
        onCameraMove(newCenter)
        
        // Update Repository: This is the reactive trigger for MockLocationService
        if (uiStateValue.isMocking && uiStateValue.mapMode == MapMode.SINGLE) {
            mockStateRepository.setCurrentMockLocation(newCenter)
        }
    }

    private fun ensureFloatingWindowPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                setMockError(MockError.FloatingWindowPermissionMissing)
                return false
            }
        }
        return true
    }

    fun searchLocations(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            searchRepository.search(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(searchError = error.message, isSearching = false) }
                }
        }
    }

    fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
    }

    fun selectSearchResult(location: GeocodedLocation) {
        _uiState.update { it.copy(centerLocation = location.latLng, searchResults = emptyList()) }
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
        addWaypointAt(_uiState.value.centerLocation)
    }

    fun addWaypointAt(latLng: LatLng) {
        val newWaypoints = _uiState.value.waypoints + latLng
        _uiState.update { it.copy(waypoints = newWaypoints) }
        mockStateRepository.setActiveRouteWaypoints(newWaypoints)
        routeSimulator.setRoute(newWaypoints)
    }

    fun removeWaypointAt(index: Int) {
        val newWaypoints = _uiState.value.waypoints.toMutableList().apply { removeAt(index) }
        _uiState.update { it.copy(waypoints = newWaypoints) }
        mockStateRepository.setActiveRouteWaypoints(newWaypoints)
        routeSimulator.setRoute(newWaypoints)
    }

    fun clearRoute() {
        _uiState.update { it.copy(waypoints = emptyList(), currentMockLocation = null, currentLocation = null) }
        mockStateRepository.setActiveRouteWaypoints(emptyList())
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
            clearRoute()

            val points = route.points
                .sortedBy { it.orderIndex }
                .map { LatLng(it.latitude, it.longitude) }

            _uiState.update {
                it.copy(
                    waypoints = points,
                    centerLocation = points.firstOrNull() ?: it.centerLocation,
                    routeFitRequestToken = if (points.size >= 2) System.currentTimeMillis() else null
                )
            }
            mockStateRepository.setActiveRouteWaypoints(points)
            routeSimulator.setRoute(points)
        }
    }

    fun onRouteFitConsumed() {
        _uiState.update { it.copy(routeFitRequestToken = null) }
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
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            setMockError(MockError.LocationPermissionMissing)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                setMockError(MockError.NotificationPermissionMissing)
                return false
            }
        }

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
        stopJoystickTicker()
        joystickOverlayManager.hide()
        super.onCleared()
    }

    private companion object {
        const val KMH_TO_MPS_DIVISOR = 3.6
    }
}
