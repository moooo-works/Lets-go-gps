2026-10-16｜Claude Opus 5.5／低思考量（只讀程式碼，沒有實機重現）

**結論：**我找到一個可以確定的程式缺陷：快速「停止→開始」時，停止動作可能把剛排隊的開始指令一起關掉。這個缺陷產生的就是這次這種當機，但還沒證明 POCO 那次是它造成的。建議先修這一處，不需要改成在 onCreate 提升。

- 名詞：**前景服務**是會在通知列顯示、系統不會隨便關掉的服務。用 `startForegroundService` 啟動後，服務必須在期限內呼叫 `startForeground` 完成提升，否則系統會讓 App 當機。

## 已證實的缺陷

- **觸發**：`handleStop()` 呼叫的是不帶編號的 `stopSelf()`（`MockLocationService.kt:680`），會無條件關掉服務。
  - 如果新的開始指令已經送進系統、但還沒輪到執行，這個服務會在提升前被關掉。
  - Android 遇到「等待提升中就被關掉」，會直接丟出同一種 ForegroundServiceDidNotStartInTimeException。

- **可能的路徑**：
  - 點通知的「停止」後，馬上在 App 裡按開始。
  - 連續注入失敗（`:285`）或路線結束（`:659`）在背景執行緒呼叫 `handleStop()`，剛好和主執行緒上的新開始撞在一起。

- **另一個問題**：`handleStop()` 先把狀態設成 IDLE（`:676`），才呼叫 `stopSelf()`。介面看到 IDLE 就允許再啟動，但服務其實還沒關完。

## 還沒證實的推測

- **主執行緒卡住**：服務的依賴都是輕量建構（DataStore 和 Health Connect 都是用到時才建立），而且服務都從前景介面啟動，App 已經在執行中，所以服務本身的延遲很小。
  - 比較可能的是 App 其他地方卡住主執行緒，例如 `BillingManager.kt:69`、`FeatureCreditStore`、`AdUnlockStore` 裡的 `runBlocking`（在主執行緒同步等待讀檔），或廣告、地圖初始化。

- **提升失敗後的處理**：`startForeground` 失敗時，catch 會呼叫 `stopSelf()`（`:438`）。這同樣是「提升前關閉」，可能仍會當機，需要實機確認。

## 關於「在 onCreate 先提升」

- **目前不建議**。onCreate 拿不到啟動指令內容，無法分辨是開始還是停止。
  - 如果是在背景用 `startService` 送來的停止、暫停指令，這時強行提升反而可能被系統拒絕。
  - 改成延後載入依賴，能省的時間也很少。

## 最小修正

1. 在 `onStartCommand` 記錄最新的啟動編號 `latestStartId`（系統替每次啟動指令編的號）。`handleStop` 改成呼叫 `stopSelf(latestStartId)`；這樣後面還有新的開始指令時，服務不會被關掉。

2. 在背景執行緒觸發的兩處 `handleStop()`（`:285`、`:659`），改成先切回主執行緒再執行，讓它和 `onStartCommand` 依序執行。

## 回歸測試

- 在 Robolectric（不用實機就能模擬 Android 的測試工具）的 `MockLocationServiceTest` 裡：
  - 先送開始（編號 1），再送停止（編號 2）和開始（編號 3）；確認停止只用編號 3 以前的指令關服務，而且最後一次開始有完成提升。
  - 確認注入失敗觸發的停止，是在主執行緒執行的。

- 限制：Robolectric 模擬不出系統的提升期限，還需要在 Android 16 實機上反覆「停止→立刻開始」20 次以上。

- 下一步：拿到當機前的 logcat，記錄開始指令送出到 `onStartCommand` 執行之間的時間差，才能判斷是競態還是主執行緒卡住。

Self-check: 只讀了檔案，沒有改檔、沒有跑測試，也沒有委派；已確定和推測的分開寫；有附上時間戳記和模型資訊。
