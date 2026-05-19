# MapViewModel Controller 重構設計

**日期**：2026-05-19  
**狀態**：已確認，待實作

---

## 問題

`MapViewModel.kt` 目前 799 行，混合六個不同職責：mock 控制、路線／waypoint 管理、搖桿控制、儲存位置 CRUD、健康檢查、Tips／Onboarding。難以測試，職責邊界不清。

---

## 方案

保留 `MapViewModel` 作為唯一對外入口（`MapScreen` API 不變），將邏輯委派給三個 Controller 類別（普通 Kotlin class，非 ViewModel）。

### 溝通模式

每個 Controller 於 `MapViewModel.init` 建立，接收：
- `state: MutableStateFlow<MapUiState>` — 直接讀寫，不需回呼
- `scope: CoroutineScope` — 使用 `viewModelScope`
- 各自所需的依賴（Repository、Context 等）

ViewModel 公開薄薄一層 delegation，例如：

```kotlin
fun toggleJoystick() = joystickController.toggle()
fun addWaypoint()    = routeController.addWaypoint()
fun selectLocation(l: SavedLocation) = locationPinController.select(l)
```

---

## 檔案結構

```
ui/map/
  MapViewModel.kt          （現有，瘦身至 ~350 行）
  JoystickController.kt    （新建，~130 行）
  RouteController.kt       （新建，~180 行）
  LocationPinController.kt （新建，~100 行）
  ── 其他現有檔案不動 ──
```

---

## 各 Controller 職責

### JoystickController（~130 行）

| 函式 | 說明 |
|------|------|
| `toggle()` | 含 floating window 權限檢查，呼叫 JoystickOverlayManager |
| `startTicker()` / `stopTicker()` | coroutine ticker loop（0.1s interval） |
| `applyMovement(dx, dy)` | 球面座標計算，更新 `centerLocation` |
| `cycleTransportMode()` | 切換 TransportMode |
| `stopMockingFromJoystick()` | 透過 `onStopMocking: () -> Unit` lambda 呼叫 ViewModel |

依賴：`JoystickOverlayManager`、`Context`、`onStopMocking` lambda

### RouteController（~180 行）

| 函式 | 說明 |
|------|------|
| `addWaypoint()` / `addWaypointAt(latLng)` | 加入 waypoint |
| `removeWaypointAt(i)` / `clearRoute()` | 移除 waypoint |
| `saveCurrentRoute(name)` | 存至 Room（`LocationRepository`） |
| `loadRoute(routeId)` | 從 Room 載入，更新 waypoints 與 routeFitBounds |
| `playRoute()` / `pauseRoute()` / `stopRoute()` | 發 Intent 給 `MockLocationService` |
| `startExplorationAtCenter()` / `startTeleportExplorationOfRoute()` | 探索模式 |
| `cycleLoopMode()` | 循環模式切換 |
| `setTransportMode(mode)` / `setSpeed(kmh)` | 速度設定 |
| `onRouteFitConsumed()` | 清除一次性地圖 fit 事件 |

依賴：`LocationRepository`、`Context`

### LocationPinController（~100 行）

| 函式 | 說明 |
|------|------|
| `select(location)` / `dismiss()` | 選取狀態管理 |
| `showEditDialog()` / `dismissEditDialog()` | 對話框狀態 |
| `delete()` | 刪除選取的 pin 並更新列表 |
| `toggleFavorite()` / `updateDetails(name, desc)` | 編輯操作 |
| `saveIfNeeded(latLng)` | 自動儲存（private，由 ViewModel 在 onCameraMove 呼叫） |

依賴：`LocationRepository`

---

## MapViewModel 保留職責（瘦身後 ~350 行）

- Mock 啟動／停止（`startMocking`、`stopMocking`）
- 錯誤處理（`handleEngineError`、`setMockError`）
- 權限檢查（`ensurePermission`、`checkMockPermission`、`refreshMockPermission`）
- Health check（`refreshHealthCheck`、`openHealthCheck`、`dismissHealthCheck`）
- Timezone 檢查（`maybeCheckTimezoneMismatch`、`dismissTimezoneMismatch`）
- Tips / Onboarding（`dismissOnboarding`、`dismissClipboardHintTip` 等）
- Map 狀態（`setMapMode`、`toggleMapType`、`onCameraMove`）
- Pro / Billing（`showProUpgradeDialog`、`launchBillingFlow`）
- In-app review 觸發（`checkAndTriggerReview`）

---

## 測試策略

現有 `MapViewModelTest.kt` 繼續覆蓋 ViewModel 層。新增三個純 JVM 測試：

```kotlin
// 範例
class JoystickControllerTest {
    private val state = MutableStateFlow(MapUiState())
    private val controller = JoystickController(
        state = state,
        scope = TestScope(),
        overlayManager = FakeJoystickOverlayManager(),
        context = mockContext,
        onStopMocking = {}
    )

    @Test fun `applyMovement 往北移動更新 latitude`() { ... }
    @Test fun `toggle 無 floating window 權限時不啟動`() { ... }
}
```

---

## 遷移順序

1. `LocationPinController`（最獨立，無 coroutine）
2. `RouteController`（有 coroutine，但無 overlay 複雜度）
3. `JoystickController`（最複雜，有 ticker loop 與 overlay）
4. 每步完成後執行 `./gradlew test` 確認綠燈

---

## 成功標準

- `MapViewModel.kt` 降至 ~350 行
- 三個 Controller 各自有對應單元測試
- `./gradlew test assembleDebug` 全數通過
- `MapScreen` 無需任何修改
