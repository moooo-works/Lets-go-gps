# 最終獨立審查：停止模擬後恢復定位（A-084）

## 結論

**沒有可確認的阻擋項目。** 有一項條件性風險（R1），需要主機做一次搜尋來排除；如果搜尋結果成立，它就是這次變更引入的範圍內回歸，應該升為阻擋。

本輪工具停用，以下是對所提供 diff 與完整檔案的靜態審查。我沒有自行執行建置、測試或裝置驗證。

---

## 原始目標

目標是「停止後按定位能拿到真實位置，不會拿到舊 mock」。兩個根因都處理到了：

- **FLP 退出**：`setMockMode(false)` 改為 callback 等待，加 3 秒 timeout。成功後才設 `IDLE`。所以 UI 看到 IDLE 時，FLP 已經確認離開 mock 模式。
- **定位按鈕**：`lastLocation` 改成 `getCurrentLocation`，參數為 `maxUpdateAge=0`、`duration=15s`，外層再包 16 秒 `withTimeoutOrNull`。另外用 `LocationCompat.isMock` 過濾，取消時也會取消 token。

## 不變式

**INV-1 通過**
- 停止中不注入：
  - `isStopping`（volatile）在 `performInjection` 開頭就擋下。
  - engine 端的 `tearingDown` 在 monitor 內設定，而 `setLocation` 是 `@Synchronized`，所以已經在途的注入會被序列化。
- 停止中不接受新開始：
  - 同一實例：`isStopping` 不會重置，開始請求會被拒絕。`startForeground` 放在拒絕判斷之前，所以不會觸發 FGS 逾時。
  - 跨實例：靠 `check(!cleanupMutex.isLocked)`。因為 `UNDISPATCHED`，無競爭時 mutex 會在 `handleStop` 返回前同步取得。
- `onDestroy` 不取消清理：清理跑在 `terminalScope`，由 `cleanupInProgress` 去重；destroyed 時不呼叫 `stopSelf`。

**INV-2 通過**
- 清理失敗時，provider 保留在 `registeredProviders/enabledProviders`，`flpEnableTask` 也不清除。
- `Teardown` 對應到 `ProviderTeardownFailed`，對話框沒有導向設定頁的分支。
- 權限撤銷路徑會先保留錯誤再設 IDLE，不會宣稱清理成功。

**INV-3 通過**
- null、mock、失敗、取消都會走到 toast，不會更新鏡頭。
- `SecurityException` 使用專用字串，可以和其他失敗區分。

**INV-4 通過**
- MapScreen 的呼叫點沒有變。
- VM 只改了一個錯誤對應。
- marker、儲存點、資料、權限政策都沒有變動。

## 驗收條件

| 項目 | 狀態 | 說明 |
|---|---|---|
| AC-1 | 通過 | engine 測試涵蓋：失敗 provider 保留後重試、FLP 失敗、未確認前不結束、timeout、只有 provider 的裝置 |
| AC-2 | 通過，有小缺口 | 主執行緒不阻塞的分析見下節。重複停止、destroy、已完成後不清除新 session、撤銷後退出都有測試。缺口：`DeveloperModeDisabled` 和 `CheckFailed` 保持可重試的服務端行為沒有測試，只有 `NotAllowed` 有 |
| AC-3 | 部分 | 請求政策有測試（maxAge=0、duration、mock/null、timeout、取消）。「不更新鏡頭並顯示失敗」只經程式碼審閱，沒有 Compose 測試 |
| AC-4 | 尚未完成 | Gradle 三項依主機回報為通過，本審查為獨立審查。Pixel 完整流程複測仍在進行，目前不能算證據。Samsung 未驗證 |

## 執行緒、生命週期與復原

