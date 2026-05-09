package com.moooo_works.letsgogps.ui.routes

import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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

    // ─── Selection mode & merge ──────────────────────────────────────────────

    private fun seedRoute(name: String, vararg coords: Pair<Double, Double>) {
        val points = coords.mapIndexed { idx, (lat, lng) ->
            RoutePoint(routeId = 0, orderIndex = idx, latitude = lat, longitude = lng)
        }
        runBlocking { repository.insertRouteWithPoints(name, points) }
    }

    @Test
    fun `enterSelectionMode preselects the long-pressed route`() = runTest {
        seedRoute("A", 1.0 to 1.0)
        seedRoute("B", 2.0 to 2.0)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        vm.enterSelectionMode(2)

        assertTrue(vm.uiState.value.isSelectionMode)
        assertEquals(listOf(2), vm.uiState.value.selectedRouteIds)
    }

    @Test
    fun `toggleSelection adds and removes preserving order`() = runTest {
        seedRoute("A", 1.0 to 1.0)
        seedRoute("B", 2.0 to 2.0)
        seedRoute("C", 3.0 to 3.0)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        vm.enterSelectionMode(1)
        vm.toggleSelection(3)
        vm.toggleSelection(2)
        assertEquals(listOf(1, 3, 2), vm.uiState.value.selectedRouteIds)

        vm.toggleSelection(3)
        assertEquals(listOf(1, 2), vm.uiState.value.selectedRouteIds)
    }

    @Test
    fun `mergeSelected concatenates points in selection order`() = runTest {
        seedRoute("A", 1.0 to 1.0, 1.5 to 1.5)
        seedRoute("B", 2.0 to 2.0)
        seedRoute("C", 3.0 to 3.0, 3.5 to 3.5, 3.9 to 3.9)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        // Selection order C → A → B; merged route should follow that order.
        vm.enterSelectionMode(3)
        vm.toggleSelection(1)
        vm.toggleSelection(2)
        vm.mergeSelected("Combined")
        advanceUntilIdle()

        val routes = vm.uiState.value.routes
        val merged = routes.firstOrNull { it.name == "Combined" }
        assertEquals("merge created a new route", true, merged != null)
        // Total points = 3 + 2 + 1 = 6, in C → A → B order.
        val mergedPoints = repository.getRouteWithPoints(merged!!.id)!!.points
            .sortedBy { it.orderIndex }
            .map { it.latitude }
        assertEquals(listOf(3.0, 3.5, 3.9, 1.0, 1.5, 2.0), mergedPoints)
        // orderIndex must be re-sequenced from 0.
        assertEquals(
            (0..5).toList(),
            repository.getRouteWithPoints(merged.id)!!.points.sortedBy { it.orderIndex }.map { it.orderIndex }
        )
    }

    @Test
    fun `mergeSelected is a no-op when fewer than two routes selected`() = runTest {
        seedRoute("A", 1.0 to 1.0)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        vm.enterSelectionMode(1)
        vm.mergeSelected("won't happen")
        advanceUntilIdle()

        // Still just the original single route.
        assertEquals(listOf("A"), vm.uiState.value.routes.map { it.name })
    }

    @Test
    fun `exitSelectionMode clears state`() = runTest {
        seedRoute("A", 1.0 to 1.0)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        vm.enterSelectionMode(1)
        vm.exitSelectionMode()

        assertTrue(!vm.uiState.value.isSelectionMode)
        assertTrue(vm.uiState.value.selectedRouteIds.isEmpty())
    }

    @Test
    fun `buildDefaultMergedName joins names`() = runTest {
        seedRoute("Morning", 1.0 to 1.0)
        seedRoute("Noon", 2.0 to 2.0)
        seedRoute("Night", 3.0 to 3.0)
        val vm = RoutesViewModel(repository)
        advanceUntilIdle()

        vm.enterSelectionMode(1)
        vm.toggleSelection(2)
        assertEquals("Morning + Noon", vm.buildDefaultMergedName())

        vm.toggleSelection(3)
        assertEquals("Morning + Noon (+1 more)", vm.buildDefaultMergedName())
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
        filterMode: String,
        folderId: Int
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

    override fun observeFolders(): Flow<List<LocationFolder>> = flowOf(emptyList())
    override fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = flowOf(emptyList())
    override suspend fun createFolder(name: String): Int = 0
    override suspend fun renameFolder(id: Int, name: String) = Unit
    override suspend fun deleteFolder(id: Int) = Unit
    override suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?) = Unit
}
