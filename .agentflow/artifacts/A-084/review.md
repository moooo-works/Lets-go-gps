# 獨立審查：停止模擬後恢復定位（A-084）

## 結論

**有 Blocker，不可進入 Pixel 實機驗證或合併。** 正式路徑中，從 UI 或通知按「停止」時，teardown 會在主執行緒同步執行，而 `Tasks.await` 在主執行緒會直接拋例外。因此每次停止都會失敗，重試也一樣。現有測試以 mock engine 取代真實 engine，所以沒有測到這條路徑。

---

## Blocker

### B1. teardown 在主執行緒同步執行，`Tasks.await` 必定失敗

**證據（推導鏈）：**

1. `MockLocationService.onStartCommand` 是 Service 生命週期回呼，執行在**主執行緒**。程式碼註解寫「binder thread」是錯的，但這是既有註解。
2. `handleStop` 使用 `terminalScope.launch(start = CoroutineStart.UNDISPATCHED)`，而 `terminalScope` 的 dispatcher 是 `Dispatchers.IO`。
3. 進入 `AndroidLocationMockEngine.teardownMockProvider()`：
   - `cleanupMutex.withLock` 在無競爭時不會掛起。
   - 接著 `withContext(Dispatchers.IO)`：新舊 context 的 `ContinuationInterceptor` 相同（都是 `Dispatchers.IO`），kotlinx 會走 `UndispatchedCoroutine` 快速路徑，**不切換執行緒**。
4. 結果：`removeTestProvider` IPC 與 `Tasks.await(...)` 都在主執行緒同步執行。
5. `Tasks.await` 內部有 `checkNotMainThread`，會拋 `IllegalStateException("Must not be called on the main application thread")`。這個例外被加入 `failures`，teardown 因此拋出錯誤，畫面顯示「停止失敗」。
6. 重試時 provider 已經移除，但 FLP 那一步仍在主執行緒、仍然失敗，所以**永遠無法停止成功**。
7. 即使 GMS 沒有這個檢查，主執行緒也會被阻塞最多 3 秒，同樣違反 AC-2。
8. `onDestroy → handleStop` 也是同一條路徑。

**測試為何沒抓到：**
- `MockTeardownRecoveryTest` 用 `runBlocking` 呼叫，其 dispatcher 是 `BlockingEventLoop`，與 IO 不同，所以真的會切到 IO 執行緒。
- `MockLocationStopRecoveryTest` 用 mockk 取代 engine，完全不經過真實的 `withContext` 與 `Tasks.await`。

**最小修正（服務端）：**

```kotlin
@Volatile private var cleanupCompleted = false

private fun handleStop() {
    synchronized(stopLock) {
        if (cleanupInProgress || cleanupCompleted) return
        cleanupInProgress = true
        isStopping = true
    }
    // ...（前段不變）
    // 不可 UNDISPATCHED：terminalScope 與 engine 同為 IO，withContext 會走同 dispatcher
    // 快速路徑，整段 teardown（含 Tasks.await）會在呼叫端（主執行緒）同步執行。
    terminalScope.launch {
        try {
            if (isProviderSetup) {
                mockEngine.teardownMockProvider()
                isProviderSetup = false
            }
            synchronized(stopLock) { cleanupCompleted = true }
            mockStateRepository.setMockStatus(MockStatus.IDLE)
            // ...其餘不變
```

需要 `cleanupCompleted` 的原因：改成非同步派發後，`stopSelf` 觸發的 `onDestroy → handleStop` 會在 IO 上**延後**執行 `setMockStatus(IDLE)` 與 `setCurrentMockLocation(null)`。如果此時新的服務實例已經設成 `MOCKING`，就會被覆寫，注入也會跟著停掉。原本 UNDISPATCHED 加上 `isProviderSetup=false` 時是同步執行，不會有這個競態。

**必補回歸探針（在現行程式碼上應該會失敗）：** 在 `MockLocationStopRecoveryTest` 裡，於 `coAnswers` 中斷言 `Looper.myLooper() !== Looper.getMainLooper()`。Robolectric 的測試執行緒就是 main looper，所以現行程式碼會失敗，修正後才會通過。

