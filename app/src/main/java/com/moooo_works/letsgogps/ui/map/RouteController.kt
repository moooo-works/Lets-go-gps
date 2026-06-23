package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus
import com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.service.MockLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RouteController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val repository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val routeSimulator: RouteSimulator,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    private val systemHealthCheck: SystemHealthCheck,
    private val onStopMocking: () -> Unit,
    private val onEnsurePermission: () -> Boolean
) {
    private companion object {
        const val KMH_TO_MPS_DIVISOR = 3.6
    }

    // Route playback is the long-running case where Doze / OEM battery management
    // kills the foreground service mid-route (the vivo "5 min then GPS resets"
    // report). If the user hasn't whitelisted us, nudge once per session via the
    // existing health-check sheet — non-blocking, they can dismiss and play.
    private var batteryNudgeShown = false

    fun addWaypoint() = addWaypointAt(state.value.centerLocation)

    fun addWaypointAt(latLng: LatLng) {
        val updated = state.value.waypoints + latLng
        state.update { it.copy(waypoints = updated) }
        mockStateRepository.setActiveRouteWaypoints(updated)
        routeSimulator.setRoute(updated)
    }

    fun removeWaypointAt(index: Int) {
        val updated = state.value.waypoints.toMutableList().apply { removeAt(index) }
        state.update { it.copy(waypoints = updated) }
        mockStateRepository.setActiveRouteWaypoints(updated)
        routeSimulator.setRoute(updated)
    }

    fun clearRoute() {
        state.update { it.copy(waypoints = emptyList(), currentMockLocation = null, currentLocation = null) }
        mockStateRepository.setActiveRouteWaypoints(emptyList())
        onStopMocking()
        routeSimulator.stop()
    }

    fun saveCurrentRoute(name: String) {
        val normalizedName = name.trim()
        val points = state.value.waypoints
        if (normalizedName.isBlank() || normalizedName.length > 40 || points.size < 2) return
        scope.launch {
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
        scope.launch {
            val route = repository.getRouteWithPoints(routeId) ?: return@launch
            clearRoute()
            val points = route.points
                .sortedBy { it.orderIndex }
                .map { LatLng(it.latitude, it.longitude) }
            state.update {
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
        state.update { it.copy(routeFitRequestToken = null) }
    }

    fun setTransportMode(mode: TransportMode) {
        state.update { it.copy(transportMode = mode, speedKmh = mode.speedKmh) }
        routeSimulator.setSpeed(mode.speedKmh / KMH_TO_MPS_DIVISOR)
        scope.launch { settingsRepository.setRouteSpeed(mode.speedKmh) }
    }

    fun setSpeed(speedKmh: Double) {
        if (speedKmh <= 0.0) {
            state.update { it.copy(mockError = MockError.InvalidInput("Speed must be greater than 0 km/h")) }
            return
        }
        state.update { it.copy(speedKmh = speedKmh) }
        routeSimulator.setSpeed(speedKmh / KMH_TO_MPS_DIVISOR)
        scope.launch { settingsRepository.setRouteSpeed(speedKmh) }
    }

    fun cycleLoopMode() {
        val next = when (state.value.loopMode) {
            LoopMode.NONE -> LoopMode.LOOP
            LoopMode.LOOP -> LoopMode.BOUNCE
            LoopMode.BOUNCE -> LoopMode.NONE
        }
        routeSimulator.setLoopMode(next)
        state.update { it.copy(loopMode = next) }
    }

    fun playRoute() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (state.value.waypoints.size < 2) {
            state.update { it.copy(mockError = MockError.InvalidInput("Add at least 2 waypoints to play a route")) }
            return
        }
        if (!onEnsurePermission()) return
        // One-time battery-whitelist nudge before a long route, so Doze/OEM
        // management is less likely to kill the service mid-route. Reuses the
        // health-check sheet (battery is its sole soft item); does not block play.
        if (!batteryNudgeShown) {
            val health = systemHealthCheck.refresh()
            if (health.statusOf(HealthCheckItem.BatteryOptimizationExempt) is ItemStatus.Failed) {
                batteryNudgeShown = true
                state.update { it.copy(showHealthCheck = true, healthCheckState = health) }
                return
            }
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_ROUTE
            }
        )
    }

    fun startExplorationAtCenter() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (!onEnsurePermission()) return
        val target = state.value.centerLocation
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_EXPLORATION
                putExtra(MockLocationService.EXTRA_LAT, target.latitude)
                putExtra(MockLocationService.EXTRA_LNG, target.longitude)
            }
        )
    }

    fun startTeleportExplorationOfRoute() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (!onEnsurePermission()) return
        val targets = state.value.waypoints
        if (targets.isEmpty()) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_TELEPORT_EXPLORATION
                putExtra(MockLocationService.EXTRA_LATS, targets.map { it.latitude }.toDoubleArray())
                putExtra(MockLocationService.EXTRA_LNGS, targets.map { it.longitude }.toDoubleArray())
            }
        )
    }

    fun pauseRoute() {
        context.startService(
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_PAUSE_ROUTE
            }
        )
    }

    fun stopRoute() = onStopMocking()
}
