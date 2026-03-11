package com.moooo_works.letsgogps.ui.savedlocations

import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SavedLocationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSavedLocationsRepository
    private val proRepository = mockk<ProRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSavedLocationsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filters show all locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "A", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "B", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(2, viewModel.filteredLocations.value.size)
        collectJob.cancel()
    }

    @Test
    fun `favorites only shows favorite locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "History", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "Fav", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onShowHistoryChanged(false)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `history only shows non-favorite locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "History", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "Fav", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onShowFavoritesChanged(false)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `cannot disable both filters`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository)
        viewModel.onShowHistoryChanged(false)
        viewModel.onShowFavoritesChanged(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showHistory || viewModel.uiState.value.showFavorites)
    }

    @Test
    fun `deleteLocation calls repository delete`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val location = SavedLocation(id = 1, name = "Test", latitude = 0.0, longitude = 0.0)

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        assertEquals(location, repository.deleted.single())
    }

    @Test
    fun `renameLocation calls repository update with valid name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "New Name")
        advanceUntilIdle()

        assertEquals("New Name", repository.updated.single().name)
    }

    @Test
    fun `renameLocation ignores empty name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "   ")
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `renameLocation ignores too long name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "A".repeat(41))
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }
}

private class FakeSavedLocationsRepository : LocationRepository {
    val savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val deleted = mutableListOf<SavedLocation>()
    val updated = mutableListOf<SavedLocation>()

    override fun getAllLocations(): Flow<List<SavedLocation>> = savedLocations

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        showHistory: Boolean,
        showFavorites: Boolean
    ): Flow<List<SavedLocation>> {
        val filtered = savedLocations.value
            .filter { it.name.contains(query, ignoreCase = true) }
            .filter {
                when {
                    showHistory && showFavorites -> true
                    showHistory && !showFavorites -> !it.isFavorite
                    !showHistory && showFavorites -> it.isFavorite
                    else -> false
                }
            }
            .let { list ->
                when (sortOption) {
                    SavedLocationsSortOption.NAME_ASC.name -> list.sortedBy { it.name.lowercase() }
                    else -> list.sortedByDescending { it.createdAt }
                }
            }
        return flowOf(filtered)
    }

    override suspend fun saveLocation(location: SavedLocation) = Unit

    override suspend fun deleteLocation(location: SavedLocation) {
        deleted += location
    }

    override suspend fun deleteNonFavorites() {
        savedLocations.value = savedLocations.value.filter { it.isFavorite }
    }

    override suspend fun updateLocation(location: SavedLocation) {
        updated += location
    }

    override fun observeRoutes(): Flow<List<RouteSummary>> = flowOf(emptyList())
    override suspend fun getRouteWithPoints(routeId: Int): RouteWithPoints? = null
    override suspend fun insertRouteWithPoints(name: String, points: List<RoutePoint>) = Unit
    override suspend fun deleteRoute(routeId: Int) = Unit
    override suspend fun updateRouteName(routeId: Int, name: String) = Unit
}