另外請在全 repo 搜尋 `teardownMockProvider` 的其他呼叫端（例如工作階段還原），確認沒有在主執行緒上 `runBlocking`，也沒有其他 UNDISPATCHED-on-IO 的寫法。這些檔案不在本次提供範圍內。

---

## Major

### M1. FLP 退出變成必要條件，導致「FLP mock 從未啟用」的裝置永遠停不了

**證據：**
- `setupMockProvider` 對 `fusedClient.setMockMode(true)` 只用 try/catch 包同步例外，回傳的 Task 失敗時沒有任何處理。也就是說，設計上允許 FLP 不可用。
- `teardownMockProvider` 卻把 `setMockMode(false)` 的失敗或逾時一律算成清理失敗。

**後果：** 沒有 GMS 或 GMS 版本過舊的裝置（`build.gradle.kts` 的 Crashlytics 註解顯示有非 GMS 使用者）、`API_UNAVAILABLE` 等情況，每次停止都會失敗。服務一直留在前景，`isStopping=true` 又會擋掉所有新的開始。這是相對於 baseline 的回歸。

**最小修正：** 記住啟用時的 Task，只有在「啟用沒有已知失敗」時，才把退出失敗算成清理失敗。

```kotlin
private var flpEnableTask: Task<Void>? = null
// setup：
flpEnableTask = fusedClient.setMockMode(true)
// teardown：
val enable = flpEnableTask
val exitRequired = !(enable != null && enable.isComplete && !enable.isSuccessful)
try {
    Tasks.await(fusedClient.setMockMode(false), 3, TimeUnit.SECONDS)
    flpEnableTask = null
} catch (e: Exception) {
    if (exitRequired) failures.add(e) else Log.w(TAG, "FLP was never enabled", e)
}
```

- `enable == null` 時保守視為必要，因此現有 4 個 engine 測試照樣成立。
- 需補一個測試：「enable Task 失敗 → exit 失敗不阻擋停止」。

### M2. mock 權限被撤銷或改選其他 App 後，停止永遠失敗，服務卡住（需裝置確認）

**證據：**
- `performInjection` 明確處理 `SecurityException`，並由此呼叫 `handleStop()`。
- FLP 的 `setMockMode` 必須是目前選定的 mock location app 才能呼叫，否則 Task 會以 SecurityException 失敗。
- 因此在權限已失去的狀態下，`setMockMode(false)` 必然失敗。

**後果：** 每次重試都必然失敗。前景服務與 `MOCKING` 狀態會一直留著，新的開始也一直被擋，使用者只能強制停止 App。設計中「失敗可再停止」的前提是重試有機會成功，這個情境不成立。

**建議（需 owner 決定）：**
- 清理失敗時，若 `mockEngine.getMockPermissionStatus() != Allowed`，視為不可重試的終局失敗：保留 Teardown 錯誤（依 INV-2，不導向 mock app 設定），但呼叫 `stopForeground` 與 `stopSelf`，讓服務退出。
- 其他失敗維持現行的可重試行為。
- Pixel 實機請加一個案例：模擬中到開發者選項改選其他 mock App，再按停止。

---

## Medium

### M3. INV-3「權限錯誤可辨識」未達成

**證據：** `MyLocationFab` 中，`loc == null` 與 `catch (e: Exception)`（包括 `getCurrentLocation` 同步拋出的 `SecurityException`）都顯示同一個 `map_real_location_unavailable`。測試只驗證到函式層會把 `SecurityException` 往外傳，UI 層無法區分兩種情況。

**最小修正：** 不自動開啟設定，沿用既有字串即可。

```kotlin
} catch (e: SecurityException) {
    Toast.makeText(context, R.string.error_location_permission, Toast.LENGTH_LONG).show()
} catch (e: Exception) { /* 原 unavailable */ }
```

---

## Minor

