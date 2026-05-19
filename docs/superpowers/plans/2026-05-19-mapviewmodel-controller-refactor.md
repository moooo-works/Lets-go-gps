# MapViewModel Controller 重構實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 將 `MapViewModel`（799 行）的邏輯拆入三個 Controller 類別，ViewModel 成為薄委派層（目標 ~350 行），各 Controller 可獨立單元測試。

**Architecture:** `LocationPinController`、`RouteController`、`JoystickController` 各自接收共用的 `MutableStateFlow<MapUiState>` 與 `CoroutineScope`，直接讀寫狀態。跨越 ViewModel 職責的操作（stopMocking、ensurePermission、onCameraMove）透過建構時傳入的 lambda 解耦。ViewModel 的公開 fun 成為一行委派，`MapScreen` 無需任何修改。

**Tech Stack:** Kotlin, Hilt (ViewModel 層不變), MockK, kotlinx-coroutines-test, Robolectric SDK 33

---

## 檔案清單

| 動作 | 路徑 |
|------|------|
| 新建 | `app/src/main/java/com/moooo_works/letsgogps/ui/map/LocationPinController.kt` |
| 新建 | `app/src/main/java/com/moooo_works/letsgogps/ui/map/RouteController.kt` |
| 新建 | `app/src/main/java/com/moooo_works/letsgogps/ui/map/JoystickController.kt` |
| 新建 | `app/src/test/java/com/moooo_works/letsgogps/ui/map/LocationPinControllerTest.kt` |
| 新建 | `app/src/test/java/com/moooo_works/letsgogps/ui/map/RouteControllerTest.kt` |
| 新建 | `app/src/test/java/com/moooo_works/letsgogps/ui/map/JoystickControllerTest.kt` |
| 修改 | `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt` |

---

### Task 1: LocationPinController

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/map/LocationPinController.kt`
- Create: `app/src/test/java/com/moooo_works/letsgogps/ui/map/LocationPinControllerTest.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt`

- [ ] **Step 1: 新建測試檔案（預期失敗）**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/ui/map/LocationPinControllerTest.kt
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
```

- [ ] **Step 2: 確認測試因 class 不存在而失敗**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.LocationPinControllerTest
```
Expected: 編譯錯誤（`LocationPinController` 不存在）

- [ ] **Step 3: 建立 LocationPinController 實作**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/ui/map/LocationPinController.kt
package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocationPinController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val repository: LocationRepository
) {
    fun select(location: SavedLocation) {
        state.update { it.copy(selectedLocation = location) }
    }

    fun dismiss() {
        state.update { it.copy(selectedLocation = null, showEditLocationDialog = false) }
    }

    fun showEditDialog() {
        state.update { it.copy(showEditLocationDialog = true) }
    }

    fun dismissEditDialog() {
        state.update { it.copy(showEditLocationDialog = false) }
    }

    fun delete() {
        val location = state.value.selectedLocation ?: return
        scope.launch {
            repository.deleteLocation(location)
            dismiss()
        }
    }

    fun toggleFavorite() {
        val location = state.value.selectedLocation ?: return
        val updated = location.copy(isFavorite = !location.isFavorite)
        scope.launch {
            repository.updateLocation(updated)
            state.update { it.copy(selectedLocation = updated) }
        }
    }

    fun updateDetails(name: String, description: String) {
        val location = state.value.selectedLocation ?: return
        val updated = location.copy(name = name.trim(), description = description.trim())
        scope.launch {
            repository.updateLocation(updated)
            state.update { it.copy(selectedLocation = updated, showEditLocationDialog = false) }
        }
    }

    fun saveIfNeeded(latLng: LatLng) {
        scope.launch {
            val epsilon = 0.0001
            val exists = state.value.savedLocations.any {
                kotlin.math.abs(it.latitude - latLng.latitude) < epsilon &&
                    kotlin.math.abs(it.longitude - latLng.longitude) < epsilon
            }
            if (!exists) {
                repository.saveLocation(
                    SavedLocation(
                        name = "Saved ${System.currentTimeMillis()}",
                        latitude = latLng.latitude,
                        longitude = latLng.longitude
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 4: 確認測試通過**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.LocationPinControllerTest
```
Expected: 9 tests PASSED

