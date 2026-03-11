package com.moooo_works.letsgogps.data.repository

import com.moooo_works.letsgogps.data.local.LocationDao
import com.moooo_works.letsgogps.data.local.RouteDao
import com.moooo_works.letsgogps.data.model.SavedLocation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationRepositoryImplTest {
    private val locationDao = mockk<LocationDao>()
    private val routeDao = mockk<RouteDao>(relaxed = true)

    @Test
    fun `observeSavedLocations applies query sort and filter arguments`() = runTest {
        val expected = listOf(
            SavedLocation(id = 1, name = "Alpha", latitude = 1.0, longitude = 1.0, isFavorite = true)
        )
        every {
            locationDao.observeSavedLocations("al", "NAME_ASC", false, true)
        } returns flowOf(expected)

        val repository = LocationRepositoryImpl(locationDao, routeDao)
        val result = repository.observeSavedLocations("al", "NAME_ASC", false, true).first()

        assertEquals(expected, result)
    }
}
