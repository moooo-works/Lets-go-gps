package com.example.mockgps.ui.routes

import com.example.mockgps.data.model.Route
import com.example.mockgps.domain.repository.LocationRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoutesViewModelTest {

    private lateinit var repository: LocationRepository
    private lateinit var viewModel: RoutesViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)

        val routesFlow = MutableStateFlow(
            listOf(Route(id = 1, name = "Route 1"))
        )
        every { repository.getAllRoutes() } returns routesFlow

        viewModel = RoutesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads routes from repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(1, state.routes.size)
        assertEquals("Route 1", state.routes[0].name)
    }

    @Test
    fun `deleteRoute calls repository delete`() = runTest {
        val route = Route(id = 1, name = "Route 1")
        viewModel.deleteRoute(route)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.deleteRoute(route) }
    }

    @Test
    fun `renameRoute calls repository update with valid name`() = runTest {
        val route = Route(id = 1, name = "Route 1")
        viewModel.renameRoute(route, "New Route Name")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { repository.updateRoute(route.copy(name = "New Route Name")) }
    }

    @Test
    fun `renameRoute does not call repository if name is blank`() = runTest {
        val route = Route(id = 1, name = "Route 1")
        viewModel.renameRoute(route, "   ")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.updateRoute(any()) }
    }
}