- [ ] **Step 5: 在 MapViewModel 加入 controller 並委派**

在 `MapViewModel.kt` 的 `_uiState` 宣告下方加入（`viewModelScope` 在 `ViewModel` 父類中定義，可直接使用）：

```kotlin
private val locationPinController = LocationPinController(_uiState, viewModelScope, repository)
```

將以下 7 個 public fun 的**函式本體全部刪除**，替換為一行委派：

```kotlin
fun selectLocation(location: SavedLocation) = locationPinController.select(location)
fun dismissSelectedLocation() = locationPinController.dismiss()
fun showEditLocationDialog() = locationPinController.showEditDialog()
fun dismissEditLocationDialog() = locationPinController.dismissEditDialog()
fun deleteSelectedLocation() = locationPinController.delete()
fun toggleFavorite() = locationPinController.toggleFavorite()
fun updateLocationDetails(name: String, description: String) =
    locationPinController.updateDetails(name, description)
```

在 `startMocking()` 中將：
```kotlin
saveLocationIfNeeded(target)
```
改為：
```kotlin
locationPinController.saveIfNeeded(target)
```

刪除 ViewModel 中的 `private fun saveLocationIfNeeded(latLng: LatLng)` 整個函式（約 15 行）。

- [ ] **Step 6: 確認全測試通過**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/map/LocationPinController.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/map/LocationPinControllerTest.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
git commit -m "refactor: extract LocationPinController from MapViewModel"
```

---

### Task 2: RouteController

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/map/RouteController.kt`
- Create: `app/src/test/java/com/moooo_works/letsgogps/ui/map/RouteControllerTest.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt`

