package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteProgress
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState

enum class TransportMode(val speedKmh: Double) {
    WALKING(5.0),
    CYCLING(15.0),
    DRIVING(40.0)
}

const val ROUTE_SPEED_MIN_KMH = 1f
const val ROUTE_SPEED_MAX_KMH = 100f
const val ROUTE_SPEED_STEPS = 0

enum class MapMode {
    SINGLE,
    ROUTE
}

data class MapUiState(
    val mapMode: MapMode = MapMode.SINGLE,
    val isMocking: Boolean = false,
    /** Optimistic flag: tap → true → cleared when [isMocking] flips or after a 5s safety timeout. */
    val isStartingMocking: Boolean = false,
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
    val isJoystickEnabled: Boolean = false,
    val isProActive: Boolean = false,
    val isAdFreeActive: Boolean = false,
    val adUnlockRemainingMillis: Long = 0L,
    val showProUpgrade: Boolean = false,
    /** Localized recurring price from Play Billing; null until ProductDetails loads. */
    val subscriptionPrice: String? = null,
    val mapType: MapType = MapType.NORMAL,
    val selectedLocation: SavedLocation? = null,
    val showEditLocationDialog: Boolean = false,
    val showOnboarding: Boolean = false,
    /** Current loop/bounce playback mode for route simulation. */
    val loopMode: LoopMode = LoopMode.NONE,
    /** Non-null while the route simulation is PLAYING or PAUSED; null when IDLE. */
    val routeProgress: RouteProgress? = null,
    /** Show a "what's new" tip card about loop/bounce modes. */
    val showLoopBounceTip: Boolean = false,
    /** Show a "what's new" tip card about clipboard detection. */
    val showClipboardHintTip: Boolean = false,
    /** Show a "what's new" tip card about GPX import. */
    val showGpxTip: Boolean = false,
    /** Show the system health-check ModalBottomSheet (driven by startMocking blocking failure or settings entry). */
    val showHealthCheck: Boolean = false,
    /** Latest health-check snapshot; null until first refresh. */
    val healthCheckState: HealthCheckState? = null,
    /** Non-null when mock-location time-zone differs from device system time-zone. */
    val timezoneMismatch: TimezoneMismatch? = null,
)

data class TimezoneMismatch(
    val mockTimezone: String,
    val systemTimezone: String,
)
