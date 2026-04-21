package com.moooo_works.letsgogps.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.service.MockLocationService
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.moooo_works.letsgogps.data.engine.MockEngineError
import com.moooo_works.letsgogps.domain.repository.ProRepository
import android.app.Activity

// State definitions are in MapState.kt

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mockEngine: LocationMockEngine,
    private val repository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val settingsRepository: SettingsRepository,
    private val routeSimulator: RouteSimulator,
    private val joystickOverlayManager: JoystickOverlayManager,
    private val proRepository: ProRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var saveCenterJob: Job? = null
    private var joystickTickerJob: Job? = null
    private var isFirstLoad = true

    private var currentJoystickX = 0f
    private var currentJoystickY = 0f

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _triggerReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerReview: SharedFlow<Unit> = _triggerReview.asSharedFlow()

    fun setMapMode(mode: MapMode) {
        if (_uiState.value.mapMode == mode) return

        if (mode == MapMode.SINGLE) {
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
                        mapMode = when (status) {
                            MockStatus.ROUTE_PLAYING, MockStatus.ROUTE_PAUSED -> MapMode.ROUTE
                            else -> it.mapMode
                        },
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
            settingsRepository.observeRouteSpeed().collect { speed ->
                val mode = TransportMode.values().find { it.speedKmh == speed }
                _uiState.update { it.copy(
                    speedKmh = speed,
                    transportMode = mode ?: it.transportMode
                ) }
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

        viewModelScope.launch {
            proRepository.isProActive.collect { isPro ->
                _uiState.update { it.copy(isProActive = isPro) }
            }
        }

        viewModelScope.launch {
            settingsRepository.observeMapType().collect { typeName ->
                val type = if (typeName == "HYBRID") MapType.HYBRID else MapType.NORMAL
                _uiState.update { it.copy(mapType = type) }
            }
        }

        viewModelScope.launch {
            settingsRepository.hasSeenOnboarding().collect { seen ->
                if (!seen) _uiState.update { it.copy(showOnboarding = true) }
            }
        }
    }

    fun dismissOnboarding() {
        _uiState.update { it.copy(showOnboarding = false) }
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
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
        checkAndTriggerReview()
    }

    private fun checkAndTriggerReview() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REVIEW_SHOWN, false)) return
        val count = prefs.getInt(KEY_MOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_MOCK_COUNT, count).apply()
        if (count >= REVIEW_TRIGGER_COUNT) {
            prefs.edit().putBoolean(KEY_REVIEW_SHOWN, true).apply()
            viewModelScope.launch { _triggerReview.emit(Unit) }
        }
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
            if (!_uiState.value.isProActive) {
                _uiState.update { it.copy(showProUpgrade = true) }
                return
            }
            if (!ensureFloatingWindowPermission()) return
            _uiState.update { it.copy(isJoystickEnabled = true) }
            startJoystickTicker()
            joystickOverlayManager.show {
                val state by uiState.collectAsState()
                JoystickOverlayView(
                    transportMode = state.transportMode,
                    onMove = { dx, dy -> 
                        currentJoystickX = dx
                        currentJoystickY = dy
                    },
                    onWindowDrag = { dx, dy ->
                        joystickOverlayManager.updatePosition(dx, dy)
                    },
                    onWindowDragEnd = {
                        joystickOverlayManager.snapToEdge()
                    },
                    onToggleSpeed = { cycleTransportMode() },
                    onStop = { stopMockingFromJoystick() }
                )
            }
        } else {
            _uiState.update { it.copy(isJoystickEnabled = false) }
            stopJoystickTicker()
            joystickOverlayManager.hide()
        }
    }

    private fun cycleTransportMode() {
        val nextMode = when (_uiState.value.transportMode) {
            TransportMode.WALKING -> TransportMode.CYCLING
            TransportMode.CYCLING -> TransportMode.DRIVING
            TransportMode.DRIVING -> TransportMode.WALKING
        }
        setTransportMode(nextMode)
    }

    private fun stopMockingFromJoystick() {
        stopMocking()
        if (_uiState.value.isJoystickEnabled) {
            toggleJoystick()
        }
    }

    private fun startJoystickTicker() {
        joystickTickerJob?.cancel()
        joystickTickerJob = viewModelScope.launch {
            while (true) {
                if (currentJoystickX != 0f || currentJoystickY != 0f) {
                    applyJoystickMovement(currentJoystickX, currentJoystickY)
                }
                delay(100) 
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
        
        val metersPerTick = (speedKmh * 1000.0 / 3600.0) * 0.1
        val degreesPerTick = metersPerTick / 111000.0
        
        val latDelta = -dy * degreesPerTick
        val cosLat = kotlin.math.cos(Math.toRadians(currentCenter.latitude)).coerceAtLeast(0.001)
        val lngDelta = dx * degreesPerTick / cosLat
        
        val newCenter = LatLng(currentCenter.latitude + latDelta, currentCenter.longitude + lngDelta)
        onCameraMove(newCenter)
        
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

    fun selectSearchResult(location: GeocodedLocation) {
        _uiState.update { it.copy(centerLocation = location.latLng) }
    }

    fun showProUpgradeDialog() {
        _uiState.update { it.copy(showProUpgrade = true) }
    }

    fun dismissProUpgrade() {
        _uiState.update { it.copy(showProUpgrade = false) }
    }

    fun selectLocation(location: SavedLocation) {
        _uiState.update { it.copy(selectedLocation = location) }
    }

    fun dismissSelectedLocation() {
        _uiState.update { it.copy(selectedLocation = null, showEditLocationDialog = false) }
    }

    fun showEditLocationDialog() {
        _uiState.update { it.copy(showEditLocationDialog = true) }
    }

    fun dismissEditLocationDialog() {
        _uiState.update { it.copy(showEditLocationDialog = false) }
    }

    fun deleteSelectedLocation() {
        val location = _uiState.value.selectedLocation ?: return
        viewModelScope.launch {
            repository.deleteLocation(location)
            dismissSelectedLocation()
        }
    }

    fun toggleFavorite() {
        val location = _uiState.value.selectedLocation ?: return
        val updated = location.copy(isFavorite = !location.isFavorite)
        viewModelScope.launch {
            repository.updateLocation(updated)
            _uiState.update { it.copy(selectedLocation = updated) }
        }
    }

    fun updateLocationDetails(name: String, description: String) {
        val location = _uiState.value.selectedLocation ?: return
        val updated = location.copy(name = name.trim(), description = description.trim())
        viewModelScope.launch {
            repository.updateLocation(updated)
            _uiState.update { it.copy(selectedLocation = updated, showEditLocationDialog = false) }
        }
    }

    fun toggleMapType() {
        val newType = if (_uiState.value.mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL
        _uiState.update { it.copy(mapType = newType) }
        viewModelScope.launch {
            settingsRepository.setMapType(if (newType == MapType.HYBRID) "HYBRID" else "NORMAL")
        }
    }

    fun launchBillingFlow(activity: Activity) {
        proRepository.launchBillingFlow(activity)
        dismissProUpgrade()
    }

    private fun saveLocationIfNeeded(latLng: LatLng) {
        viewModelScope.launch {
            if (!_uiState.value.isProActive && _uiState.value.savedLocations.size >= FREE_LOCATION_LIMIT) {
                return@launch
            }
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
        viewModelScope.launch { settingsRepository.setRouteSpeed(mode.speedKmh) }
    }

    fun setSpeed(speedKmh: Double) {
        if (speedKmh <= 0.0) {
            setMockError(MockError.InvalidInput("Speed must be greater than 0 km/h"))
            return
        }
        _uiState.update { it.copy(speedKmh = speedKmh) }
        routeSimulator.setSpeed(speedKmh / KMH_TO_MPS_DIVISOR)
        viewModelScope.launch { settingsRepository.setRouteSpeed(speedKmh) }
    }

    fun playRoute() {
        if (!_uiState.value.isProActive) {
            _uiState.update { it.copy(showProUpgrade = true) }
            return
        }
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
        const val FREE_LOCATION_LIMIT = 5
        const val PREFS_NAME = "mockgps_prefs"
        const val KEY_MOCK_COUNT = "mock_start_count"
        const val KEY_REVIEW_SHOWN = "review_shown"
        const val REVIEW_TRIGGER_COUNT = 3
    }
}
