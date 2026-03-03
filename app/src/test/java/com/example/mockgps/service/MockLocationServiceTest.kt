package com.example.mockgps.service

import android.content.Intent
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.repository.MockStateRepository
import io.mockk.mockk
import io.mockk.verify
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Service

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MockLocationServiceTest {

    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ACTION_PAUSE_ROUTE delegates to routeSimulator pause`() {
        val service = Robolectric.buildService(MockLocationService::class.java).create().get()

        // Manual injection since we aren't using Hilt in plain unit tests easily
        service.mockStateRepository = mockStateRepository
        service.mockEngine = mockEngine
        service.routeSimulator = routeSimulator

        val intent = Intent().apply { action = MockLocationService.ACTION_PAUSE_ROUTE }
        service.onStartCommand(intent, 0, 1)

        verify { routeSimulator.pause() }
    }
}
