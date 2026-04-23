package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationPoint
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.service.MockLocationService
import io.mockk.coEvery
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
import com.moooo_works.letsgogps.data.engine.MockEngineError
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewModelTest {

    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val joystickOverlayManager = mockk<JoystickOverlayManager>(relaxed = true)
    private val proRepository = mockk<ProRepository>(relaxed = true)
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

    private fun createViewModel() = MapViewModel(
        mockEngine, repository, mockStateRepository, settingsRepository,
        routeSimulator, joystickOverlayManager, proRepository, context
    )

    @Test
    fun `init loads last center from settings repository`() = runTest {
        val lastCenter = LatLng(25.1, 121.1)
        lastCenterFlow.value = lastCenter
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(lastCenter, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `selectSearchResult updates center location`() = runTest {
        val viewModel = createViewModel()
        val selected = GeocodedLocation("Target", "Address", LatLng(10.0, 20.0))

        viewModel.selectSearchResult(selected)

        assertEquals(selected.latLng, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `startMocking succeeds and sends intent when permission granted`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        val viewModel = createViewModel()

        viewModel.startMocking()
        advanceUntilIdle()

        val intentSlot = slot<Intent>()
        verify(atLeast = 1) { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_SINGLE, intentSlot.captured.action)
    }

    @Test
    fun `setSpeed rejects non positive speed`() = runTest {
        val viewModel = createViewModel()
        viewModel.setSpeed(0.0)
        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.InvalidInput)
    }
}
