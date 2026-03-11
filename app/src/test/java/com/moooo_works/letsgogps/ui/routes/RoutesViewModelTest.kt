package com.moooo_works.letsgogps.ui.routes

import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLocationRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeLocationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `insert route keeps points order`() = runTest {
        repository.insertRouteWithPoints(
            "Test Route",
            listOf(
                RoutePoint(routeId = 0, orderIndex = 0, latitude = 1.0, longitude = 1.0),
                RoutePoint(routeId = 0, orderIndex = 1, latitude = 2.0, longitude = 2.0),
                RoutePoint(routeId = 0, orderIndex = 2, latitude = 3.0, longitude = 3.0)
            )
        )

        val route = repository.getRouteWithPoints(1)
        assertEquals(listOf(0, 1, 2), route!!.points.map { it.orderIndex })
        assertEquals(listOf(1.0, 2.0, 3.0), route.points.map { it.latitude })
    }

    @Test
    fun `rename route updates list`() = runTest {
        repository.insertRouteWithPoints(
            "Old Name",
            listOf(
                RoutePoint(routeId = 0, orderIndex = 0, latitude = 1.0, longitude = 1.0),
                RoutePoint(routeId = 0, orderIndex = 1, latitude = 2.0, longitude = 2.0)
            )
        )
        val viewModel = RoutesViewModel(repository)

        viewModel.renameRoute(1, "New Name")
        advanceUntilIdle()

        assertEquals("New Name", viewModel.uiState.value.routes.first().name)
    }

    @Test
    fun `delete route updates list`() = runTest {
        repository.insertRouteWithPoints(
            "Route A",
            listOf(
                RoutePoint(routeId = 0, orderIndex = 0, latitude = 1.0, longitude = 1.0),
                RoutePoint(routeId = 0, orderIndex = 1, latitude = 2.0, longitude = 2.0)
            )
        )
        val viewModel = RoutesViewModel(repository)

        viewModel.deleteRoute(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.routes.isEmpty())
    }
}

private class FakeLocationRepository : LocationRepository {
    private val routes = mutableListOf<RouteWithPoints>()
    private var nextId = 1
    private val routeFlow = MutableStateFlow<List<RouteSummary>>(emptyList())

    override fun getAllLocations(): Flow<List<SavedLocation>> = flowOf(emptyList())
    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        showHistory: Boolean,
        showFavorites: Boolean
    ): Flow<List<SavedLocation>> = flowOf(emptyList())
    override suspend fun saveLocation(location: SavedLocation) = Unit
    override suspend fun deleteLocation(location: SavedLocation) = Unit
    override suspend fun deleteNonFavorites() = Unit
    override suspend fun updateLocation(location: SavedLocation) = Unit

    override fun observeRoutes(): Flow<List<RouteSummary>> = routeFlow

    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? =
        routes.firstOrNull { it.route.id == routeId }

    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) {
        val routeId = nextId++
        val route = Route(id = routeId, name = name)
        val mappedPoints = points.mapIndexed { index, point ->
            point.copy(id = index + 1, routeId = routeId, orderIndex = index)
        }
        routes.removeAll { it.route.id == routeId }
        routes.add(RouteWithPoints(route = route, points = mappedPoints))
        emitSummaries()
    }

    override suspend fun deleteRoute(routeId: Int) {
        routes.removeAll { it.route.id == routeId }
        emitSummaries()
    }

    override suspend fun updateRouteName(routeId: Int, name: String) {
        val index = routes.indexOfFirst { it.route.id == routeId }
        if (index == -1) return
        val current = routes[index]
        routes[index] = current.copy(route = current.route.copy(name = name))
        emitSummaries()
    }

    private fun emitSummaries() {
        routeFlow.value = routes.map {
            RouteSummary(
                id = it.route.id,
                name = it.route.name,
                pointCount = it.points.size,
                createdAt = it.route.createdAt
            )
        }
    }
}
