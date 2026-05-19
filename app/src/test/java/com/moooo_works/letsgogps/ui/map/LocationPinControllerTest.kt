package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationPinControllerTest {

    private val repository = mockk<LocationRepository>(relaxed = true)
    private val state = MutableStateFlow(MapUiState())
    private val location = SavedLocation(id = 1, name = "A", latitude = 10.0, longitude = 20.0)

    @Test
    fun `select sets selectedLocation`() = runTest {
        val ctrl = LocationPinController(state, this, repository)
        ctrl.select(location)
        assertEquals(location, state.value.selectedLocation)
    }

    @Test
    fun `dismiss clears selectedLocation and edit dialog`() = runTest {
        state.value = state.value.copy(selectedLocation = location, showEditLocationDialog = true)
        val ctrl = LocationPinController(state, this, repository)
        ctrl.dismiss()
        assertNull(state.value.selectedLocation)
        assertFalse(state.value.showEditLocationDialog)
    }

    @Test
    fun `showEditDialog sets showEditLocationDialog true`() = runTest {
        val ctrl = LocationPinController(state, this, repository)
        ctrl.showEditDialog()
        assertTrue(state.value.showEditLocationDialog)
    }

    @Test
    fun `dismissEditDialog sets showEditLocationDialog false`() = runTest {
        state.value = state.value.copy(showEditLocationDialog = true)
        val ctrl = LocationPinController(state, this, repository)
        ctrl.dismissEditDialog()
        assertFalse(state.value.showEditLocationDialog)
    }

    @Test
    fun `delete calls repository and clears selection`() = runTest {
        state.value = state.value.copy(selectedLocation = location)
        val ctrl = LocationPinController(state, this, repository)
        ctrl.delete()
        advanceUntilIdle()
        coVerify { repository.deleteLocation(location) }
        assertNull(state.value.selectedLocation)
    }

    @Test
    fun `delete is no-op when nothing selected`() = runTest {
        val ctrl = LocationPinController(state, this, repository)
        ctrl.delete()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.deleteLocation(any()) }
    }

    @Test
    fun `toggleFavorite inverts isFavorite and persists`() = runTest {
        state.value = state.value.copy(selectedLocation = location.copy(isFavorite = false))
        val ctrl = LocationPinController(state, this, repository)
        ctrl.toggleFavorite()
        advanceUntilIdle()
        assertTrue(state.value.selectedLocation!!.isFavorite)
        coVerify { repository.updateLocation(location.copy(isFavorite = true)) }
    }

    @Test
    fun `updateDetails trims fields and closes dialog`() = runTest {
        state.value = state.value.copy(selectedLocation = location, showEditLocationDialog = true)
        val ctrl = LocationPinController(state, this, repository)
        ctrl.updateDetails("  New Name  ", "  desc  ")
        advanceUntilIdle()
        assertEquals("New Name", state.value.selectedLocation!!.name)
        assertEquals("desc", state.value.selectedLocation!!.description)
        assertFalse(state.value.showEditLocationDialog)
    }

    @Test
    fun `saveIfNeeded saves when no nearby location exists`() = runTest {
        state.value = state.value.copy(savedLocations = emptyList())
        val ctrl = LocationPinController(state, this, repository)
        ctrl.saveIfNeeded(LatLng(10.0, 20.0))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.saveLocation(any()) }
    }

    @Test
    fun `saveIfNeeded skips when location already exists within epsilon`() = runTest {
        val near = SavedLocation(id = 2, name = "B", latitude = 10.00005, longitude = 20.00005)
        state.value = state.value.copy(savedLocations = listOf(near))
        val ctrl = LocationPinController(state, this, repository)
        ctrl.saveIfNeeded(LatLng(10.0, 20.0))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.saveLocation(any()) }
    }
}
