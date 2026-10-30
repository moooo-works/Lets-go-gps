# 最終增量審查：`MyLocationFab` 動畫逾時與 fallback

## 結論

**沒有阻擋項目。** 這次的修改範圍很小，生命週期、逾時和取消的語意都正確，也保留了「只接受新鮮、非 mock 定位」的政策。

這是靜態審查，我沒有執行任何建置或測試。測試結果（lintDebug、assembleDebug、326 項、60 項既有 skip、0 失敗）和裝置結果都是依主機的回報。

## 增量本身的正確性

**範圍與最小性**

- 只修改了 `MapOverlays.kt` 中 `onLocationFound` 之前的鏡頭移動，並新增 `withTimeoutOrNull` 和 `CameraPosition` 兩個 import，兩者都有用到。
- `freshDeviceLocation`、Toast 分支、`SecurityException` 處理、ViewModel 和設定都沒有改動。
- `onLocationFound` 仍然只會在拿到非 null、非 mock 的 `loc` 之後才呼叫，所以新鮮非 mock 政策不受影響。

**逾時語意**

- `withTimeoutOrNull` 只吞掉自己的 `TimeoutCancellationException`，並回傳 `null`，接著執行 setter fallback。
- 外層 scope 被取消時（例如 FAB 離開 composition，`rememberCoroutineScope` 被取消），`CancellationException` 會穿過 `withTimeoutOrNull`，再由外層 `catch (e: CancellationException) { throw e }` 重新拋出。因此 composable 被 dispose 之後，不會執行 setter，也不會呼叫 `onLocationFound`。這是正確的。
- `finally { locating = false }` 在所有路徑上都會執行。spinner 的上限現在是 16 秒（定位）加 2 秒（動畫），不會再無限等待。

**fallback 的效果**

- 依 maps-compose 的一般實作：
    - 地圖尚未 attach 時，`position` setter 會寫入 `rawPosition`，等地圖 attach 後套用。動畫本來在等地圖的 callback，取消時會被移除。
    - 地圖已 attach 時，setter 會呼叫 `moveCamera`，這會中斷任何還在進行的同目標原生動畫。
- 逾時剛好與動畫完成同時發生時，最壞情況只是多做一次對同一目標的 `moveCamera`。這是冪等的，沒有影響。
- 動畫和 setter 都在 composition 的 Main dispatcher 上執行，符合 GoogleMap 的執行緒要求。
- 之後 `onLocationFound` 會讓 `centerLocation` 更新，觸發 `LaunchedEffect(uiState.centerLocation)`。此時鏡頭已經在目標位置，距離 ≤ 1 m，不會再設定一次，也不會閃動。

**對原始目標的影響**

原始目標是「停止模擬後能拿到真實位置」。這個修正位在 engine teardown，增量沒有碰到。增量只確保拿到真實定位之後，ViewModel 的 `onCameraMove` 一定會被呼叫，即使動畫卡住也一樣。這強化了使用者可見的結果，沒有削弱它。

## 殘餘風險（都不阻擋）

1. **手勢或其他鏡頭移動打斷動畫。** 這是既有行為，不是本次引入的。
    - maps-compose 在 `onCancel` 時，會以 `CancellationException("Animation cancelled")` 恢復 continuation。這個例外會穿過 `withTimeoutOrNull` 並被重新拋出，所以不會呼叫 `onLocationFound`，也不會顯示 Toast。
    - 使用者拖動時，`isMoving` 的 `LaunchedEffect` 會把 ViewModel 同步到拖動後的位置，所以結果可以接受。
2. **依賴函式庫版本。** 上面對 maps-compose `animate` 和 setter 的描述，是依該函式庫常見的實作推論。本次沒有提供版本或原始碼，我無法逐行確認。不過結論在合理的實作差異下仍然成立。
3. **長距離跳轉的視覺效果。** 如果原生預設動畫超過 2 秒，畫面會在途中直接跳到目標。最終狀態正確，只是視覺上不平滑。

## 證明範圍與限制

- **增量沒有自動化測試或裝置測試涵蓋。** 裝置 instrumentation 測試直接呼叫 `freshDeviceLocation`，沒有經過 `MyLocationFab`。AC-3（FAB 失敗時不移動鏡頭並顯示錯誤）仍然沒有 Compose 測試，狀態和前次審查相同。
- **地圖渲染沒有驗證。** 隔離 package 無法取得圖磚，所以完整圖磚渲染與實際鏡頭停在目標位置都沒有驗證。不能從「spinner 會結束」推論「渲染出的鏡頭位置正確」。
- **Samsung 機型無法取得**，沒有實測。正式簽章與正式 package 也沒有實測。
- 前次審查列出的範圍限定（Service intent 端到端、`IDLE` 轉換、AC-2）維持不變。