- [ ] **Step 1: 新建測試檔案（預期失敗）**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/ui/map/RouteControllerTest.kt
package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.service.MockLocationService
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteControllerTest {

    private val repository = mockk<LocationRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val state = MutableStateFlow(MapUiState())
    private var stopMockingCalled = false
    private var ensurePermissionResult = true

    private fun makeController(scope: kotlinx.coroutines.CoroutineScope) = RouteController(
        state = state,
        scope = scope,
        repository = repository,
        mockStateRepository = mockStateRepository,
        routeSimulator = routeSimulator,
        settingsRepository = settingsRepository,
        context = context,
        onStopMocking = { stopMockingCalled = true },
        onEnsurePermission = { ensurePermissionResult }
    )

    @Test
    fun `addWaypointAt appends waypoint and syncs simulator`() = runTest {
        val ctrl = makeController(this)
        val pt = LatLng(1.0, 2.0)
        ctrl.addWaypointAt(pt)
        assertEquals(listOf(pt), state.value.waypoints)
        verify { mockStateRepository.setActiveRouteWaypoints(listOf(pt)) }
        verify { routeSimulator.setRoute(listOf(pt)) }
    }

    @Test
    fun `addWaypoint uses current centerLocation`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(5.0, 6.0))
        val ctrl = makeController(this)
        ctrl.addWaypoint()
        assertEquals(LatLng(5.0, 6.0), state.value.waypoints.first())
    }

    @Test
    fun `removeWaypointAt removes correct index`() = runTest {
        state.value = state.value.copy(waypoints = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0)))
        val ctrl = makeController(this)
        ctrl.removeWaypointAt(0)
        assertEquals(listOf(LatLng(3.0, 4.0)), state.value.waypoints)
        verify { mockStateRepository.setActiveRouteWaypoints(listOf(LatLng(3.0, 4.0))) }
    }

    @Test
    fun `clearRoute resets waypoints and calls onStopMocking and stops simulator`() = runTest {
        state.value = state.value.copy(waypoints = listOf(LatLng(1.0, 2.0)))
        val ctrl = makeController(this)
        ctrl.clearRoute()
        assertTrue(state.value.waypoints.isEmpty())
        assertTrue(stopMockingCalled)
        verify { routeSimulator.stop() }
    }

    @Test
    fun `cycleLoopMode rotates NONE then LOOP then BOUNCE then back to NONE`() = runTest {
        val ctrl = makeController(this)
        assertEquals(LoopMode.NONE, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.LOOP, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.BOUNCE, state.value.loopMode)
        ctrl.cycleLoopMode(); assertEquals(LoopMode.NONE, state.value.loopMode)
        verify(exactly = 3) { routeSimulator.setLoopMode(any()) }
    }

    @Test
    fun `setSpeed rejects zero and sets InvalidInput error`() = runTest {
        val ctrl = makeController(this)
        ctrl.setSpeed(0.0)
        assertTrue(state.value.mockError is MockError.InvalidInput)
        verify(exactly = 0) { routeSimulator.setSpeed(any()) }
    }

    @Test
    fun `setSpeed positive value updates state and simulator`() = runTest {
        val ctrl = makeController(this)
        ctrl.setSpeed(30.0)
        advanceUntilIdle()
        assertEquals(30.0, state.value.speedKmh, 0.0)
        verify { routeSimulator.setSpeed(30.0 / 3.6) }
        verify { settingsRepository.setRouteSpeed(30.0) }
    }

    @Test
    fun `setTransportMode updates state transport and speed`() = runTest {
        val ctrl = makeController(this)
        ctrl.setTransportMode(TransportMode.DRIVING)
        advanceUntilIdle()
        assertEquals(TransportMode.DRIVING, state.value.transportMode)
        assertEquals(40.0, state.value.speedKmh, 0.0)
        verify { routeSimulator.setSpeed(40.0 / 3.6) }
        verify { settingsRepository.setRouteSpeed(40.0) }
    }

    @Test
    fun `playRoute shows Pro upgrade when not Pro`() = runTest {
        state.value = state.value.copy(isProActive = false)
        val ctrl = makeController(this)
        ctrl.playRoute()
        assertTrue(state.value.showProUpgrade)
        verify(exactly = 0) { context.startForegroundService(any()) }
    }

    @Test
    fun `playRoute sends ACTION_START_ROUTE when Pro and permission ok`() = runTest {
        state.value = state.value.copy(isProActive = true)
        val ctrl = makeController(this)
        val intentSlot = slot<Intent>()
        ctrl.playRoute()
        verify { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_ROUTE, intentSlot.captured.action)
    }

    @Test
    fun `pauseRoute sends ACTION_PAUSE_ROUTE`() = runTest {
        val ctrl = makeController(this)
        val intentSlot = slot<Intent>()
        ctrl.pauseRoute()
        verify { context.startService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_PAUSE_ROUTE, intentSlot.captured.action)
    }

    @Test
    fun `loadRoute restores sorted waypoints and sets fit token`() = runTest {
        val route = RouteWithPoints(
            route = Route(id = 1, name = "R"),
            points = listOf(
                RoutePoint(routeId = 1, orderIndex = 1, latitude = 3.0, longitude = 4.0),
                RoutePoint(routeId = 1, orderIndex = 0, latitude = 1.0, longitude = 2.0)
            )
        )
        coEvery { repository.getRouteWithPoints(1) } returns route
        val ctrl = makeController(this)
        ctrl.loadRoute(1)
        advanceUntilIdle()
        assertEquals(2, state.value.waypoints.size)
        assertEquals(LatLng(1.0, 2.0), state.value.waypoints[0])
        assertEquals(LatLng(3.0, 4.0), state.value.waypoints[1])
        assertTrue(state.value.routeFitRequestToken != null)
    }

    @Test
    fun `onRouteFitConsumed clears routeFitRequestToken`() = runTest {
        state.value = state.value.copy(routeFitRequestToken = 12345L)
        val ctrl = makeController(this)
        ctrl.onRouteFitConsumed()
        assertNull(state.value.routeFitRequestToken)
    }
}
```

- [ ] **Step 2: 確認測試因 class 不存在而失敗**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.RouteControllerTest
```
Expected: 編譯錯誤（`RouteController` 不存在）

