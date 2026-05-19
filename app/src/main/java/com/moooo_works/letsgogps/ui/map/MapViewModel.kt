package com.moooo_works.letsgogps.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.TimezoneRepository
import com.moooo_works.letsgogps.utils.GeoDistanceMeters
import java.util.TimeZone
import kotlinx.coroutines.flow.first
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
import com.moooo_works.letsgogps.domain.LoopMode
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
    private val systemHealthCheck: SystemHealthCheck,
    private val timezoneRepository: TimezoneRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var timezoneCheckJob: Job? = null
    private var lastTimezoneCheckLatLng: LatLng? = null

    private var saveCenterJob: Job? = null
    private var isFirstLoad = true

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val locationPinController = LocationPinController(_uiState, viewModelScope, repository)

    private val routeController = RouteController(
        state = _uiState,
        scope = viewModelScope,
        repository = repository,
        mockStateRepository = mockStateRepository,
        routeSimulator = routeSimulator,
        settingsRepository = settingsRepository,
        context = context,
        onStopMocking = ::stopMocking,
        onEnsurePermission = ::ensurePermission
    )

    private val joystickController = JoystickController(
        state = _uiState,
        scope = viewModelScope,
        overlayManager = joystickOverlayManager,
        mockStateRepository = mockStateRepository,
        context = context,
        onStopMocking = ::stopMocking,
        onCameraMove = ::onCameraMove,
        onSetTransportMode = ::setTransportMode
    )

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
                            MockStatus.ROUTE_PLAYING,
                            MockStatus.ROUTE_PAUSED,
                            MockStatus.ROUTE_COMPLETED -> MapMode.ROUTE
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
                routeSimulator.setSpeed(speed / KMH_TO_MPS_DIVISOR)
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
            proRepository.isAdFreeActive.collect { isAdFree ->
                _uiState.update { it.copy(isAdFreeActive = isAdFree) }
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

        // Collect route simulation progress
        viewModelScope.launch {
            routeSimulator.routeProgress.collect { progress ->
                _uiState.update { it.copy(routeProgress = progress) }
            }
        }

        // Show "what's new" tip if user hasn't seen this feature version yet
        viewModelScope.launch {
            settingsRepository.getLoopBounceTipSeenVersion().collect { seenVersion ->
                if (seenVersion < LOOP_BOUNCE_TIP_VERSION) {
                    _uiState.update { it.copy(showLoopBounceTip = true) }
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.hasSeenClipboardHintTip().collect { seen ->
                if (!seen) {
                    _uiState.update { it.copy(showClipboardHintTip = true) }
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.hasSeenGpxTip().collect { seen ->
                if (!seen) {
                    _uiState.update { it.copy(showGpxTip = true) }
                }
            }
        }
    }

    fun dismissOnboarding() {
        _uiState.update { it.copy(showOnboarding = false) }
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    /**
     * Cycles the loop mode through NONE → LOOP → BOUNCE → NONE.
     * Propagates the new mode to [RouteSimulator] immediately.
     */
    fun cycleLoopMode() = routeController.cycleLoopMode()

    /** Dismiss the "clipboard hint" new-feature tip and persist the ack. */
    fun dismissClipboardHintTip() {
        _uiState.update { it.copy(showClipboardHintTip = false) }
        viewModelScope.launch { settingsRepository.setClipboardHintTipSeen() }
    }

    /** Dismiss the "GPX import" new-feature tip and persist the ack. */
    fun dismissGpxTip() {
        _uiState.update { it.copy(showGpxTip = false) }
        viewModelScope.launch { settingsRepository.setGpxTipSeen() }
    }

    /** Dismiss the "loop/bounce is available" new-feature tip and persist the ack. */
    fun dismissLoopBounceTip() {
        _uiState.update { it.copy(showLoopBounceTip = false) }
        viewModelScope.launch {
            settingsRepository.setLoopBounceTipSeen(LOOP_BOUNCE_TIP_VERSION)
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
        // Pre-flight health check: surface every blocking failure at once
        // instead of the legacy fail-fast MockErrorDialog.
        val healthState = systemHealthCheck.refresh()
        if (healthState.hasBlockingFailure) {
            _uiState.update {
                it.copy(showHealthCheck = true, healthCheckState = healthState)
            }
            return
        }

        if (!ensurePermission()) return

        val target = _uiState.value.centerLocation
        val intent = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START_SINGLE
            putExtra(MockLocationService.EXTRA_LAT, target.latitude)
            putExtra(MockLocationService.EXTRA_LNG, target.longitude)
        }
        ContextCompat.startForegroundService(context, intent)

        maybeCheckTimezoneMismatch(target)
        locationPinController.saveIfNeeded(target)
        checkAndTriggerReview()
    }

    /**
     * Async timezone check on mock start. Throttled to "moved more than 1km
     * since last check" so the API isn't hammered on rapid restarts. Uses
     * timeapi.io — failure (network, parse) silently skips the warning.
     */
    private fun maybeCheckTimezoneMismatch(target: LatLng) {
        val last = lastTimezoneCheckLatLng
        if (last != null &&
            GeoDistanceMeters.haversineMeters(last.latitude, last.longitude, target.latitude, target.longitude) < 1000.0
        ) return
        lastTimezoneCheckLatLng = target

        timezoneCheckJob?.cancel()
        timezoneCheckJob = viewModelScope.launch {
            if (!settingsRepository.observeEnableTimezoneCheck().first()) return@launch
            val mockTz = timezoneRepository.resolveTimezone(target.latitude, target.longitude)
                ?: return@launch
            val systemTz = TimeZone.getDefault().id
            if (mockTz != systemTz) {
                _uiState.update {
                    it.copy(timezoneMismatch = TimezoneMismatch(mockTz, systemTz))
                }
            } else {
                _uiState.update { it.copy(timezoneMismatch = null) }
            }
        }
    }

    fun dismissTimezoneMismatch() {
        _uiState.update { it.copy(timezoneMismatch = null) }
    }

    fun disableTimezoneCheck() {
        viewModelScope.launch { settingsRepository.setEnableTimezoneCheck(false) }
        _uiState.update { it.copy(timezoneMismatch = null) }
    }

    /** Re-evaluate health check; called when user taps Re-check or returns from Settings. */
    fun refreshHealthCheck() {
        _uiState.update {
            it.copy(healthCheckState = systemHealthCheck.refresh())
        }
    }

    /** Open the health-check sheet manually (e.g. from settings entry). */
    fun openHealthCheck() {
        _uiState.update {
            it.copy(
                showHealthCheck = true,
                healthCheckState = systemHealthCheck.refresh(),
            )
        }
    }

    fun dismissHealthCheck() {
        _uiState.update { it.copy(showHealthCheck = false) }
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

    fun toggleJoystick() = joystickController.toggle()

    fun selectSearchResult(location: GeocodedLocation) {
        _uiState.update { it.copy(centerLocation = location.latLng) }
    }

    fun showProUpgradeDialog() {
        _uiState.update { it.copy(showProUpgrade = true) }
    }

    fun dismissProUpgrade() {
        _uiState.update { it.copy(showProUpgrade = false) }
    }

    fun selectLocation(location: SavedLocation) = locationPinController.select(location)
    fun dismissSelectedLocation() = locationPinController.dismiss()
    fun showEditLocationDialog() = locationPinController.showEditDialog()
    fun dismissEditLocationDialog() = locationPinController.dismissEditDialog()
    fun deleteSelectedLocation() = locationPinController.delete()
    fun toggleFavorite() = locationPinController.toggleFavorite()
    fun updateLocationDetails(name: String, description: String) =
        locationPinController.updateDetails(name, description)

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

    fun addWaypoint() = routeController.addWaypoint()

    fun addWaypointAt(latLng: LatLng) = routeController.addWaypointAt(latLng)

    fun removeWaypointAt(index: Int) = routeController.removeWaypointAt(index)

    fun clearRoute() = routeController.clearRoute()

    fun saveCurrentRoute(name: String) = routeController.saveCurrentRoute(name)

    fun loadRoute(routeId: Int) = routeController.loadRoute(routeId)

    fun onRouteFitConsumed() = routeController.onRouteFitConsumed()

    fun setTransportMode(mode: TransportMode) = routeController.setTransportMode(mode)

    fun setSpeed(speedKmh: Double) = routeController.setSpeed(speedKmh)

    fun playRoute() = routeController.playRoute()

    /**
     * Start spiral exploration around the current map center. Pro-gated like
     * playRoute since it leans on the same continuous simulation pipeline.
     */
    fun startExplorationAtCenter() = routeController.startExplorationAtCenter()

    /**
     * Teleport-explore the currently loaded route's waypoints. No-op when no
     * route is loaded — calling site should hide/disable the entry point in
     * that case.
     */
    fun startTeleportExplorationOfRoute() = routeController.startTeleportExplorationOfRoute()

    fun pauseRoute() = routeController.pauseRoute()

    fun stopRoute() = routeController.stopRoute()

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
            MockPermissionStatus.DeveloperModeDisabled -> {
                setMockError(MockError.DeveloperModeDisabled)
                false
            }
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
        joystickController.onCleared()
        super.onCleared()
    }

    private companion object {
        const val KMH_TO_MPS_DIVISOR = 3.6
        const val PREFS_NAME = "mockgps_prefs"
        const val KEY_MOCK_COUNT = "mock_start_count"
        const val KEY_REVIEW_SHOWN = "review_shown"
        const val REVIEW_TRIGGER_COUNT = 3
        /** Bump this when a new feature tip should be shown again to existing users. */
        const val LOOP_BOUNCE_TIP_VERSION = 1
    }
}
