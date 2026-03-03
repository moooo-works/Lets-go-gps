package com.example.mockgps.ui.savedlocations

import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