- [ ] **Step 3: 建立 RouteController 實作**

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/ui/map/RouteController.kt
package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.service.MockLocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RouteController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val repository: LocationRepository,
    private val mockStateRepository: MockStateRepository,
    private val routeSimulator: RouteSimulator,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    private val onStopMocking: () -> Unit,
    private val onEnsurePermission: () -> Boolean
) {
    private companion object {
        const val KMH_TO_MPS = 3.6
    }

    fun addWaypoint() = addWaypointAt(state.value.centerLocation)

    fun addWaypointAt(latLng: LatLng) {
        val updated = state.value.waypoints + latLng
        state.update { it.copy(waypoints = updated) }
        mockStateRepository.setActiveRouteWaypoints(updated)
        routeSimulator.setRoute(updated)
    }

    fun removeWaypointAt(index: Int) {
        val updated = state.value.waypoints.toMutableList().apply { removeAt(index) }
        state.update { it.copy(waypoints = updated) }
        mockStateRepository.setActiveRouteWaypoints(updated)
        routeSimulator.setRoute(updated)
    }

    fun clearRoute() {
        state.update { it.copy(waypoints = emptyList(), currentMockLocation = null, currentLocation = null) }
        mockStateRepository.setActiveRouteWaypoints(emptyList())
        onStopMocking()
        routeSimulator.stop()
    }

    fun saveCurrentRoute(name: String) {
        val normalizedName = name.trim()
        val points = state.value.waypoints
        if (normalizedName.isBlank() || normalizedName.length > 40 || points.size < 2) return
        scope.launch {
            repository.insertRouteWithPoints(
                normalizedName,
                points.mapIndexed { index, point ->
                    RoutePoint(
                        routeId = 0,
                        orderIndex = index,
                        latitude = point.latitude,
                        longitude = point.longitude
                    )
                }
            )
        }
    }

    fun loadRoute(routeId: Int) {
        scope.launch {
            val route = repository.getRouteWithPoints(routeId) ?: return@launch
            clearRoute()
            val points = route.points
                .sortedBy { it.orderIndex }
                .map { LatLng(it.latitude, it.longitude) }
            state.update {
                it.copy(
                    waypoints = points,
                    centerLocation = points.firstOrNull() ?: it.centerLocation,
                    routeFitRequestToken = if (points.size >= 2) System.currentTimeMillis() else null
                )
            }
            mockStateRepository.setActiveRouteWaypoints(points)
            routeSimulator.setRoute(points)
        }
    }

    fun onRouteFitConsumed() {
        state.update { it.copy(routeFitRequestToken = null) }
    }

    fun setTransportMode(mode: TransportMode) {
        state.update { it.copy(transportMode = mode, speedKmh = mode.speedKmh) }
        routeSimulator.setSpeed(mode.speedKmh / KMH_TO_MPS)
        scope.launch { settingsRepository.setRouteSpeed(mode.speedKmh) }
    }

    fun setSpeed(speedKmh: Double) {
        if (speedKmh <= 0.0) {
            state.update { it.copy(mockError = MockError.InvalidInput("Speed must be greater than 0 km/h")) }
            return
        }
        state.update { it.copy(speedKmh = speedKmh) }
        routeSimulator.setSpeed(speedKmh / KMH_TO_MPS)
        scope.launch { settingsRepository.setRouteSpeed(speedKmh) }
    }

    fun cycleLoopMode() {
        val next = when (state.value.loopMode) {
            LoopMode.NONE -> LoopMode.LOOP
            LoopMode.LOOP -> LoopMode.BOUNCE
            LoopMode.BOUNCE -> LoopMode.NONE
        }
        routeSimulator.setLoopMode(next)
        state.update { it.copy(loopMode = next) }
    }

    fun playRoute() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (!onEnsurePermission()) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_ROUTE
            }
        )
    }

    fun startExplorationAtCenter() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (!onEnsurePermission()) return
        val target = state.value.centerLocation
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_EXPLORATION
                putExtra(MockLocationService.EXTRA_LAT, target.latitude)
                putExtra(MockLocationService.EXTRA_LNG, target.longitude)
            }
        )
    }

    fun startTeleportExplorationOfRoute() {
        if (!state.value.isProActive) { state.update { it.copy(showProUpgrade = true) }; return }
        if (!onEnsurePermission()) return
        val targets = state.value.waypoints
        if (targets.isEmpty()) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START_TELEPORT_EXPLORATION
                putExtra(MockLocationService.EXTRA_LATS, targets.map { it.latitude }.toDoubleArray())
                putExtra(MockLocationService.EXTRA_LNGS, targets.map { it.longitude }.toDoubleArray())
            }
        )
    }

    fun pauseRoute() {
        context.startService(
            Intent(context, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_PAUSE_ROUTE
            }
        )
    }

    fun stopRoute() = onStopMocking()
}
```

- [ ] **Step 4: 確認測試通過**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.RouteControllerTest
```
Expected: 13 tests PASSED

