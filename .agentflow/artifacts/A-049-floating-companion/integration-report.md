* _2026-10-08 16:40:25 +0800 (gpt-6.1-sol/high)_

- **接線已完成，尚待 host 建置驗證。** 地點選取、查詢與貼上保持獨立；只有明確執行才啟動模擬。

- **必要限制。** 此 worker 未跑 Gradle 或操作裝置；鍵盤、小地圖及主地圖回歸仍由 host 驗證。本報告不代表功能驗收通過。

- **下一步。** Host 執行必要測試與建置；命令／權限失敗應在原 App 顯示既有健康或錯誤流程。

## 實作範圍

- `MapViewModel.kt` 注入既有 SearchRepository，建立新 controller，以原搖桿 wrapper 顯示同一主題的面板。

- 明確貼上只在浮窗取得焦點後讀剪貼簿；目前與選點複製各自來源。切頁、收合及關閉清方向與焦點；關閉工具不停止模擬。回 App 只由按鈕啟動。

- 新定位、探索及路線播放捕捉原始目標；健康檢查、Pro、權限與次數檢查完成才 dispatch。不先停止活動路線以通過檢查。

- 載入與待走路線由 controller 確認、通過檢查後，先要求停止舊模擬並等待 service 發出 IDLE，最多五秒，才準備新路線；等待中取消不套用新 route。播放是獨立明確操作。暫停／繼續／停止重用既有 service 命令，繼續不收新一次 session 次數。

- `CompanionExecutionRequests.kt` 保留不可變路線及請求代號。新命令、關閉、取消次數對話框、停止與 ViewModel 清理使舊請求失效；晚到的廣告回呼不能啟動失效請求。次數不足後繼續會重新檢查資格。

- 依 host 明確允許，`FloatingCompanionState.kt` 僅增加 `PlayRoute(points)`；既有 VM 測試建構呼叫同步新增 SearchRepository。

- 新啟動若被 Android 拒絕，改呈現既有 Unknown 錯誤且不扣次數；Pro 失效會關工具／清 pending。廣告 unavailable 的晚回結果只更新仍相同的次數待辦。

## 證據與缺口

- Git preflight：`feat/floating-companion`；status 為 `feat/floating-companion...origin/main`，無 behind；fetch 後 origin/main 為 `d01db7d9900d30f2473ab9da36317ed11ad21230`。既有 AGENTS 與 CLAUDE 已讀，檔案範圍不與 host 重疊。

- `git diff --check` 通過。新增 helper 測試涵蓋取消、新命令淘汰舊回呼、活動路線改變、外部 mutable list 不改 snapshot。VM 測試涵蓋 Pro 拒絕零 dispatch／stop、原選定負座標、次數取消後不啟動。

- Host 執行後已核對 VM XML：10 tests、0 failures、0 skipped（2026-10-08 16:45:53 +0800）。原 class 的 SDK 33 會被舊 Robolectric 跳過；限定此 class 改 SDK 28 與明確 Application，並補實際 Flow fixture，使測試真的執行。worker 未取得 helper red-first 執行證據；helper 4 tests 通過由 host 回報。

- 此次通過之後另修正主頁明確新操作取代浮窗待辦的衝突，新增第 11 個 VM 測試，尚待 host 再跑。其觸發為浮窗定位等待次數、主頁改選新點並探索；預期只有主頁新目標探索被執行，不恢復舊浮窗定位。

- Host 尚需最新完整 Gradle／裝置結果及最終 code review。

Self-check: 僅修改受指派 VM、execution helper／tests、必要建構測試及 host 明確允許的單一 state variant；沒有變更資料 schema、廣告制度、依賴或其他工作區；未將未跑測試及裝置行為寫為通過。