1. **Task 取消時沒有任何提示。** `FreshDeviceLocation.kt` 的 `addOnCanceledListener { continuation.cancel() }` 會丟出一般的 `CancellationException`。`withTimeoutOrNull` 不攔它，FAB 端又會 rethrow，結果 spinner 結束卻沒有 Toast。建議改成 `continuation.resume(null)`。
2. **destroy 期間清理失敗會殘留 provider。** 若清理進行中發生 `onDestroy`，之後清理又失敗：服務已經消失、狀態不會是 IDLE；新實例的 `isProviderSetup=false`，再按停止只會設成 IDLE，不會重做 teardown。若 engine 是 singleton，殘留的 provider 會讓原始問題重現。Hilt binding 沒有提供，無法判定。M1 修正後，可以考慮讓 `handleStop` 一律呼叫 teardown（engine 只移除有追蹤的 provider，具冪等性）。
3. **停止期間的開始指令被丟棄，但仍扣次數。** 停止進行中或停止失敗時，懸浮工具的 Locate 會送出 `ACTION_START_SINGLE`。服務在 `startForeground` 後依 `isStopping` 直接丟棄，但 VM 端仍會執行 `consumeStepSyncCreditIfNeeded`，並移動 `centerLocation`。這是既有問題，但清理時間拉長到最多 3 秒以上，影響窗口變大。
4. **停止失敗後的 UI 與通知狀態不一致。** 被丟棄的開始指令會呼叫 `startForeground(buildNotification(IDLE))`，把通知改成「準備中…」。另外，停止失敗後 `mockStatus` 仍停在 `ROUTE_PLAYING`，UI 顯示播放中，實際上已經停止。這些不違反契約，但會誤導使用者。

---

## INV / AC 評估

| 項目 | 判定 | 說明 |
|---|---|---|
| INV-1 | 邏輯上成立 | 停止期間由 `isStopping`、engine 的 `tearingDown` 與 `@Synchronized` 擋住注入與開始；`terminalScope` 不會被 `onDestroy` 取消。B1 修正時需加 `cleanupCompleted`，避免引入覆寫新工作階段的競態。 |
| INV-2 | 部分成立 | 失敗的 provider 確實保留，也不會標記成功；`Teardown → ProviderTeardownFailed` 有測試。但錯誤對話框（MapState 或 dialog）的原始碼未提供，「不導向 mock app 設定」**未證明**。M1、M2 會造成無法恢復的永久失敗。 |
| INV-3 | **未達成** | 舊 mock 座標確實被拒絕（`maxAge=0`、`isMock`）；但權限錯誤無法辨識（M3）。 |
| INV-4 | **未證明** | MapScreen、NavHost 與 `MyLocationFab` 的呼叫端原始碼未提供。從已提供的檔案看，沒有 VM owner 或資料政策的變更。 |
| AC-1 | 成立 | 兩項故障注入，加上 FLP 的成功、失敗、逾時都有測試。 |
| AC-2 | **不成立** | 被 B1 推翻。重複停止的防護與失敗重試只在 mock engine 下驗證過。 |
| AC-3 | 大致成立 | `maxAge=0`、15/16 秒期限、null 或 mock 不移動鏡頭並顯示失敗。Minor 1 的取消情境沒有提示。 |
| AC-4 | 未完成 | Gradle 三項檢查已通過，本審查已完成並提出阻擋項。Pixel 隔離套件實測尚未進行；Samsung 沒有裝置，不可聲稱已實測。 |

**原始需求「停止後按定位能回到真實位置」：** 目前在正式路徑上無法達成，因為 B1 讓停止必定失敗，test provider 與 FLP mock 也就不會解除。修正 B1 與 M1 之後，依目前設計應可達成，但仍需 Pixel 實測「開始 → 停止 → 按定位」，並確認不需要 Google Maps 刷新。

## 最小性

- 沒有新增常駐服務或權限，`suspend` 介面的變更範圍合理。
- B1 與 M1 的修正都只改動單一函式內的少數幾行，不擴大範圍。
- M2 涉及「失敗時是否保留服務」的設計取捨，需要 owner 決定後才實作。