- [ ] **Step 5: 在 MapViewModel 加入 routeController 並委派**

在 `locationPinController` 宣告下方加入：

```kotlin
private val routeController = RouteController(
    state = _uiState,
    scope = viewModelScope,
    repository = repository,
    mockStateRepository = mockStateRepository,
    routeSimulator = routeSimulator,
    settingsRepository = settingsRepository,
    context = context,
    onStopMocking = ::stopMocking,
    onEnsurePermission = ::ensurePermission
)
```

將以下 fun 的**函式本體全部刪除**，替換為一行委派：

```kotlin
fun cycleLoopMode() = routeController.cycleLoopMode()
fun addWaypoint() = routeController.addWaypoint()
fun addWaypointAt(latLng: LatLng) = routeController.addWaypointAt(latLng)
fun removeWaypointAt(index: Int) = routeController.removeWaypointAt(index)
fun clearRoute() = routeController.clearRoute()
fun saveCurrentRoute(name: String) = routeController.saveCurrentRoute(name)
fun loadRoute(routeId: Int) = routeController.loadRoute(routeId)
fun onRouteFitConsumed() = routeController.onRouteFitConsumed()
fun setTransportMode(mode: TransportMode) = routeController.setTransportMode(mode)
fun setSpeed(speedKmh: Double) = routeController.setSpeed(speedKmh)
fun playRoute() = routeController.playRoute()
fun startExplorationAtCenter() = routeController.startExplorationAtCenter()
fun startTeleportExplorationOfRoute() = routeController.startTeleportExplorationOfRoute()
fun pauseRoute() = routeController.pauseRoute()
fun stopRoute() = routeController.stopRoute()
```

ViewModel companion object 裡的 `KMH_TO_MPS_DIVISOR` 若無其他地方引用則刪除（已移至 `RouteController`）。

- [ ] **Step 6: 確認全測試通過**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/map/RouteController.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/map/RouteControllerTest.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
git commit -m "refactor: extract RouteController from MapViewModel"
```

---

### Task 3: JoystickController

**Files:**
- Create: `app/src/main/java/com/moooo_works/letsgogps/ui/map/JoystickController.kt`
- Create: `app/src/test/java/com/moooo_works/letsgogps/ui/map/JoystickControllerTest.kt`
- Modify: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt`

- [ ] **Step 1: 新建測試檔案（預期失敗）**

