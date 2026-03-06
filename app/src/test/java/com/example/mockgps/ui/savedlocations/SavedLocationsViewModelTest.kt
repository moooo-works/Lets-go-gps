package com.example.mockgps.ui.savedlocations

import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.getAllLocations() } returns emptyFlow()
        every {
            repository.observeSavedLocations(any(), any(), any(), any())
        } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default filters show all locations`() = runTest {
        val all = listOf(
            SavedLocation(id = 1, name = "A", latitude = 0.0, longitude = 0.0, isFavorite = false),
            SavedLocation(id = 2, name = "B", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )
        every {
            repository.observeSavedLocations("", SavedLocationsSortOption.RECENT.name, true, true)
        } returns flowOf(all)

        val viewModel = SavedLocationsViewModel(repository)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(all, viewModel.filteredLocations.value)
    }

    @Test
    fun `favorites only shows favorite locations`() = runTest {
        val favorites = listOf(
            SavedLocation(id = 2, name = "Fav", latitude = 0.0, longitude = 0.0, isFavorite = true)
        )
        every {
            repository.observeSavedLocations("", SavedLocationsSortOption.RECENT.name, true, true)
        } returns flowOf(emptyList())
        every {
            repository.observeSavedLocations("", SavedLocationsSortOption.RECENT.name, false, true)
        } returns flowOf(favorites)

        val viewModel = SavedLocationsViewModel(repository)
        viewModel.onShowHistoryChanged(false)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(favorites, viewModel.filteredLocations.value)
    }

    @Test
    fun `history only shows non-favorite locations`() = runTest {
        val history = listOf(
            SavedLocation(id = 1, name = "History", latitude = 0.0, longitude = 0.0, isFavorite = false)
        )
        every {
            repository.observeSavedLocations("", SavedLocationsSortOption.RECENT.name, true, true)
        } returns flowOf(emptyList())
        every {
            repository.observeSavedLocations("", SavedLocationsSortOption.RECENT.name, true, false)
        } returns flowOf(history)

        val viewModel = SavedLocationsViewModel(repository)
        viewModel.onShowFavoritesChanged(false)
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals(history, viewModel.filteredLocations.value)
    }

    @Test
    fun `cannot disable both filters`() = runTest {
        val viewModel = SavedLocationsViewModel(repository)
        viewModel.onShowHistoryChanged(false)
        viewModel.onShowFavoritesChanged(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showHistory || viewModel.uiState.value.showFavorites)
    }

    @Test
    fun `deleteLocation calls repository delete`() = runTest {
        val viewModel = SavedLocationsViewModel(repository)
        val location = SavedLocation(id = 1, name = "Test", latitude = 0.0, longitude = 0.0)

        viewModel.deleteLocation(location)
        advanceUntilIdle()

        coVerify { repository.deleteLocation(location) }
    }

    @Test
    fun `renameLocation calls repository update with valid name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)
        val newName = "New Name"

        viewModel.renameLocation(location, newName)
        advanceUntilIdle()

        coVerify { repository.updateLocation(location.copy(name = newName)) }
    }

    @Test
    fun `renameLocation ignores empty name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)

        viewModel.renameLocation(location, "   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateLocation(any()) }
    }

    @Test
    fun `renameLocation ignores too long name`() = runTest {
        val viewModel = SavedLocationsViewModel(repository)
        val location = SavedLocation(id = 1, name = "Old", latitude = 0.0, longitude = 0.0)
        val longName = "A".repeat(41)

        viewModel.renameLocation(location, longName)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateLocation(any()) }
    }
}
