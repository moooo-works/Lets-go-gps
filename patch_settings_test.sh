cat << 'INNER_EOF' > app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt.patch
--- app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt
+++ app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt
@@ -10,6 +10,7 @@
 import com.example.mockgps.domain.repository.LocationRepository
 import com.example.mockgps.domain.repository.MockStateRepository
 import com.example.mockgps.domain.repository.MockStatus
+import com.example.mockgps.ui.settings.ImportPreview
 import io.mockk.coEvery
 import io.mockk.coVerify
 import io.mockk.every
@@ -88,7 +89,7 @@
         var errorResult: String? = null

         val latch = java.util.concurrent.CountDownLatch(1)
-        viewModel.exportDataToUri(uri) { success, error ->
+        viewModel.exportDataToUri(uri, true, true) { success, error ->
             successResult = success
             errorResult = error
             latch.countDown()
@@ -105,10 +106,10 @@
     }

     @Test
-    fun testImportData_SuccessWithDeduplication() = runTest {
+    fun testParseImportData_Success() = runTest {
         val jsonInput = """
             {
-              "schemaVersion": 1,
+              "schemaVersion": 2,
               "savedLocations": [
                 {
                   "name": "Loc 1",
@@ -140,24 +141,148 @@
         val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
         every { contentResolver.openInputStream(uri) } returns inputStream

-        // Mock existing data: Loc 1 already exists
-        coEvery { locationRepository.getAllLocations() } returns flowOf(
-            listOf(SavedLocation(id = 1, name = "Loc 1 Existing", latitude = 10.0, longitude = 20.0, createdAt = 123L))
-        )
-        coEvery { locationRepository.observeRoutes() } returns flowOf(emptyList())
-
         var successResult: Boolean? = null
-        var msgResult: String? = null
+        var previewResult: ImportPreview? = null
+        var errorResult: String? = null

         val latch = java.util.concurrent.CountDownLatch(1)
-        viewModel.importDataFromUri(uri) { success, msg ->
+        viewModel.parseImportData(uri) { success, preview, error ->
             successResult = success
-            msgResult = msg
+            previewResult = preview
+            errorResult = error
             latch.countDown()
         }
         latch.await()

         assertEquals(true, successResult)
-        assertTrue(msgResult!!.contains("1 imported, 1 skipped"))
-        assertTrue(msgResult!!.contains("1 imported, 0 skipped"))
-
-        coVerify(exactly = 1) { locationRepository.saveLocation(any()) }
-        coVerify(exactly = 1) { locationRepository.insertRouteWithPoints(any(), any()) }
+        assertEquals(2, previewResult!!.savedLocationsCount)
+        assertEquals(1, previewResult!!.routesCount)
+        assertEquals(2, previewResult!!.schemaVersion)
+        assertEquals(null, errorResult)
+    }
+
+    @Test
+    fun testApplyImportData_SavedLocationsDeduplication_19m_21m() = runTest {
+        val jsonInput = """
+            {
+              "schemaVersion": 2,
+              "savedLocations": [
+                {
+                  "name": "Loc 19m",
+                  "lat": 25.00017,
+                  "lng": 121.0000,
+                  "createdAt": 123
+                },
+                {
+                  "name": "Loc 21m",
+                  "lat": 25.00019,
+                  "lng": 121.0000,
+                  "createdAt": 124
+                }
+              ],
+              "routes": []
+            }
+        """.trimIndent()
+
+        val uri = mockk<Uri>()
+        val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
+        every { contentResolver.openInputStream(uri) } returns inputStream
+
+        // Mock existing data: Center point (25.0000, 121.0000)
+        coEvery { locationRepository.getAllLocations() } returns flowOf(
+            listOf(SavedLocation(id = 1, name = "Center", latitude = 25.0000, longitude = 121.0000, createdAt = 123L))
+        )
+        coEvery { locationRepository.observeRoutes() } returns flowOf(emptyList())
+
+        var successResult: Boolean? = null
+        var msgResult: String? = null
+
+        val preview = ImportPreview(uri, 2, 2, 0)
+
+        val latch = java.util.concurrent.CountDownLatch(1)
+        viewModel.applyImportData(preview) { success, msg ->
+            successResult = success
+            msgResult = msg
+            latch.countDown()
+        }
+        latch.await()
+
+        assertEquals(true, successResult)
+        // Loc 19m should be skipped (deduplicated), Loc 21m should be imported
+        assertTrue(msgResult!!.contains("Locations: 1 imported, 1 skipped"))
+
+        coVerify(exactly = 1) { locationRepository.saveLocation(match { it.name == "Loc 21m" }) }
+    }
+
+    @Test
+    fun testApplyImportData_RoutesDeduplication_SamePoints_RenameDiffPoints() = runTest {
+        val jsonInput = """
+            {
+              "schemaVersion": 1,
+              "savedLocations": [],
+              "routes": [
+                {
+                  "name": "Route A",
+                  "points": [
+                    { "lat": 10.0, "lng": 20.0, "dwellSeconds": 5 },
+                    { "lat": 11.0, "lng": 21.0, "dwellSeconds": 0 }
+                  ],
+                  "createdAt": 123
+                },
+                {
+                  "name": "Route B",
+                  "points": [
+                    { "lat": 30.0, "lng": 40.0, "dwellSeconds": 5 },
+                    { "lat": 31.0, "lng": 41.0, "dwellSeconds": 0 }
+                  ],
+                  "createdAt": 124
+                }
+              ]
+            }
+        """.trimIndent()
+
+        val uri = mockk<Uri>()
+        val inputStream = ByteArrayInputStream(jsonInput.toByteArray())
+        every { contentResolver.openInputStream(uri) } returns inputStream
+
+        coEvery { locationRepository.getAllLocations() } returns flowOf(emptyList())
+        coEvery { locationRepository.observeRoutes() } returns flowOf(
+            listOf(
+                RouteSummary(id = 1, name = "Route A", pointCount = 2, createdAt = 123L),
+                RouteSummary(id = 2, name = "Route B", pointCount = 2, createdAt = 124L)
+            )
+        )
+
+        coEvery { locationRepository.getRouteWithPoints(1) } returns RouteWithPoints(
+            route = com.example.mockgps.data.model.Route(id = 1, name = "Route A", createdAt = 123L),
+            points = listOf(
+                RoutePoint(id = 1, routeId = 1, orderIndex = 0, latitude = 10.0, longitude = 20.0, dwellSeconds = 5),
+                RoutePoint(id = 2, routeId = 1, orderIndex = 1, latitude = 11.0, longitude = 21.0, dwellSeconds = 0)
+            )
+        )
+        // Route B has different points
+        coEvery { locationRepository.getRouteWithPoints(2) } returns RouteWithPoints(
+            route = com.example.mockgps.data.model.Route(id = 2, name = "Route B", createdAt = 124L),
+            points = listOf(
+                RoutePoint(id = 3, routeId = 2, orderIndex = 0, latitude = 50.0, longitude = 50.0, dwellSeconds = 5),
+                RoutePoint(id = 4, routeId = 2, orderIndex = 1, latitude = 51.0, longitude = 51.0, dwellSeconds = 0)
+            )
+        )
+
+        var successResult: Boolean? = null
+        var msgResult: String? = null
+
+        val preview = ImportPreview(uri, 1, 0, 2)
+
+        val latch = java.util.concurrent.CountDownLatch(1)
+        viewModel.applyImportData(preview) { success, msg ->
+            successResult = success
+            msgResult = msg
+            latch.countDown()
+        }
+        latch.await()
+
+        assertEquals(true, successResult)
+        // Route A skipped (same name & points), Route B imported with rename
+        assertTrue(msgResult!!.contains("Routes: 1 imported, 1 skipped"))
+
+        coVerify(exactly = 0) { locationRepository.insertRouteWithPoints("Route A", any()) }
+        coVerify(exactly = 1) { locationRepository.insertRouteWithPoints("Route B (imported 2)", any()) }
     }

     @Test
@@ -185,7 +310,7 @@
         var errorResult: String? = null

         val latch = java.util.concurrent.CountDownLatch(1)
-        viewModel.exportDataToUri(uri) { success, error ->
+        viewModel.exportDataToUri(uri, true, true) { success, error ->
             successResult = success
             errorResult = error
             latch.countDown()
INNER_EOF
patch -p0 < app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt.patch
