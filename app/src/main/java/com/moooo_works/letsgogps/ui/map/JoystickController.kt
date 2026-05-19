package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoystickController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val overlayManager: JoystickOverlayManager,
    private val mockStateRepository: MockStateRepository,
    private val context: Context,
    private val onStopMocking: () -> Unit,
    private val onCameraMove: (LatLng) -> Unit,
    private val onSetTransportMode: (TransportMode) -> Unit
) {
    private var tickerJob: Job? = null
    private var currentX = 0f
    private var currentY = 0f

    fun toggle() {
        if (!state.value.isJoystickEnabled) {
            if (!state.value.isProActive) {
                state.update { it.copy(showProUpgrade = true) }
                return
            }
            if (!ensureFloatingWindowPermission()) return
            state.update { it.copy(isJoystickEnabled = true) }
            startTicker()
            overlayManager.show {
                val s by state.collectAsState()
                JoystickOverlayView(
                    transportMode = s.transportMode,
                    onMove = { dx, dy -> currentX = dx; currentY = dy },
                    onWindowDrag = { dx, dy -> overlayManager.updatePosition(dx, dy) },
                    onWindowDragEnd = { overlayManager.snapToEdge() },
                    onToggleSpeed = { cycleTransportMode() },
                    onStop = { stopMockingFromJoystick() }
                )
            }
        } else {
            state.update { it.copy(isJoystickEnabled = false) }
            stopTicker()
            overlayManager.hide()
        }
    }

    private fun cycleTransportMode() {
        val next = when (state.value.transportMode) {
            TransportMode.WALKING -> TransportMode.CYCLING
            TransportMode.CYCLING -> TransportMode.DRIVING
            TransportMode.DRIVING -> TransportMode.WALKING
        }
        onSetTransportMode(next)
    }

    private fun stopMockingFromJoystick() {
        onStopMocking()
        if (state.value.isJoystickEnabled) toggle()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                if (currentX != 0f || currentY != 0f) applyMovement(currentX, currentY)
                delay(100)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        currentX = 0f
        currentY = 0f
    }

    private fun applyMovement(dx: Float, dy: Float) {
        val current = state.value
        val metersPerTick = (current.speedKmh * 1000.0 / 3600.0) * 0.1
        val degreesPerTick = metersPerTick / 111000.0
        val latDelta = -dy * degreesPerTick
        val cosLat = kotlin.math.cos(Math.toRadians(current.centerLocation.latitude)).coerceAtLeast(0.001)
        val lngDelta = dx * degreesPerTick / cosLat
        val newCenter = LatLng(
            current.centerLocation.latitude + latDelta,
            current.centerLocation.longitude + lngDelta
        )
        onCameraMove(newCenter)
        if (current.isMocking && current.mapMode == MapMode.SINGLE) {
            mockStateRepository.setCurrentMockLocation(newCenter)
        }
    }

    private fun ensureFloatingWindowPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            state.update { it.copy(mockError = MockError.FloatingWindowPermissionMissing) }
            return false
        }
        return true
    }

    fun onCleared() {
        stopTicker()
        overlayManager.hide()
    }

    internal fun applyMovementForTest(dx: Float, dy: Float) = applyMovement(dx, dy)
    internal fun cycleTransportModeForTest() = cycleTransportMode()
}
