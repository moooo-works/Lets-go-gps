package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.service.MockLocationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteControllerTest {

    private val repository = mockk<LocationRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val state = MutableStateFlow(MapUiState())
    private var stopMockingCalled = false
    private var ensurePermissionResult = true

    private fun makeController(scope: kotlinx.coroutines.CoroutineScope) = RouteController(
        state = state,
        scope = scope,
        repository = repository,
        mockStateRepository = mockStateRepository,
        routeSimulator = routeSimulator,
        settingsRepository = settingsRepository,
        context = context,
        onStopMocking = { stopMockingCalled = true },
        onEnsurePermission = { ensurePermissionResult }
    )

    @Test
    fun `addWaypointAt appends waypoint and syncs simulator`() = runTest {
        val ctrl = makeController(this)
        val pt = LatLng(1.0, 2.0)
        ctrl.addWaypointAt(pt)
        assertEquals(listOf(pt), state.value.waypoints)
        verify { mockStateRepository.setActiveRouteWaypoints(listOf(pt)) }
        verify { routeSimulator.setRoute(listOf(pt)) }
    }

    @Test
    fun `addWaypoint uses current centerLocation`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(5.0, 6.0))
        val ctrl = makeController(this)
        ctrl.addWaypoint()
        assertEquals(LatLng(5.0, 6.0), state.value.waypoints.first())
    }

    @Test
    fun `removeWaypointAt removes correct index`() = runTest {
        state.value = state.value.copy(waypoints = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        val ctrl = makeController(this)
        ctrl.removeWaypointAt(0)
        assertEquals(listOf(LatLng(3.0, 4.0)), state.value.waypoints)
        verify { mockStateRepository.setActiveRouteWaypoints(listOf(LatLng(3.0, 4.0))) }
    }

    @Test
    fun `clearRoute resets waypoints and calls onStopMocking and stops simulator`() = runTest {
        state.value = state.value.copy(waypoints = listOf(LatLng(1.0, 2.0)))
        val ctrl = makeController(this)
        ctrl.clearRoute()
        assertTrue(state.value.waypoints.isEmpty())
        assertTrue(stopMockingCalled)
        verify { routeSimulator.stop() }
    }

    @Test
    fun `cycleLoopMode rotates NONE then LOOP then BOUNCE then back to NONE`() = runTest {
        val ctrl = makeController(this)
        assertEquals(LoopMode.NONE, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.LOOP, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.BOUNCE, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.NONE, state.value.loopMode)
        verify(exactly = 3) { routeSimulator.setLoopMode(any()) }
    }

    @Test
    fun `setSpeed rejects zero and sets InvalidInput error`() = runTest {
        val ctrl = makeController(this)
        ctrl.setSpeed(0.0)
        assertTrue(state.value.mockError is MockError.InvalidInput)
        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
    }

    @Test
    fun `setSpeed positive value updates state and simulator`() = runTest {
        val ctrl = makeController(this)
        ctrl.setSpeed(30.0)
        advanceUntilIdle()
        assertEquals(30.0, state.value.speedKmh, 0.0)
        verify { routeSimulator.setSpeed(30.0 / 3.6) }
        coVerify { settingsRepository.setRouteSpeed(30.0) }
    }

    @Test
    fun `setTransportMode updates state transport and speed`() = runTest {
        val ctrl = makeController(this)
        ctrl.setTransportMode(TransportMode.DRIVING)
        advanceUntilIdle()
        assertEquals(TransportMode.DRIVING, state.value.transportMode)
        assertEquals(40.0, state.value.speedKmh, 0.0)
        verify { routeSimulator.setSpeed(40.0 / 3.6) }
        coVerify { settingsRepository.setRouteSpeed(40.0) }
    }

    @Test
    fun `playRoute shows Pro upgrade when not Pro`() = runTest {
        state.value = state.value.copy(isProActive = false)
        val ctrl = makeController(this)
        ctrl.playRoute()
        assertTrue(state.value.showProUpgrade)
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `playRoute sends ACTION_START_ROUTE when Pro and permission ok`() = runTest {
        state.value = state.value.copy(isProActive = true, waypoints = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        val ctrl = makeController(this)
        val intentSlot = slot<Intent>()
        ctrl.playRoute()
        verify { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_ROUTE, intentSlot.captured.action)
    }

    @Test
    fun `pauseRoute sends ACTION_PAUSE_ROUTE`() = runTest {
        val ctrl = makeController(this)
        val intentSlot = slot<Intent>()
        ctrl.pauseRoute()
        verify { context.startService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_PAUSE_ROUTE, intentSlot.captured.action)
    }

    @Test
    fun `loadRoute restores sorted waypoints and sets fit token`() = runTest {
        val route = RouteWithPoints(
            route = Route(id = 1, name = "R"),
            points = listOf(
                RoutePoint(routeId = 1, orderIndex = 1, latitude = 3.0, longitude = 4.0),
                RoutePoint(routeId = 1, orderIndex = 0, latitude = 1.0, longitude = 2.0)
            )
        )
        coEvery { repository.getRouteWithPoints(1) } returns route
        val ctrl = makeController(this)
        ctrl.loadRoute(1)
        advanceUntilIdle()
        assertEquals(2, state.value.waypoints.size)
        assertEquals(LatLng(1.0, 2.0), state.value.waypoints[0])
        assertEquals(LatLng(3.0, 4.0), state.value.waypoints[1])
        assertTrue(state.value.routeFitRequestToken != null)
    }

    @Test
    fun `onRouteFitConsumed clears routeFitRequestToken`() = runTest {
        state.value = state.value.copy(routeFitRequestToken = 12345L)
        val ctrl = makeController(this)
        ctrl.onRouteFitConsumed()
        assertNull(state.value.routeFitRequestToken)
    }

    @Test
    fun `playRoute does not start service when waypoints fewer than 2`() = runTest {
        state.value = state.value.copy(isProActive = true, waypoints = listOf(LatLng(1.0, 2.0)))
        val ctrl = makeController(this)
        ctrl.playRoute()
        assertTrue(state.value.mockError is MockError.InvalidInput)
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `playRoute does not start service when permission denied`() = runTest {
        state.value = state.value.copy(isProActive = true, waypoints = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        ensurePermissionResult = false
        val ctrl = makeController(this)
        ctrl.playRoute()
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `loadRoute calls clearRoute before loading new points`() = runTest {
        val existing = LatLng(9.0, 9.0)
        state.value = state.value.copy(waypoints = listOf(existing))
        val route = RouteWithPoints(
            route = Route(id = 2, name = "New"),
            points = listOf(RoutePoint(routeId = 2, orderIndex = 0, latitude = 1.0, longitude = 2.0),
                            RoutePoint(routeId = 2, orderIndex = 1, latitude = 3.0, longitude = 4.0))
        )
        coEvery { repository.getRouteWithPoints(2) } returns route
        val ctrl = makeController(this)
        ctrl.loadRoute(2)
        advanceUntilIdle()
        // The old waypoint must not appear in the new list
        assertTrue(state.value.waypoints.none { it == existing })
        assertEquals(2, state.value.waypoints.size)
    }
}
