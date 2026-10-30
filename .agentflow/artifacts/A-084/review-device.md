# 補充獨立審查：裝置回歸測試與 AC-4（A-084）

## 結論

**沒有阻擋項目。** R1 關閉。新增的裝置測試邏輯正確，結果有效。修訂後的 AC-4 可以接受，但結案文字需要精確寫出「停止」證明到哪一層（見下文）。

本輪是靜態審查。我沒有執行建置或裝置測試，裝置結果都是依主機的回報。

## R1：關閉

依 STOPCONTEXT 與主機的呼叫點盤點：

- `UseRoute` 送出 STOP 後，會等 repository 變成 `IDLE`（5 秒，比 FLP 的 3 秒 timeout 長），然後只準備路線，不會自動開始。
- `PlayRoute` 是另一個需要使用者明確操作的 action。
- 其他 STOP 呼叫點也不會自動開始。

所以「同一實例 STOP 之後緊接 START」的序列不存在。

**殘餘的窄窗口（非阻擋）**：如果服務在成功分支中先讓 `IDLE` 可見，之後才在 Main 上呼叫 `stopSelf`，那麼使用者在這幾毫秒內按播放，START 會落在 `isStopping=true` 的舊實例上而被丟棄。

- 建議確認：成功分支裡設定 `IDLE` 和呼叫 `stopSelf` 的先後順序。
- 這與 R3 是同一等級的理論風險，不需要為此修改。

## 新增測試的正確性

**正確的部分**

- `assumeTrue` 放在 `ActivityScenario.launch` 之前。不符合條件時是 skip，不會失敗，也不會留下 activity。
- runner 狀態碼是 `1` 接著 `0`，沒有 `-4`（假設失敗）。所以測試確實執行並通過，不是被跳過。
- 啟動 `MainActivity` 讓 app 處於前景，可以避開背景定位的節流。
- `runOnMainSync` 加上 IO scope 的 `launch(UNDISPATCHED)`，重現了服務原本的進入形態：在 Main 上開始，第一次 suspend 之後才離開 Main。
- 有 `CompletableDeferred` 加 10 秒 `withTimeout`，例外會帶回測試執行緒。
- `freshDeviceLocation` 是正式程式碼，在 runBlocking 的 instrumentation 執行緒上呼叫，不在 Main 上。
- 測試前已強制停止 Google Maps，排除了原本「開過 Maps 才恢復」的干擾。

**限制（都不阻擋，列為證明範圍）**

1. **缺少正向對照。** 測試沒有在停止前斷言「mock 確實生效」，例如先取得一次 `isMock==true` 的位置。如果 `setLocation` 沒有生效，測試也會通過。另外也沒有在舊程式碼上跑出紅燈的紀錄。因此它證明的是「停止後能拿到真實位置」，不能證明「它攔得住原本的回歸」。
2. **不能證明 B1。** 測試沒有斷言 `removeTestProvider` 或 `synchronized` 不在 Main 上執行。在 Main 上做 binder 呼叫預設不會丟例外，所以不管 B1 有沒有修好，這個測試都會通過。註解寫「Match the real Service's previously failing entry/context」，只代表進入形態相同，不代表驗證了執行緒。
    - 另外請確認 `withContext(IO)` 加 `yield()` 是放在 engine 還是 Service。若在 Service，這個測試完全沒有經過那段程式碼。
3. **繞過了 Service。** 測試直接呼叫 `engine.teardownMockProvider()`，沒有經過 ACTION_STOP、`handleStop`、`cleanupMutex`、`isStopping` 和 repository 的 `IDLE` 轉換。這些仍然只由 JVM 測試和靜態審查涵蓋。
4. **FLP 由測試自行開啟。** 測試自己呼叫了 `setMockMode(true)`，所以無法區分 engine 自己的開啟與追蹤路徑是否正確。不過結果是非 mock，可以反推 FLP 確實已退出 mock 模式。
5. **新鮮度只靠 API 契約。** 新鮮度依賴 `maxUpdateAge=0`，測試沒有比對 `elapsedRealtimeNanos` 是否晚於停止完成的時間。總耗時 2.7 秒，這個定位可能來自 fused 或網路，但仍然是真實定位。
6. **小問題。**
    - `finally` 再呼叫一次 teardown，如果它丟例外，會蓋掉原本的斷言失敗。
    - 其他 app 正在進行的定位請求沒有受到控制。
    - import 的排列順序和萬用字元寫法可以順手整理。

第 1、2、5 點可以列為後續改進，不需要為了結案修改。

## 修訂後的 AC-4 判定

**通過（有範圍限定）。** 結案時建議寫成：

> Pixel 7 Pro / Android 17，隔離的 debug 簽章 package：engine 清理從 Main 以 UNDISPATCHED 進入並成功完成，之後正式的 `freshDeviceLocation` 在 Google Maps 已強制停止的情況下，取得非 mock 的新定位。

**不在範圍內，需要明確揭露：**

- 裝置上 Service intent 停止流程與 `IDLE` 轉換的端到端驗證
- 透過 `MyLocationFab` 移動鏡頭與地圖渲染（受 package 白名單影響，只經靜態審查）
- 正式簽章與正式 package 的安裝（FLP 行為不受簽章影響，推論合理，但沒有實測）
- Samsung 機型

這些限制都不需要為了取得 Samsung 硬體或修改 Cloud 存取設定而改動原始碼。

## 沿用自前次審查、仍然有效的項目

- AC-2：`DeveloperModeDisabled` 和 `CheckFailed` 保持可重試的服務端行為，仍然沒有測試。
- AC-3：FAB「不移動鏡頭並顯示失敗」沒有 Compose 測試。
- R2 到 R7 的狀態不變。
