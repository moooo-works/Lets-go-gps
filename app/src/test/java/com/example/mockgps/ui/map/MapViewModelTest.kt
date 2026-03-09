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
import com.example.mockgps.domain.repository.GeocodedLocation
import com.example.mockgps.domain.repository.MockStatus
import com.example.mockgps.service.MockLocationService
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
import com.example.mockgps.data.engine.MockEngineError
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapViewModelTest {

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
    fun `init loads last center from settings repository`() = runTest {
        val lastCenter = LatLng(25.1, 121.1)
        lastCenterFlow.value = lastCenter
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        advanceUntilIdle()
        assertEquals(lastCenter, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `searchLocations updates uiState with results`() = runTest {
        val query = "Taipei 101"
        val mockResults = listOf(
            GeocodedLocation("Taipei 101", "Xinyi Road", LatLng(25.0330, 121.5654))
        )
        coEvery { searchRepository.search(query) } returns Result.success(mockResults)
        
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        
        viewModel.searchLocations(query)
        assertTrue(viewModel.uiState.value.isSearching)
        
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(mockResults, viewModel.uiState.value.searchResults)
    }

    @Test
    fun `searchLocations sets error on failure`() = runTest {
        val query = "Unknown Place"
        val errorMsg = "Network Error"
        coEvery { searchRepository.search(query) } returns Result.failure(Exception(errorMsg))
        
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        
        viewModel.searchLocations(query)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(errorMsg, viewModel.uiState.value.searchError)
    }

    @Test
    fun `selectSearchResult updates center location and clears results`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        val selected = GeocodedLocation("Target", "Address", LatLng(10.0, 20.0))
        
        viewModel.selectSearchResult(selected)
        
        assertEquals(selected.latLng, viewModel.uiState.value.centerLocation)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
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
    fun `setSpeed rejects non positive speed`() = runTest {
        val viewModel = MapViewModel(mockEngine, repository, mockStateRepository, settingsRepository, searchRepository, routeSimulator, context)
        viewModel.setSpeed(0.0)
        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
        assertTrue(viewModel.uiState.value.mockError is MockError.InvalidInput)
    }
}