- **B1 確認修正**：
  1. `terminalScope` 是 IO，`launch(UNDISPATCHED)` 在 Main 上開始執行。
  2. 接著 `withContext(Dispatchers.IO)` 因為是同一個 dispatcher，會走快速路徑，仍然停在 Main。
  3. `yield()` 在這裡會強制 dispatch，所以 `synchronized` 和 `removeTestProvider` 不會在 Main 上執行。

  邏輯正確。
- **timeout**：`TimeoutCancellationException` 被 `catch (e: Exception)` 收進 failures，這是預期行為。`terminalScope` 從不取消，所以不會吞掉外部取消。
- **callback 執行緒**：`Executor { it.run() }` 在 callback 執行緒上 resume，沒有阻塞。

---

## 非阻擋觀察與條件風險

**R1（條件性）同一實例停止後再開始，開始請求會被靜默丟棄**

這次移除了開始時的 `isStopping = false`。如果有任何 VM 路徑對同一個服務實例連續送出 `ACTION_STOP` 和 `ACTION_START_*`（例如切換模式或重新開始），開始請求只會執行 `startForeground` 然後返回。UI 層的計費（步數同步次數或點數）此時可能已經扣除。

- 確認方式：搜尋 `MapViewModel` 及其他送出 `ACTION_STOP` 的呼叫點，看有沒有緊接著送出開始 intent 的序列。
- 沒有這種序列：此項關閉。
- 有這種序列：屬於這次修正造成的回歸，需在範圍內補正，不需要新的授權。

**R2 撤銷權限時錯誤訊息被覆寫**

注入途中權限被撤銷時，流程是：`setMockError(SetLocation)` → `handleStop` → 清理很可能也失敗 → `setMockError(Teardown)` 覆寫前一個錯誤。兩者都是不導向設定頁的一般錯誤，結果仍符合 INV-2，只是訊息從「注入失敗」變成「清理失敗」。

**R3 跨實例的極窄競態**

舊實例在清理中被 destroy 後，它的成功分支在 mutex 釋放之後才設 `IDLE` 並清除位置。如果新實例剛好在這段微秒級的空檔完成 setup，新 session 的狀態可能被清掉。這需要「清理中被 stopService」加上「微秒內有新的開始請求」同時發生，屬理論風險，目前不需修改。

**R4 FLP 持續失敗時服務會一直停在前景**

如果權限為 Allowed，但 FLP 啟用成功或仍在等待，而退出持續失敗或逾時，服務會留在前景，每次停止都失敗，唯一出口是強制停止。這是設計刻意的取捨（不假報成功），列為已知限制。

**R5 依賴宣告方式**

`play-services-location:21.3.0` 直接寫死版本，沒有走 version catalog（`libs.*`）。建議確認兩件事：
- catalog 或既有的傳遞依賴是否已經提供這個套件，若有就改用 alias。
- `dependencies` 樹中 GMS 版本沒有被意外升級，合併後的 manifest 沒有新增權限。

**R6 跨實例在清理中開始會看到 Setup 錯誤**

錯誤內容是「cleanup still in progress」，使用者看得到也可以重試，可以接受。

**R7 模擬進行中按定位的行為改變**

模擬中按定位，現在會顯示「無法取得真實位置」，而不是跳到 mock 位置。這符合 AC-3 的文字，屬於預期內的行為改變，提出來是讓擁有者知道。

## 證明限制

- Pixel 最終「開始 → 停止 → 按定位」複測尚未完成。另外地圖圖磚是空白的（package 白名單沒有改），所以需要用 logcat 或座標數值來證明鏡頭目標是真實位置，不能只靠畫面。
- 隔離 package 與正式簽章不同，結果不能直接推論到正式版安裝。
- Samsung 未實測，不能聲稱該機型已驗證。
- 既有 60 個 SDK33 測試被跳過，屬於既有問題，已揭露；這次新增的 15 項斷言實際有執行。
- 有兩處只經靜態審閱、沒有測試：服務端的 `DeveloperModeDisabled`/`CheckFailed` 分支，以及 FAB 的 UI 行為。
