package com.moooo_works.letsgogps.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.moooo_works.letsgogps.ui.theme.MockGpsTheme
import com.moooo_works.letsgogps.domain.repository.SearchRepository
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.FeatureCost
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import com.moooo_works.letsgogps.data.billing.RewardedAdManager
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
    private val rewardedAdManager: RewardedAdManager,
    private val systemHealthCheck: SystemHealthCheck,
    private val timezoneRepository: TimezoneRepository,
    private val searchRepository: SearchRepository,
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
        systemHealthCheck = systemHealthCheck,
        onStopMocking = ::stopMocking,
        onEnsurePermission = ::ensurePermission,
        onSimulationStarted = ::consumeStepSyncCreditIfNeeded
    )

    private val joystickController = JoystickController(
        state = _uiState,
        scope = viewModelScope,
        overlayManager = joystickOverlayManager,
        mockStateRepository = mockStateRepository,
        context = context,
        onStopMocking = ::stopMocking,
        onCameraMove = ::onCameraMove,
        onSetTransportMode = ::setTransportMode,
        onOverlayDismissed = {
            cancelCompanionPending()
            floatingCompanionController.close()
        }
    )

    private val companionRequests = CompanionExecutionRequests()
    private var companionPending: CompanionExecutionRequests.Request? = null
    private var companionJob: Job? = null
    val floatingCompanionController = FloatingCompanionController(
        _uiState, repository, searchRepository, settingsRepository, viewModelScope, ::executeCompanionAction
    )

    private fun companionRouteKey(): List<LatLng>? = _uiState.value.let {
        it.waypoints.toList().takeIf { _ -> it.isMocking && it.mapMode == MapMode.ROUTE }
    }

    internal fun executeCompanionAction(action: CompanionAction) {
        cancelCompanionPending()
        val request = companionRequests.create(action, companionRouteKey())
        executeCompanionRequest(request)
    }

    internal suspend fun awaitCompanionCommandForTest() { companionJob?.join() }

    private fun executeCompanionRequest(request: CompanionExecutionRequests.Request, withoutSteps: Boolean = false) {
        companionJob?.cancel()
        companionJob = viewModelScope.launch {
            val health = withContext(Dispatchers.IO) { systemHealthCheck.refresh() }
            if (!companionRequests.isValid(request, companionRouteKey()) ||
                (request.action is CompanionAction.PlayRoute && request.action.points != _uiState.value.waypoints)) {
                floatingCompanionController.setMessage(CompanionMessage.ROUTE_CHANGED)
                return@launch
            }
            if (!_uiState.value.isProActive || !proRepository.isProActive.value) {
                floatingCompanionController.setMessage(CompanionMessage.NEED_PRO)
                return@launch
            }
            if (health.hasBlockingFailure) {
                _uiState.update { it.copy(showHealthCheck = true, healthCheckState = health) }
                return@launch
            }
            if (!ensurePermission()) return@launch
            val action = request.action
            if (action is CompanionAction.UseRoute) {
                // Loading prepares an immutable route; playing is a separate explicit action.
                if (action.points.size < 2) return@launch
                if (_uiState.value.isMocking) {
                    try {
                        context.startService(Intent(context, MockLocationService::class.java).apply {
                            this.action = MockLocationService.ACTION_STOP
                        })
                    } catch (error: RuntimeException) {
                        setMockError(MockError.Unknown(error.message ?: "Unable to stop simulation"))
                        return@launch
                    }
                    // Apply only once the old service has stopped; a late STOP must not stop new playback.
                    val stopped = withTimeoutOrNull(5_000L) {
                        mockStateRepository.mockStatus.first { it == MockStatus.IDLE }
                    }
                    if (stopped == null || !companionRequests.isCurrent(request)) return@launch
                }
                routeSimulator.stop()
                mockStateRepository.setActiveRouteWaypoints(action.points)
                routeSimulator.setRoute(action.points)
                _uiState.update { it.copy(mapMode = MapMode.ROUTE, waypoints = action.points,
                    centerLocation = action.points.first(), routeFitRequestToken = System.currentTimeMillis()) }
                companionRequests.invalidate()
                return@launch
            }
            val pending = when (action) {
                is CompanionAction.Locate -> PendingStart.SINGLE
                is CompanionAction.Explore -> PendingStart.EXPLORATION
                is CompanionAction.PlayRoute -> PendingStart.ROUTE
                else -> return@launch
            }
            val steps = if (withoutSteps) false else StepSyncGate.resolve(_uiState, pending)
            if (steps == null) { companionPending = request; return@launch }
            if (!companionRequests.isValid(request, companionRouteKey())) return@launch
            val intent = Intent(context, MockLocationService::class.java).apply {
                putExtra(MockLocationService.EXTRA_STEP_SYNC_ALLOWED, steps)
                when (action) {
                    is CompanionAction.Locate -> {
                        this.action = MockLocationService.ACTION_START_SINGLE
                        putExtra(MockLocationService.EXTRA_LAT, action.target.latLng.latitude)
                        putExtra(MockLocationService.EXTRA_LNG, action.target.latLng.longitude)
                    }
                    is CompanionAction.Explore -> {
                        this.action = MockLocationService.ACTION_START_EXPLORATION
                        putExtra(MockLocationService.EXTRA_LAT, action.target.latLng.latitude)
                        putExtra(MockLocationService.EXTRA_LNG, action.target.latLng.longitude)
                    }
                    is CompanionAction.PlayRoute -> this.action = MockLocationService.ACTION_START_ROUTE
                    else -> Unit
                }
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: RuntimeException) {
                setMockError(MockError.Unknown(error.message ?: "Unable to start simulation"))
                companionPending = null
                companionRequests.invalidate()
                return@launch
            }
            consumeStepSyncCreditIfNeeded(steps)
            companionPending = null
            companionRequests.invalidate()
            if (action is CompanionAction.Locate) {
                _uiState.update { it.copy(mapMode = MapMode.SINGLE, centerLocation = action.target.latLng) }
                maybeCheckTimezoneMismatch(action.target.latLng)
            }
        }
    }

    private fun cancelCompanionPending() {
        floatingCompanionController.cancelPendingActions()
        companionRequests.invalidate()
        companionJob?.cancel()
        if (companionPending != null) clearStepSyncCreditDialogState()
        companionPending = null
    }

    private fun companionPlayPause() {
        when (_uiState.value.simulationState) {
            SimulationState.PLAYING -> pauseRoute()
            SimulationState.PAUSED -> {
                cancelCompanionPending()
                if (_uiState.value.isProActive && ensurePermission()) context.startService(
                    Intent(context, MockLocationService::class.java).apply { action = MockLocationService.ACTION_RESUME_ROUTE })
            }
            else -> if (_uiState.value.waypoints.size >= 2)
                executeCompanionAction(CompanionAction.PlayRoute(_uiState.value.waypoints.toList()))
        }
    }

    private fun releaseCompanionInput() {
        joystickController.releaseMovement()
        joystickOverlayManager.setInputFocusable(false)
    }

    private fun copyCompanionCoordinate(point: LatLng?) {
        if (point == null) { floatingCompanionController.setMessage(CompanionMessage.UNKNOWN_LOCATION); return }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("GPS", formatCompanionCoordinate(point)))
        floatingCompanionController.setMessage(CompanionMessage.COPIED)
    }

    private fun closeCompanion() {
        cancelCompanionPending()
        floatingCompanionController.close()
        releaseCompanionInput()
        if (_uiState.value.isJoystickEnabled) joystickController.toggle()
    }

    private fun openAppFromCompanion() {
        releaseCompanionInput()
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun companionActions() = FloatingCompanionActions(
        onExpandedChange = { releaseCompanionInput(); floatingCompanionController.setExpanded(it) },
        onTabChange = { releaseCompanionInput(); floatingCompanionController.setTab(it) },
        onQueryChange = floatingCompanionController::setQuery,
        onSearch = floatingCompanionController::search,
        onPaste = { joystickOverlayManager.runWhenFocused {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let {
                floatingCompanionController.setQuery(it); floatingCompanionController.search()
            }
            joystickOverlayManager.setInputFocusable(false)
        } },
        onSelect = floatingCompanionController::select,
        onFolderChange = floatingCompanionController::setFolder,
        onFavoritesChange = floatingCompanionController::setFavoritesOnly,
        onLoadRoute = floatingCompanionController::requestLoadRoute,
        onLocate = floatingCompanionController::requestLocate,
        onQueue = floatingCompanionController::queueSelected,
        onApplyQueue = floatingCompanionController::applyQueue,
        onSave = floatingCompanionController::saveSelected,
        onConfirmReplacement = floatingCompanionController::confirmReplacement,
        onCancelReplacement = floatingCompanionController::cancelReplacement,
        onPlayPause = ::companionPlayPause,
        onStop = { cancelCompanionPending(); joystickController.releaseMovement(); stopMocking() },
        onSpeedChange = ::setSpeed, onLoopChange = ::cycleLoopMode,
        onExplore = floatingCompanionController::requestExplore,
        onCopyCurrent = { copyCompanionCoordinate(currentCompanionCoordinate(_uiState.value)) },
        onCopySelected = { copyCompanionCoordinate(floatingCompanionController.state.value.selectedLocation?.latLng) },
        onClose = ::closeCompanion, onOpenApp = ::openAppFromCompanion,
        onInputFocus = joystickOverlayManager::setInputFocusable,
        onWindowDrag = { x, y -> joystickOverlayManager.updatePosition(x.toInt(), y.toInt()) },
        onWindowDragEnd = joystickOverlayManager::snapToEdge
    )

    private val _triggerReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerReview: SharedFlow<Unit> = _triggerReview.asSharedFlow()

    fun setMapMode(mode: MapMode) {
        if (_uiState.value.mapMode == mode) return
        cancelCompanionPending()

        if (mode == MapMode.SINGLE) {
            if (_uiState.value.simulationState != SimulationState.IDLE) {
                stopRoute()
            }
        }
        _uiState.update { it.copy(mapMode = mode) }
    }

    init {
        joystickController.setOverlayWrapper { joystick ->
            val map by uiState.collectAsState()
            val companion by floatingCompanionController.state.collectAsState()
            MockGpsTheme { FloatingCompanionView(map, companion, companionActions(), joystick) }
        }
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
                val mocking = status != MockStatus.IDLE
                _uiState.update {
                    it.copy(
                        isMocking = mocking,
                        // Clear the optimistic loading flag as soon as the service
                        // publishes the real mocking status — that's the moment
                        // the button can stop showing "啟動中…" and reveal the
                        // real stop button.
                        isStartingMocking = if (mocking) false else it.isStartingMocking,
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
                if (!isPro && _uiState.value.isJoystickEnabled) closeCompanion()
            }
        }

        viewModelScope.launch {
            proRepository.isSubscriptionActive.collect { subscribed ->
                _uiState.update { it.copy(isSubscriptionActive = subscribed) }
            }
        }
        viewModelScope.launch {
            proRepository.featureCredits.collect { credits ->
                _uiState.update { it.copy(featureCredits = credits) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeStepSyncEnabled().collect { enabled ->
                _uiState.update { it.copy(stepSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            proRepository.isAdFreeActive.collect { isAdFree ->
                _uiState.update { it.copy(isAdFreeActive = isAdFree) }
            }
        }

        viewModelScope.launch {
            proRepository.adUnlockExpiryMillis.collect { expiry ->
                _uiState.update {
                    it.copy(adUnlockRemainingMillis = (expiry - System.currentTimeMillis()).coerceAtLeast(0L))
                }
            }
        }

        viewModelScope.launch {
            proRepository.subscriptionOffer.collect { offer ->
                _uiState.update { it.copy(subscriptionOffer = offer) }
            }
        }

        rewardedAdManager.preload()

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

        viewModelScope.launch {
            settingsRepository.hasSeenJumpModeTip().collect { seen ->
                if (!seen) {
                    _uiState.update { it.copy(showJumpModeTip = true) }
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
    fun togglePlaybackMode() = routeController.togglePlaybackMode()
    fun setJumpInterval(sec: Int) = routeController.setJumpInterval(sec)

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

    /** Dismiss the "jump playback mode" new-feature tip and persist the ack. */
    fun dismissJumpModeTip() {
        _uiState.update { it.copy(showJumpModeTip = false) }
        viewModelScope.launch { settingsRepository.setJumpModeTipSeen() }
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
        cancelCompanionPending()
        // Debounce + idempotent: ignore taps while a start is already in flight
        // or mocking is already active. Without this guard a rapid double-tap
        // could fire two `startForegroundService` calls.
        val current = _uiState.value
        if (current.isStartingMocking || current.isMocking) return

        // Optimistic UI: flip the loading flag before doing anything else so
        // the button can show a spinner immediately. The `mockStatus` collector
        // clears the flag when the real status arrives; failure paths below
        // also clear it.
        _uiState.update { it.copy(isStartingMocking = true) }

        viewModelScope.launch {
            // Health check is 6 system IPC queries (AppOps/PackageManager/Settings/
            // PowerManager/LocationManager). Cheap individually but adds up on
            // Android 16 Beta where IPC overhead is higher — pull off the main
            // thread so the button's spinner animates smoothly.
            val healthState = withContext(Dispatchers.IO) { systemHealthCheck.refresh() }
            if (healthState.hasBlockingFailure) {
                _uiState.update {
                    it.copy(
                        showHealthCheck = true,
                        healthCheckState = healthState,
                        isStartingMocking = false,
                    )
                }
                return@launch
            }

            if (!ensurePermission()) {
                _uiState.update { it.copy(isStartingMocking = false) }
                return@launch
            }

            val stepSyncActive = StepSyncGate.resolve(_uiState, PendingStart.SINGLE)
            if (stepSyncActive == null) {
                // 次數不足，對話框已彈出——先解除按鈕的 loading 狀態。
                _uiState.update { it.copy(isStartingMocking = false) }
                return@launch
            }

            val target = _uiState.value.centerLocation
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_SINGLE
                putExtra(MockLocationService.EXTRA_LAT, target.latitude)
                putExtra(MockLocationService.EXTRA_LNG, target.longitude)
                putExtra(MockLocationService.EXTRA_STEP_SYNC_ALLOWED, stepSyncActive)
            }
            ContextCompat.startForegroundService(context, intent)
            consumeStepSyncCreditIfNeeded(stepSyncActive)

            maybeCheckTimezoneMismatch(target)
            locationPinController.saveIfNeeded(target)
            checkAndTriggerReview()

            // Safety: if the service never publishes MOCKING within 5s (engine
            // setup crashed, FGS rejected, etc.), un-stick the button so the
            // user can retry instead of staring at a frozen spinner forever.
            delay(5_000)
            _uiState.update { state ->
                if (state.isStartingMocking) state.copy(isStartingMocking = false) else state
            }
        }
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
        cancelCompanionPending()
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
        if (_uiState.value.isJoystickEnabled) closeCompanion() else joystickController.toggle()
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

    fun watchRewardedAd(activity: Activity) {
        rewardedAdManager.showAd(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    proRepository.grantAdUnlockHours(6)
                    _uiState.update { it.copy(showProUpgrade = false) }
                }
            },
            onUnavailable = {
                _uiState.update { it.copy(mockError = MockError.RewardedAdUnavailable) }
            }
        )
    }

    // ── 步數同步計次閘門 ──────────────────────────────────────────────────

    /**
     * 啟動成功後才扣次數。啟動失敗就等於沒扣過，不需要退款邏輯。
     * 訂閱者不扣。
     */
    private fun consumeStepSyncCreditIfNeeded(stepSyncActive: Boolean) {
        if (!StepSyncGate.shouldConsumeCredit(_uiState.value, stepSyncActive)) return
        viewModelScope.launch {
            proRepository.consumeFeatureCredits(FeatureCost.STEP_SYNC_SESSION)
        }
    }

    /** 「這次不用步數同步」——照常啟動模擬，只是不寫步數，也不扣次數。 */
    fun startWithoutStepSync() {
        companionPending?.let { request ->
            companionPending = null
            clearStepSyncCreditDialogState()
            executeCompanionRequest(request, withoutSteps = true)
            return
        }
        val pending = _uiState.value.pendingStepSyncStart ?: return
        dismissStepSyncCreditDialog()
        resumePendingStart(pending, stepSyncActive = false)
    }

    fun dismissStepSyncCreditDialog() {
        cancelCompanionPending()
        clearStepSyncCreditDialogState()
    }

    private fun clearStepSyncCreditDialogState() {
        _uiState.update {
            it.copy(
                showStepSyncCreditDialog = false,
                pendingStepSyncStart = null,
                stepSyncAdUnavailable = false,
            )
        }
    }

    /** 看一支獎勵廣告換一次步數同步。中途關閉或無庫存都不扣次數。 */
    fun watchAdForStepSyncCredit(activity: Activity) {
        val companionRequest = companionPending
        val pending = _uiState.value.pendingStepSyncStart ?: return
        // 刻意不設 loading 旗標：RewardedAdManager 在「使用者看到一半關掉」時
        // onReward 與 onUnavailable 都不會觸發，旗標會永遠卡在 true 而鎖死
        // 對話框。獎勵廣告是全螢幕的，播放期間看不到這個對話框，spinner 沒有價值。
        _uiState.update { it.copy(stepSyncAdUnavailable = false) }
        rewardedAdManager.showAd(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    proRepository.grantFeatureCredits(FeatureCost.CREDITS_PER_REWARDED_AD)
                    if (companionRequest != null) {
                        if (companionPending !== companionRequest || !companionRequests.isValid(companionRequest, companionRouteKey())) return@launch
                        companionPending = null
                        clearStepSyncCreditDialogState()
                        executeCompanionRequest(companionRequest)
                    } else {
                        dismissStepSyncCreditDialog()
                        resumePendingStart(pending, stepSyncActive = true)
                    }
                }
            },
            onUnavailable = {
                if (companionRequest != null && (companionPending !== companionRequest ||
                        !companionRequests.isValid(companionRequest, companionRouteKey()))) return@showAd
                // 不扣次數、不關對話框——使用者仍可改選「這次不用步數同步」。
                _uiState.update { it.copy(stepSyncAdUnavailable = true) }
            }
        )
    }

    private fun resumePendingStart(pending: PendingStart, stepSyncActive: Boolean) {
        when (pending) {
            PendingStart.ROUTE -> routeController.startRouteService(stepSyncActive)
            PendingStart.EXPLORATION -> routeController.startExplorationService(stepSyncActive)
            PendingStart.TELEPORT_EXPLORATION ->
                routeController.startTeleportExplorationService(stepSyncActive)
            PendingStart.SINGLE -> startSingleMock(stepSyncActive)
        }
    }

    private fun startSingleMock(stepSyncActive: Boolean) {
        val target = _uiState.value.centerLocation
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_SINGLE
                putExtra(MockLocationService.EXTRA_LAT, target.latitude)
                putExtra(MockLocationService.EXTRA_LNG, target.longitude)
                putExtra(MockLocationService.EXTRA_STEP_SYNC_ALLOWED, stepSyncActive)
            }
        )
        consumeStepSyncCreditIfNeeded(stepSyncActive)
        viewModelScope.launch {
            maybeCheckTimezoneMismatch(target)
            locationPinController.saveIfNeeded(target)
        }
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

    fun playRoute() { cancelCompanionPending(); routeController.playRoute() }

    /**
     * Start spiral exploration around the current map center. Pro-gated like
     * playRoute since it leans on the same continuous simulation pipeline.
     */
    fun startExplorationAtCenter() { cancelCompanionPending(); routeController.startExplorationAtCenter() }

    /**
     * Teleport-explore the currently loaded route's waypoints. No-op when no
     * route is loaded — calling site should hide/disable the entry point in
     * that case.
     */
    fun startTeleportExplorationOfRoute() { cancelCompanionPending(); routeController.startTeleportExplorationOfRoute() }

    fun pauseRoute() { cancelCompanionPending(); routeController.pauseRoute() }

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
        cancelCompanionPending()
        floatingCompanionController.dispose()
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
