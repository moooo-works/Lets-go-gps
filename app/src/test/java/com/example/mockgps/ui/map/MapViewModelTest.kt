package com.example.mockgps.ui.map

import android.content.Context
import android.content.Intent
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.SimulationPoint
import com.example.mockgps.domain.MockPermissionStatus
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.LocationRepository
import com.example.mockgps.domain.repository.MockStateRepository
import com.example.mockgps.domain.repository.SettingsRepository
import com.example.mockgps.domain.repository.SearchRepository
import com.example.mockgps.domain.repository.MockStatus
import com.example.mockgps.service.MockLocationService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.mockgps.data.engine.MockEngineError
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewModelTest {
    @Test
    fun `init loads last center from settings repository`() = runTest {
        val lastCenter = LatLng(25.1, 121.1)
        lastCenterFlow.value = lastCenter
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        advanceUntilIdle()
        assertEquals(lastCenter, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `onCameraMove saves center to settings repository after debounce`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        val newCenter = LatLng(26.0, 122.0)

        viewModel.onCameraMove(newCenter)
        advanceUntilIdle()

        verify { kotlinx.coroutines.runBlocking { settingsRepository.setLastCenter(newCenter) } }
    }

    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val searchRepository = mockk<SearchRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val simulationStateFlow = MutableStateFlow(SimulationState.IDLE)
    private val currentLocationFlow = MutableStateFlow<SimulationPoint?>(null)

    private val mockStatusFlow = MutableStateFlow(MockStatus.IDLE)
    private val currentMockLocationFlow = MutableStateFlow<LatLng?>(null)
    private val mockErrorFlow = MutableStateFlow<MockEngineError?>(null)
    private val lastCenterFlow = MutableStateFlow<LatLng?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.getAllLocations() } returns emptyFlow()
        every { routeSimulator.simulationState } returns simulationStateFlow
        every { routeSimulator.currentLocation } returns currentLocationFlow
        every { mockStateRepository.mockStatus } returns mockStatusFlow
        every { mockStateRepository.currentMockLocation } returns currentMockLocationFlow
        every { mockStateRepository.mockError } returns mockErrorFlow
        every { settingsRepository.observeLastCenter() } returns lastCenterFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startMocking sets NotMockAppSelected error when permission denied`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.NotAllowed
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.startMocking()
        advanceUntilIdle()

        verify(exactly = 0) { context.startService(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.NotMockAppSelected)
    }

    @Test
    fun `startMocking sets PermissionCheckFailed when permission check throws`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.CheckFailed(IllegalStateException("AppOps unavailable"))
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.startMocking()
        advanceUntilIdle()

        verify(exactly = 0) { context.startService(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.PermissionCheckFailed)
        assertFalse(viewModel.uiState.value.mockError is MockError.NotMockAppSelected)
    }

    @Test
    fun `startMocking succeeds and sends intent when permission granted`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed

        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.startMocking()
        advanceUntilIdle()

        val intentSlot = slot<Intent>()
        verify(atLeast = 1) { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_SINGLE, intentSlot.captured.action)
    }

    @Test
    fun `playRoute sends intent when permission granted`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed

        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.playRoute()
        advanceUntilIdle()

        val intentSlot = slot<Intent>()
        verify(atLeast = 1) { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_ROUTE, intentSlot.captured.action)
    }

    @Test
    fun `stopMocking sends stop intent`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.stopMocking()
        advanceUntilIdle()

        val intentSlot = slot<Intent>()
        verify { context.startService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_STOP, intentSlot.captured.action)
    }

    @Test
    fun `mock status updates UI state`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        mockStatusFlow.value = MockStatus.MOCKING
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMocking)

        mockStatusFlow.value = MockStatus.ROUTE_PLAYING
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMocking)
        assertEquals(SimulationState.PLAYING, viewModel.uiState.value.simulationState)
    }

    @Test
    fun `setSpeed rejects non positive speed`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)

        viewModel.setSpeed(0.0)

        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.InvalidInput)
    }
}
