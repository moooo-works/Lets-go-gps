package com.example.mockgps.ui.map

import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.LocationRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewModelTest {
    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val simulationStateFlow = MutableStateFlow(SimulationState.IDLE)
    private val currentLocationFlow = MutableStateFlow<com.google.android.gms.maps.model.LatLng?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.getAllLocations() } returns emptyFlow()
        every { routeSimulator.simulationState } returns simulationStateFlow
        every { routeSimulator.currentLocation } returns currentLocationFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startMocking sets NotMockAppSelected error when permission denied`() = runTest {
        every { mockEngine.isMockingAllowed() } returns false
        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.startMocking()
        advanceUntilIdle()

        verify(exactly = 0) { mockEngine.setupMockProvider() }
        assertFalse(viewModel.uiState.value.isMocking)
        assertTrue(viewModel.uiState.value.mockError is MockError.NotMockAppSelected)
    }

    @Test
    fun `startMocking sets ProviderSetupFailed error when setup fails`() = runTest {
        every { mockEngine.isMockingAllowed() } returns true
        every { mockEngine.setupMockProvider() } throws SecurityException("Setup failed")

        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.startMocking()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.mockError is MockError.ProviderSetupFailed)
        assertEquals(
            "System rejected mock provider: Setup failed",
            (viewModel.uiState.value.mockError as MockError.ProviderSetupFailed).message
        )
    }

    @Test
    fun `startMocking succeeds when permission granted and setup succeeds`() = runTest {
        every { mockEngine.isMockingAllowed() } returns true
        every { mockEngine.setupMockProvider() } just runs

        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.startMocking()
        advanceUntilIdle()

        verify { mockEngine.setupMockProvider() }
        assertTrue(viewModel.uiState.value.isMocking)
        assertTrue(viewModel.uiState.value.mockError == null)
    }

    @Test
    fun `setSpeed rejects non positive speed`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.setSpeed(0.0)

        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.InvalidInput)
    }
}