```kotlin
// app/src/test/java/com/moooo_works/letsgogps/ui/map/JoystickControllerTest.kt
package com.moooo_works.letsgogps.ui.map

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JoystickControllerTest {

    private val overlayManager = mockk<JoystickOverlayManager>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val state = MutableStateFlow(MapUiState())
    private var stopMockingCalled = false
    private var lastCameraMove: LatLng? = null
    private var lastSetTransportMode: TransportMode? = null

    private fun makeController(scope: kotlinx.coroutines.CoroutineScope) = JoystickController(
        state = state,
        scope = scope,
        overlayManager = overlayManager,
        mockStateRepository = mockStateRepository,
        context = context,
        onStopMocking = { stopMockingCalled = true },
        onCameraMove = { lastCameraMove = it },
        onSetTransportMode = { lastSetTransportMode = it }
    )

    @Test
    fun `toggle shows Pro upgrade when not Pro`() = runTest {
        state.value = state.value.copy(isProActive = false)
        val ctrl = makeController(this)
        ctrl.toggle()
        assertTrue(state.value.showProUpgrade)
        assertFalse(state.value.isJoystickEnabled)
    }

    @Test
    fun `toggle disables joystick and hides overlay when already enabled`() = runTest {
        state.value = state.value.copy(isProActive = true, isJoystickEnabled = true)
        val ctrl = makeController(this)
        ctrl.toggle()
        assertFalse(state.value.isJoystickEnabled)
        verify { overlayManager.hide() }
    }

    @Test
    fun `applyMovementForTest moves center north when dy is negative`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(10.0, 20.0), speedKmh = 36.0)
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 0f, dy = -1f)
        assertNotNull(lastCameraMove)
        assertTrue(lastCameraMove!!.latitude > 10.0)
    }

    @Test
    fun `applyMovementForTest moves center east when dx is positive`() = runTest {
        state.value = state.value.copy(centerLocation = LatLng(0.0, 20.0), speedKmh = 36.0)
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        assertNotNull(lastCameraMove)
        assertTrue(lastCameraMove!!.longitude > 20.0)
    }

    @Test
    fun `applyMovementForTest updates mock location when mocking in SINGLE mode`() = runTest {
        state.value = state.value.copy(
            centerLocation = LatLng(10.0, 20.0),
            speedKmh = 36.0,
            isMocking = true,
            mapMode = MapMode.SINGLE
        )
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        verify { mockStateRepository.setCurrentMockLocation(any()) }
    }

    @Test
    fun `applyMovementForTest does not update mock location when not mocking`() = runTest {
        state.value = state.value.copy(
            centerLocation = LatLng(10.0, 20.0),
            speedKmh = 36.0,
            isMocking = false
        )
        val ctrl = makeController(this)
        ctrl.applyMovementForTest(dx = 1f, dy = 0f)
        verify(exactly = 0) { mockStateRepository.setCurrentMockLocation(any()) }
    }

    @Test
    fun `cycleTransportModeForTest cycles WALKING to CYCLING`() = runTest {
        state.value = state.value.copy(transportMode = TransportMode.WALKING)
        val ctrl = makeController(this)
        ctrl.cycleTransportModeForTest()
        assert(lastSetTransportMode == TransportMode.CYCLING)
    }

    @Test
    fun `onCleared hides overlay`() = runTest {
        val ctrl = makeController(this)
        ctrl.onCleared()
        verify { overlayManager.hide() }
    }
}
```

