package com.moooo_works.letsgogps.service

import android.content.Intent
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MockLocationServiceTest {

    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val mockStatusFlow = MutableStateFlow(MockStatus.IDLE)
    private val currentMockLocationFlow = MutableStateFlow<LatLng?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { mockStateRepository.mockStatus } returns mockStatusFlow
        every { mockStateRepository.currentMockLocation } returns currentMockLocationFlow
        every { settingsRepository.observeAltitude() } returns flowOf(15.0)
        every { settingsRepository.observeRandomAltitude() } returns flowOf(false)
        every { settingsRepository.observeCoordinateJitter() } returns flowOf(false)
        every { settingsRepository.observeRouteSpeed() } returns flowOf(5.0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `service reacts to repository location changes when mocking is active`() = runTest {
        val controller = Robolectric.buildService(MockLocationService::class.java)
        val service = controller.create().get()

        service.mockStateRepository = mockStateRepository
        service.mockEngine = mockEngine
        service.routeSimulator = routeSimulator
        service.settingsRepository = settingsRepository

        // Set status to MOCKING
        mockStatusFlow.value = MockStatus.MOCKING
        
        // Simulate rapid joystick movements
        val point1 = LatLng(25.0, 121.0)
        val point2 = LatLng(25.0001, 121.0001)
        
        currentMockLocationFlow.value = point1
        advanceUntilIdle()
        
        currentMockLocationFlow.value = point2
        advanceUntilIdle()

        // Verify that setLocation was called for both points
        verify { mockEngine.setLocation(point1.latitude, point1.longitude, any(), any(), any()) }
        verify { mockEngine.setLocation(point2.latitude, point2.longitude, any(), any(), any()) }
    }

    @Test
    fun `ACTION_PAUSE_ROUTE delegates to routeSimulator pause`() {
        val service = Robolectric.buildService(MockLocationService::class.java).create().get()
        service.mockStateRepository = mockStateRepository
        service.mockEngine = mockEngine
        service.routeSimulator = routeSimulator
        service.settingsRepository = settingsRepository

        val intent = Intent().apply { action = MockLocationService.ACTION_PAUSE_ROUTE }
        service.onStartCommand(intent, 0, 1)

        verify { routeSimulator.pause() }
    }
}
