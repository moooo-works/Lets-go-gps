package com.example.mockgps.ui.map

import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.repository.LocationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startMocking succeeds when permission granted`() = runTest {
        every { mockEngine.isMockingAllowed() } returns true
        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.startMocking()

        verify { mockEngine.setupMockProvider() }
        assertTrue(viewModel.uiState.value.isMocking)
    }

    @Test
    fun `startMocking fails when permission denied`() = runTest {
        every { mockEngine.isMockingAllowed() } returns false
        val viewModel = MapViewModel(mockEngine, repository, routeSimulator)

        viewModel.startMocking()

        verify(exactly = 0) { mockEngine.setupMockProvider() }
        assertFalse(viewModel.uiState.value.isMocking)
        assertTrue(viewModel.uiState.value.mockError != null)
    }
}
