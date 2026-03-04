package com.example.mockgps.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.mockgps.data.model.RoutePoint
import com.example.mockgps.data.model.RouteSummary
import com.example.mockgps.data.model.RouteWithPoints
import com.example.mockgps.data.model.SavedLocation
import com.example.mockgps.domain.repository.LocationRepository
import com.example.mockgps.domain.repository.MockStateRepository
import com.example.mockgps.domain.repository.MockStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private val locationRepository = mockk<LocationRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = kotlinx.coroutines.test.TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        every { context.packageManager } returns mockk(relaxed = true)
        every { context.packageName } returns "com.example.mockgps"
        every { context.applicationInfo } returns mockk { targetSdkVersion = 34 }

        val mockStatusFlow = MutableStateFlow(MockStatus.IDLE)
        every { mockStateRepository.mockStatus } returns mockStatusFlow

        viewModel = SettingsViewModel(locationRepository, mockStateRepository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testExportData_Success() = runTest {
        val uri = mockk<Uri>()
        val outputStream = ByteArrayOutputStream()
        every { contentResolver.openOutputStream(uri) } returns outputStream

        coEvery { locationRepository.getAllLocations() } returns flowOf(
            listOf(SavedLocation(id = 1, name = "Loc 1", latitude = 10.0, longitude = 20.0, createdAt = 123L))
        )
        coEvery { locationRepository.observeRoutes() } returns flowOf(
            listOf(RouteSummary(id = 1, name = "Route 1", pointCount = 2, createdAt = 123L))
        )
        coEvery { locationRepository.getRouteWithPoints(1) } returns RouteWithPoints(
            route = com.example.mockgps.data.model.Route(id = 1, name = "Route 1", createdAt = 123L),
            points = listOf(
                RoutePoint(id = 1, routeId = 1, orderIndex = 0, latitude = 10.0, longitude = 20.0, dwellSeconds = 5),
                RoutePoint(id = 2, routeId = 1, orderIndex = 1, latitude = 11.0, longitude = 21.0, dwellSeconds = 0)
            )
        )

        var successResult: Boolean? = null
        var errorResult: String? = null

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.exportDataToUri(uri) { success, error ->
            successResult = success
            errorResult = error
            latch.countDown()
        }
        latch.await()

        assertEquals(true, successResult)
        assertEquals(null, errorResult)
        val json = outputStream.toString()
        assertTrue(json.contains("Loc 1"))
        assertTrue(json.contains("Route 1"))
        assertTrue(json.contains("10.0"))
        assertTrue(json.contains("20.0"))
    }

    @Test
    fun testImportData_SuccessWithDeduplication() = runTest {
        val jsonInput = """
            {
              "schemaVersion": 1,
              "savedLocations": [
                {
                  "name": "Loc 1",
                  "lat": 10.0,
                  "lng": 20.0,
                  "createdAt": 123
                },
                {
                  "name": "Loc 2",
                  "lat": 30.0,
                  "lng": 40.0,
                  "createdAt": 124
                }
              ],
              "routes": [
                {
                  "name": "Route 1",
                  "points": [
                    { "lat": 10.0, "lng": 20.0, "dwellSeconds": 5 },
                    { "lat": 11.0, "lng": 21.0, "dwellSeconds": 0 }
                  ],
                  "createdAt": 123
                }
              ]
            }
        """.trimIndent()

        val uri = mockk<Uri>()
        val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
        every { contentResolver.openInputStream(uri) } returns inputStream

        // Mock existing data: Loc 1 already exists
        coEvery { locationRepository.getAllLocations() } returns flowOf(
            listOf(SavedLocation(id = 1, name = "Loc 1 Existing", latitude = 10.0, longitude = 20.0, createdAt = 123L))
        )
        coEvery { locationRepository.observeRoutes() } returns flowOf(emptyList())

        var successResult: Boolean? = null
        var msgResult: String? = null

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.importDataFromUri(uri) { success, msg ->
            successResult = success
            msgResult = msg
            latch.countDown()
        }
        latch.await()

        assertEquals(true, successResult)
        assertTrue(msgResult!!.contains("1 imported, 1 skipped"))
        assertTrue(msgResult!!.contains("1 imported, 0 skipped"))

        coVerify(exactly = 1) { locationRepository.saveLocation(any()) }
        coVerify(exactly = 1) { locationRepository.insertRouteWithPoints(any(), any()) }
    }

    @Test
    fun testGenerateDiagnostics() {
        val diag = viewModel.generateDiagnostics()
        assertTrue(diag.contains("MockGPS Diagnostics"))
        assertTrue(diag.contains("Mock Status: IDLE"))
        assertTrue(diag.contains("Target SDK: 34"))
    }
}
