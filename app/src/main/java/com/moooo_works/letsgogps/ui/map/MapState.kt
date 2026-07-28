package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapType
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RoutePlaybackMode
import com.moooo_works.letsgogps.domain.RouteProgress
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer

enum class TransportMode(val speedKmh: Double) {
    WALKING(5.0),
    CYCLING(15.0),
    DRIVING(40.0)
}

/**
 * 哪一種模擬啟動被步數同步的計次閘門擋下，等使用者看完廣告後要恢復執行。
 *
 * 用 enum 而非 lambda：UiState 必須可比較，塞 lambda 會讓 Compose 每次
 * recomposition 都判定狀態改變。各啟動所需的參數本來就都在 state 裡。
 */
enum class PendingStart {
    SINGLE,
    ROUTE,
    EXPLORATION,
    TELEPORT_EXPLORATION,
}

const val ROUTE_SPEED_MIN_KMH = 1f
const val ROUTE_SPEED_MAX_KMH = 100f
const val ROUTE_SPEED_STEPS = 0

/** 步數同步的計速上限（km/h）。UI 用；門檻定義在 [StepAccumulator.MAX_STEP_SPEED_KMH]。 */
const val STEP_SYNC_MAX_SPEED_KMH =
    com.moooo_works.letsgogps.domain.health.StepAccumulator.MAX_STEP_SPEED_KMH.toFloat()

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
    /** Play Billing offer (localized price + trial eligibility); null until ProductDetails loads. */
    val subscriptionOffer: SubscriptionOffer? = null,

    // ── 步數同步計次閘門 ──────────────────────────────────────────────────
    /** Subscription only — ad-unlock must not satisfy the credit gate. */
    val isSubscriptionActive: Boolean = false,
    /** Whether the user turned step sync on in Settings. */
    val stepSyncEnabled: Boolean = false,
    /** Remaining per-use credits in the generic wallet. */
    val featureCredits: Int = 0,
    /** Shown when a non-subscriber with 0 credits tries to start with step sync on. */
    val showStepSyncCreditDialog: Boolean = false,
    /** Which start action to resume once the user earns a credit. */
    val pendingStepSyncStart: PendingStart? = null,
    /** Set when the rewarded ad could not be shown, so the dialog can explain. */
    val stepSyncAdUnavailable: Boolean = false,
    val mapType: MapType = MapType.NORMAL,
    val selectedLocation: SavedLocation? = null,
    val showEditLocationDialog: Boolean = false,
    val showOnboarding: Boolean = false,
    /** Current loop/bounce playback mode for route simulation. */
    val loopMode: LoopMode = LoopMode.NONE,
    /** WALK = interpolate at speed; JUMP = teleport waypoint-to-waypoint. */
    val playbackMode: RoutePlaybackMode = RoutePlaybackMode.WALK,
    /** Seconds between jumps when [playbackMode] is JUMP. */
    val jumpIntervalSec: Int = RouteSimulator.DEFAULT_JUMP_INTERVAL_SEC,
    /** Non-null while the route simulation is PLAYING or PAUSED; null when IDLE. */
    val routeProgress: RouteProgress? = null,
    /** Show a "what's new" tip card about loop/bounce modes. */
    val showLoopBounceTip: Boolean = false,
    /** Show a "what's new" tip card about clipboard detection. */
    val showClipboardHintTip: Boolean = false,
    /** Show a "what's new" tip card about GPX import. */
    val showGpxTip: Boolean = false,
    /** Show a "what's new" tip card about jump playback mode. */
    val showJumpModeTip: Boolean = false,
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
