package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteProgress
import com.moooo_works.letsgogps.domain.SimulationState

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
    val isJoystickEnabled: Boolean = false,
    val isProActive: Boolean = false,
    val showProUpgrade: Boolean = false,
    val mapType: MapType = MapType.NORMAL,
    val selectedLocation: SavedLocation? = null,
    val showEditLocationDialog: Boolean = false,
    val showOnboarding: Boolean = false,
    /** Current loop/bounce playback mode for route simulation. */
    val loopMode: LoopMode = LoopMode.NONE,
    /** Non-null while the route simulation is PLAYING or PAUSED; null when IDLE. */
    val routeProgress: RouteProgress? = null,
    /** Show a "what's new" tip card about loop/bounce modes. */
    val showLoopBounceTip: Boolean = false
)
