package com.moooo_works.letsgogps.ui.savedlocations

import com.moooo_works.letsgogps.data.model.FolderWithCount
import com.moooo_works.letsgogps.data.model.LocationFolder
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import io.mockk.every
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedLocationsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSavedLocationsRepository
    private val proRepository = mockk<ProRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSavedLocationsRepository()
        every { settingsRepository.hasSeenOnboarding() } returns flowOf(true)
        every { settingsRepository.hasSeenSortTip() } returns flowOf(true)
        every { settingsRepository.hasSeenFolderTip() } returns flowOf(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filter shows all locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "A", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "B", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(2, viewModel.filteredLocations.value.size)
        collectJob.cancel()
    }

    @Test
    fun `favorites filter shows only favorite locations`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "History", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "Fav", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onFilterChanged(LocationFilter.Favorites)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `folder filter shows only locations in that folder`() = runTest {
        repository.savedLocations.value = listOf(
            SavedLocation(id = 1, name = "In Folder", latitude = 0.0, longitude = 0.0, folderId = 10),
            SavedLocation(id = 2, name = "No Folder", latitude = 0.0, longitude = 0.0, folderId = null)
        )

        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val collectJob = backgroundScope.launch { viewModel.filteredLocations.collect { } }
        viewModel.onFilterChanged(LocationFilter.Folder(folderId = 10, folderName = "Test"))
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(listOf(1), viewModel.filteredLocations.value.map { it.id })
        collectJob.cancel()
    }

    @Test
    fun `deleteLocation calls repository delete`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Test", latitude = 0.0, longitude = 0.0)

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        assertEquals(location, repository.deleted.single())
    }

    @Test
    fun `renameLocation calls repository update with valid name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "New Name")
        advanceUntilIdle()

        assertEquals("New Name", repository.updated.single().name)
    }

    @Test
    fun `renameLocation ignores empty name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "   ")
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `renameLocation ignores too long name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "A".repeat(41))
        advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `enterBatchSelection activates batch mode with one id selected`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)

        viewModel.enterBatchSelection(locationId = 5)
        advanceUntilIdle()

        assertTrue(viewModel.batchSelection.value.active)
        assertEquals(setOf(5), viewModel.batchSelection.value.selectedIds)
    }

    @Test
    fun `toggleBatchSelection adds and removes ids`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.enterBatchSelection(locationId = 1)
        viewModel.toggleBatchSelection(locationId = 2)
        viewModel.toggleBatchSelection(locationId = 1)
        advanceUntilIdle()

        assertEquals(setOf(2), viewModel.batchSelection.value.selectedIds)
    }

    @Test
    fun `exitBatchSelection clears batch mode`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.enterBatchSelection(locationId = 1)
        viewModel.exitBatchSelection()
        advanceUntilIdle()

        assertFalse(viewModel.batchSelection.value.active)
        assertTrue(viewModel.batchSelection.value.selectedIds.isEmpty())
    }

    @Test
    fun `deleteFolder resets filter to All when current folder is deleted`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.onFilterChanged(LocationFilter.Folder(folderId = 99, folderName = "Old"))
        viewModel.deleteFolder(id = 99)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.filter is LocationFilter.All)
    }

    @Test
    fun `moveBatchToFolder calls repository and exits batch mode`() = runTest {
        val viewModel = SavedLocationsViewModel(repository, proRepository, settingsRepository)
        viewModel.enterBatchSelection(locationId = 1)
        viewModel.toggleBatchSelection(locationId = 2)

        viewModel.moveBatchToFolder(folderId = 5)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1, 2) to 5), repository.movedLocations.map { it.first.sorted() to it.second })
        assertFalse(viewModel.batchSelection.value.active)
    }
}

private class FakeSavedLocationsRepository : LocationRepository {
    val savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val deleted = mutableListOf<SavedLocation>()
    val updated = mutableListOf<SavedLocation>()
    val deletedFolderIds = mutableListOf<Int>()
    val movedLocations = mutableListOf<Pair<List<Int>, Int?>>()

    override fun getAllLocations(): Flow<List<SavedLocation>> = savedLocations

    override fun observeSavedLocations(
        query: String,
        sortOption: String,
        filterMode: String,
        folderId: Int
    ): Flow<List<SavedLocation>> {
        val filtered = savedLocations.value
            .filter { it.name.contains(query, ignoreCase = true) }
            .filter {
                when (filterMode) {
                    "FAVORITES" -> it.isFavorite
                    "FOLDER" -> it.folderId == folderId
                    else -> true
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

    override fun observeFolders(): Flow<List<LocationFolder>> = flowOf(emptyList())
    override fun observeFoldersWithCount(): Flow<List<FolderWithCount>> = flowOf(emptyList())
    override suspend fun createFolder(name: String): Int = 0
    override suspend fun renameFolder(id: Int, name: String) = Unit
    override suspend fun deleteFolder(id: Int) { deletedFolderIds += id }
    override suspend fun moveLocationsToFolder(locationIds: List<Int>, folderId: Int?) {
        movedLocations += locationIds to folderId
    }
}
