package com.moooo_works.letsgogps.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteSummary
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.ui.settings.ImportPreview
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
import kotlinx.coroutines.test.runTest
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
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val proRepository = mockk<ProRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = kotlinx.coroutines.test.TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        every { context.packageManager } returns mockk(relaxed = true)
        every { context.packageName } returns "com.moooo_works.letsgogps"
        every { context.applicationInfo } returns mockk { targetSdkVersion = 34 }

        val mockStatusFlow = MutableStateFlow(MockStatus.IDLE)
        every { mockStateRepository.mockStatus } returns mockStatusFlow
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        every { proRepository.isProActive } returns MutableStateFlow(true)

        viewModel = SettingsViewModel(locationRepository, mockStateRepository, settingsRepository, mockEngine, proRepository, context)
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
            route = com.moooo_works.letsgogps.data.model.Route(id = 1, name = "Route 1", createdAt = 123L),
            points = listOf(
                RoutePoint(id = 1, routeId = 1, orderIndex = 0, latitude = 10.0, longitude = 20.0, dwellSeconds = 5),
                RoutePoint(id = 2, routeId = 1, orderIndex = 1, latitude = 11.0, longitude = 21.0, dwellSeconds = 0)
            )
        )

        var successResult: Boolean? = null
        var errorResult: String? = null

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.exportDataToUri(uri, true, true) { success, error ->
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
    fun testParseImportData_Success() = runTest {
        val jsonInput = """
            {
              "schemaVersion": 2,
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

        var successResult: Boolean? = null
        var previewResult: ImportPreview? = null
        var errorResult: String? = null

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.parseImportData(uri) { success, preview, error ->
            successResult = success
            previewResult = preview
            errorResult = error
            latch.countDown()
        }
        latch.await()

        assertEquals(true, successResult)
        assertEquals(2, previewResult!!.savedLocationsCount)
        assertEquals(1, previewResult!!.routesCount)
        assertEquals(2, previewResult!!.schemaVersion)
        assertEquals(null, errorResult)
    }

    @Test
    fun testApplyImportData_SavedLocationsDeduplication_19m_21m() = runTest {
        val jsonInput = """
            {
              "schemaVersion": 2,
              "savedLocations": [
                {
                  "name": "Loc 19m",
                  "lat": 25.00017,
                  "lng": 121.0000,
                  "createdAt": 123
                },
                {
                  "name": "Loc 21m",
                  "lat": 25.00019,
                  "lng": 121.0000,
                  "createdAt": 124
                }
              ],
              "routes": []
            }
        """.trimIndent()

        val uri = mockk<Uri>()
        val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
        every { contentResolver.openInputStream(uri) } returns inputStream

        // Mock existing data: Center point (25.0000, 121.0000)
        coEvery { locationRepository.getAllLocations() } returns flowOf(
            listOf(SavedLocation(id = 1, name = "Center", latitude = 25.0000, longitude = 121.0000, createdAt = 123L))
        )
        coEvery { locationRepository.observeRoutes() } returns flowOf(emptyList())

        var successResult: Boolean? = null
        var msgResult: String? = null

        val preview = ImportPreview(uri, 2, 2, 0)

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.applyImportData(preview) { success, msg ->
            successResult = success
            msgResult = msg
            latch.countDown()
        }
        latch.await()

        assertEquals(true, successResult)
        // Loc 19m should be skipped (deduplicated), Loc 21m should be imported
        assertTrue(msgResult!!.contains("Locations: 1 imported, 1 skipped"))

        coVerify(exactly = 1) { locationRepository.saveLocation(match { it.name == "Loc 21m" }) }
    }

    @Test
    fun testApplyImportData_RoutesDeduplication_SamePoints_RenameDiffPoints() = runTest {
        val jsonInput = """
            {
              "schemaVersion": 1,
              "savedLocations": [],
              "routes": [
                {
                  "name": "Route A",
                  "points": [
                    { "lat": 10.0, "lng": 20.0, "dwellSeconds": 5 },
                    { "lat": 11.0, "lng": 21.0, "dwellSeconds": 0 }
                  ],
                  "createdAt": 123
                },
                {
                  "name": "Route B",
                  "points": [
                    { "lat": 30.0, "lng": 40.0, "dwellSeconds": 5 },
                    { "lat": 31.0, "lng": 41.0, "dwellSeconds": 0 }
                  ],
                  "createdAt": 124
                }
              ]
            }
        """.trimIndent()

        val uri = mockk<Uri>()
        val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
        every { contentResolver.openInputStream(uri) } returns inputStream

        coEvery { locationRepository.getAllLocations() } returns flowOf(emptyList())
        coEvery { locationRepository.observeRoutes() } returns flowOf(
            listOf(
                RouteSummary(id = 1, name = "Route A", pointCount = 2, createdAt = 123L),
                RouteSummary(id = 2, name = "Route B", pointCount = 2, createdAt = 124L)
            )
        )

        coEvery { locationRepository.getRouteWithPoints(1) } returns RouteWithPoints(
            route = com.moooo_works.letsgogps.data.model.Route(id = 1, name = "Route A", createdAt = 123L),
            points = listOf(
                RoutePoint(id = 1, routeId = 1, orderIndex = 0, latitude = 10.0, longitude = 20.0, dwellSeconds = 5),
                RoutePoint(id = 2, routeId = 1, orderIndex = 1, latitude = 11.0, longitude = 21.0, dwellSeconds = 0)
            )
        )
        // Route B has different points
        coEvery { locationRepository.getRouteWithPoints(2) } returns RouteWithPoints(
            route = com.moooo_works.letsgogps.data.model.Route(id = 2, name = "Route B", createdAt = 124L),
            points = listOf(
                RoutePoint(id = 3, routeId = 2, orderIndex = 0, latitude = 50.0, longitude = 50.0, dwellSeconds = 5),
                RoutePoint(id = 4, routeId = 2, orderIndex = 1, latitude = 51.0, longitude = 51.0, dwellSeconds = 0)
            )
        )

        var successResult: Boolean? = null
        var msgResult: String? = null

        val preview = ImportPreview(uri, 1, 0, 2)

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.applyImportData(preview) { success, msg ->
            successResult = success
            msgResult = msg
            latch.countDown()
        }
        latch.await()

        assertEquals(true, successResult)
        // Route A skipped (same name & points), Route B imported with rename
        assertTrue(msgResult!!.contains("Routes: 1 imported, 1 skipped"))

        coVerify(exactly = 0) { locationRepository.insertRouteWithPoints("Route A", any()) }
        coVerify(exactly = 1) { locationRepository.insertRouteWithPoints("Route B (imported 2)", any()) }
    }

    @Test
    fun testGenerateDiagnostics() {
        val diag = viewModel.generateDiagnostics()
        assertTrue(diag.contains("MockGPS Diagnostics"))
        assertTrue(diag.contains("Mock Status: IDLE"))
        assertTrue(diag.contains("Target SDK: 34"))
    }

    @Test
    fun testExportData_Failure_NullOutputStream() = runTest {
        val uri = mockk<Uri>()
        every { contentResolver.openOutputStream(uri) } returns null

        coEvery { locationRepository.getAllLocations() } returns flowOf(emptyList())
        coEvery { locationRepository.observeRoutes() } returns flowOf(emptyList())

        var successResult: Boolean? = null
        var errorResult: String? = null

        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.exportDataToUri(uri, true, true) { success, error ->
            successResult = success
            errorResult = error
            latch.countDown()
        }

        latch.await()

        assertEquals(false, successResult)
        assertTrue(errorResult?.contains("openOutputStream returned null") == true)
    }
}
