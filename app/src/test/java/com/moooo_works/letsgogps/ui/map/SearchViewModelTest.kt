package com.moooo_works.letsgogps.ui.map

import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.SearchRepository
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchViewModelTest {

    private val searchRepository = mockk<SearchRepository>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = SearchViewModel(searchRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() {
        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.searchResults.isEmpty())
        assertNull(state.searchError)
    }

    @Test
    fun `searchLocations sets isSearching true while loading`() = runTest {
        val query = "Taipei 101"
        coEvery { searchRepository.search(query) } returns Result.success(emptyList())

        viewModel.searchLocations(query)
        assertTrue(viewModel.uiState.value.isSearching)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `searchLocations updates results on success`() = runTest {
        val query = "Taipei 101"
        val mockResults = listOf(
            GeocodedLocation("Taipei 101", "Xinyi Road", LatLng(25.0330, 121.5654))
        )
        coEvery { searchRepository.search(query) } returns Result.success(mockResults)

        viewModel.searchLocations(query)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearching)
        assertEquals(mockResults, viewModel.uiState.value.searchResults)
        assertNull(viewModel.uiState.value.searchError)
    }

    @Test
    fun `searchLocations sets error on failure`() = runTest {
        val query = "Unknown Place"
        val errorMsg = "Network Error"
        coEvery { searchRepository.search(query) } returns Result.failure(Exception(errorMsg))

        viewModel.searchLocations(query)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertEquals(errorMsg, viewModel.uiState.value.searchError)
    }

    @Test
    fun `searchLocations ignores blank query`() = runTest {
        viewModel.searchLocations("   ")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSearching)
        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
    }

    @Test
    fun `clearSearchResults clears results and error`() = runTest {
        val query = "Taipei"
        coEvery { searchRepository.search(query) } returns Result.failure(Exception("err"))
        viewModel.searchLocations(query)
        advanceUntilIdle()

        viewModel.clearSearchResults()

        assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        assertNull(viewModel.uiState.value.searchError)
    }
}
