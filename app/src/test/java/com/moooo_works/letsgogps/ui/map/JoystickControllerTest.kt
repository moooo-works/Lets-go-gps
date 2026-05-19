package com.moooo_works.letsgogps.ui.map

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JoystickControllerTest {

    private val overlayManager = mockk<JoystickOverlayManager>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val state = MutableStateFlow(MapUiState())
    private var stopMockingCalled = false
    private var lastCameraMove: LatLng? = null
    private var lastSetTransportMode: TransportMode? = null

    private fun makeController(scope: kotlinx.coroutines.CoroutineScope) = JoystickController(
        state = state,
        scope = scope,
        overlayManager = overlayManager,
        mockStateRepository = mockStateRepository,
        context = context,
        onStopMocking = { stopMockingCalled = true },
        onCameraMove = { lastCameraMove = it },
        onSetTransportMode = { lastSetTransportMode = it }
    )

    @Test
    fun `toggle shows Pro upgrade when not Pro`() = runTest {
        state.value = state.value.copy(isProActive = false)
        val ctrl = makeController(this)
        ctrl.toggle()
        assertTrue(state.value.showProUpgrade)
        assertFalse(state.value.isJoystickEnabled)
    }

    @Test
    fun `toggle disables joystick and hides overlay when already enabled`() = runTest {
        state.value = state.value.copy(isProActive = true, isJoystickEnabled = true)
        val ctrl = makeController(this)
        ctrl.toggle()
        assertFalse(state.value.isJoystickEnabled)
        verify { overlayManager.hide() }
    }

    @Test
    fun `applyMovementForTest moves center north when dy is negative`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(10.0, 20.0), speedKmh = 36.0)
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 0f, dy = -1f)
        assertNotNull(lastCameraMove)
        assertTrue(lastCameraMove!!.latitude > 10.0)
    }

    @Test
    fun `applyMovementForTest moves center east when dx is positive`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(0.0, 20.0), speedKmh = 36.0)
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        assertNotNull(lastCameraMove)
        assertTrue(lastCameraMove!!.longitude > 20.0)
    }

    @Test
    fun `applyMovementForTest updates mock location when mocking in SINGLE mode`() = runTest {
        state.value = state.value.copy(
            centerLocation = LatLng(10.0, 20.0),
            speedKmh = 36.0,
            isMocking = true,
            mapMode = MapMode.SINGLE
        )
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        verify { mockStateRepository.setCurrentMockLocation(any()) }
    }

    @Test
    fun `applyMovementForTest does not update mock location when not mocking`() = runTest {
        state.value = state.value.copy(
            centerLocation = LatLng(10.0, 20.0),
            speedKmh = 36.0,
            isMocking = false
        )
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        verify(exactly = 0) { mockStateRepository.setCurrentMockLocation(any()) }
    }

    @Test
    fun `cycleTransportModeForTest cycles WALKING to CYCLING`() = runTest {
        state.value = state.value.copy(transportMode = TransportMode.WALKING)
        val ctrl = makeController(this)
        ctrl.cycleTransportModeForTest()
        assert(lastSetTransportMode == TransportMode.CYCLING)
    }

    @Test
    fun `onCleared hides overlay`() = runTest {
        val ctrl = makeController(this)
        ctrl.onCleared()
        verify { overlayManager.hide() }
    }
}