- [ ] **Step 2: 確認測試因 class 不存在而失敗**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.JoystickControllerTest
```
Expected: 編譯錯誤（`JoystickController` 不存在）

- [ ] **Step 3: 建立 JoystickController 實作**

`applyMovementForTest` 與 `cycleTransportModeForTest` 是 `internal` visibility，僅供同 package 的測試呼叫。

```kotlin
// app/src/main/java/com/moooo_works/letsgogps/ui/map/JoystickController.kt
package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class JoystickController(
    private val state: MutableStateFlow<MapUiState>,
    private val scope: CoroutineScope,
    private val overlayManager: JoystickOverlayManager,
    private val mockStateRepository: MockStateRepository,
    private val context: Context,
    private val onStopMocking: () -> Unit,
    private val onCameraMove: (LatLng) -> Unit,
    private val onSetTransportMode: (TransportMode) -> Unit
) {
    private var tickerJob: Job? = null
    private var currentX = 0f
    private var currentY = 0f

    fun toggle() {
        if (!state.value.isJoystickEnabled) {
            if (!state.value.isProActive) {
                state.update { it.copy(showProUpgrade = true) }
                return
            }
            if (!ensureFloatingWindowPermission()) return
            state.update { it.copy(isJoystickEnabled = true) }
            startTicker()
            overlayManager.show {
                val s by state.collectAsState()
                JoystickOverlayView(
                    transportMode = s.transportMode,
                    onMove = { dx, dy -> currentX = dx; currentY = dy },
                    onWindowDrag = { dx, dy -> overlayManager.updatePosition(dx, dy) },
                    onWindowDragEnd = { overlayManager.snapToEdge() },
                    onToggleSpeed = { cycleTransportMode() },
                    onStop = { stopMockingFromJoystick() }
                )
            }
        } else {
            state.update { it.copy(isJoystickEnabled = false) }
            stopTicker()
            overlayManager.hide()
        }
    }

    private fun cycleTransportMode() {
        val next = when (state.value.transportMode) {
            TransportMode.WALKING -> TransportMode.CYCLING
            TransportMode.CYCLING -> TransportMode.DRIVING
            TransportMode.DRIVING -> TransportMode.WALKING
        }
        onSetTransportMode(next)
    }

    private fun stopMockingFromJoystick() {
        onStopMocking()
        if (state.value.isJoystickEnabled) toggle()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                if (currentX != 0f || currentY != 0f) applyMovement(currentX, currentY)
                delay(100)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        currentX = 0f
        currentY = 0f
    }

    private fun applyMovement(dx: Float, dy: Float) {
        val current = state.value
        val metersPerTick = (current.speedKmh * 1000.0 / 3600.0) * 0.1
        val degreesPerTick = metersPerTick / 111000.0
        val latDelta = -dy * degreesPerTick
        val cosLat = kotlin.math.cos(Math.toRadians(current.centerLocation.latitude)).coerceAtLeast(0.001)
        val lngDelta = dx * degreesPerTick / cosLat
        val newCenter = LatLng(
            current.centerLocation.latitude + latDelta,
            current.centerLocation.longitude + lngDelta
        )
        onCameraMove(newCenter)
        if (current.isMocking && current.mapMode == MapMode.SINGLE) {
            mockStateRepository.setCurrentMockLocation(newCenter)
        }
    }

    private fun ensureFloatingWindowPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            state.update { it.copy(mockError = MockError.FloatingWindowPermissionMissing) }
            return false
        }
        return true
    }

    fun onCleared() {
        stopTicker()
        overlayManager.hide()
    }

    internal fun applyMovementForTest(dx: Float, dy: Float) = applyMovement(dx, dy)
    internal fun cycleTransportModeForTest() = cycleTransportMode()
}
```

- [ ] **Step 4: 確認測試通過**

```bash
./gradlew test --tests com.moooo_works.letsgogps.ui.map.JoystickControllerTest
```
Expected: 7 tests PASSED

- [ ] **Step 5: 在 MapViewModel 加入 joystickController 並委派**

在 `routeController` 宣告下方加入（注意：`onSetTransportMode = ::setTransportMode` 呼叫的是 ViewModel 的公開 fun，該 fun 已委派給 `routeController`）：

```kotlin
private val joystickController = JoystickController(
    state = _uiState,
    scope = viewModelScope,
    overlayManager = joystickOverlayManager,
    mockStateRepository = mockStateRepository,
    context = context,
    onStopMocking = ::stopMocking,
    onCameraMove = ::onCameraMove,
    onSetTransportMode = ::setTransportMode
)
```

將 `toggleJoystick()` 改為：

```kotlin
fun toggleJoystick() = joystickController.toggle()
```

刪除 ViewModel 中下列欄位與函式（已全部搬進 `JoystickController`）：
- `private var joystickTickerJob: Job?`
- `private var currentJoystickX: Float`
- `private var currentJoystickY: Float`
- `private fun cycleTransportMode()`
- `private fun stopMockingFromJoystick()`
- `private fun startJoystickTicker()`
- `private fun stopJoystickTicker()`
- `private fun applyJoystickMovement(dx: Float, dy: Float)`
- `private fun ensureFloatingWindowPermission(): Boolean`

將 `override fun onCleared()` 改為：

```kotlin
override fun onCleared() {
    joystickController.onCleared()
    super.onCleared()
}
```

- [ ] **Step 6: 確認全測試通過**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/map/JoystickController.kt \
        app/src/test/java/com/moooo_works/letsgogps/ui/map/JoystickControllerTest.kt \
        app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
git commit -m "refactor: extract JoystickController from MapViewModel"
```

---

### Task 4: 最終驗證

**Files:**
- Modify（可能）: `app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt`

- [ ] **Step 1: 確認 MapViewModel 行數已大幅降低**

```bash
wc -l app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
```
Expected: 400 行以下。若超過 420 行，檢查是否有函式本體未替換為委派。

- [ ] **Step 2: 確認 ViewModel 不再殘留移出的邏輯**

```bash
grep -n "delay\|tickerJob\|currentJoystick\|saveLocationIfNeeded\|applyJoystick\|cycleTransportMode\|ensureFloatingWindow" \
  app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
```
Expected: 無輸出

- [ ] **Step 3: 完整建置與測試**

```bash
./gradlew test assembleDebug
```
Expected: BUILD SUCCESSFUL，全部測試（原有 MapViewModelTest + 3 個新 Controller 測試共約 29 個）通過

- [ ] **Step 4: Commit（若有殘留清理）**

若 Step 1–2 有發現殘留並清理，執行：

```bash
git add app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt
git commit -m "refactor: finalize MapViewModel delegation cleanup"
```
