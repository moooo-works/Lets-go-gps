# A-082 測試證據

## 範圍與限制

來源：使用者「開始測試」。只測現有程式與已安裝版本，不修產品、不建立或安裝 APK。既有 A-077 未交付紀錄保留。主機已讀共用 project-rules，專案 rules.md／sys-invariants.md／backlog.md 不存在；未建立新保證或排程。既有 AGENTS 模擬權限三態與錯誤分流是後續修正約束，本輪不改其行為。

## Git preflight

- branch: main
- status: main...origin/main；.agentflow/devlog.md 已修改、A-077/ 未追蹤。
- git fetch origin --prune：sandbox FETCH_HEAD 寫入被拒，核准提升後成功。
- origin/main: 62b9898bf98a78c8f3d1439df5a28c48cf784e8c
- 最近五筆：62b9898 補齊v9遷移回覆與恢復狀態；bed4426 遷移專案設定至 Agentflow v9；202580c 說明Debug APK原始輸出與交付副本位置；17d6d6a 交付含前景服務修復的1.4.3 Debug APK；0135214 記錄前景服務當機修復與PR66交付。

## 自動測試

`./gradlew test` 初次因缺 MAPS_API_KEY 失敗。以僅供測試的佔位環境值重跑先命中快取，再以 `--rerun-tasks` 實際執行：311項、60 skipped、0 failure、0 error，即251通過。baseline-test.log 保存完整建置摘要。XML確認 AndroidLocationMockEngineTest 8/8 skip、MockLocationServiceTest 3/3 skip，原因為 Robolectric legacy resources mode 不支援 P 之後 SDK；MapViewModelTest 17項通過。不能把整體建置成功當作定位引擎測試通過。

以額外暫存 Kotlin 測試來源及 init script，在 Robolectric SDK28／Application 下測現有 engine；未改主產品來源。MockTeardownProbeTest 2項實際執行、0 skip、2 assertion failures：

- 移除gps provider拋錯後，enabledProviders 不再含gps：證實失敗項被清掉，無法據此重試。
- setMockMode(false) 回傳已完成且失敗的 Task 時，teardown 正常返回：證實非同步失敗未交給呼叫端。這是函式可觀察性的缺口證據，不表示現介面唯一正確修正必須採拋例外；也不證明未送出的 Android LocationManager 錯誤完全不會經 errors flow 回報。

測試原文、init及probe-results.xml／probe-test.log保存於本目錄。重現時先把兩個測試檔還原到init所記的 `/private/tmp/gps-a082-tests` 與 init路徑，再執行 `MAPS_API_KEY=unit-test-placeholder ./gradlew -I /private/tmp/gps-a082-tests.init.gradle testDebugUnitTest --tests '*MockTeardownProbeTest'`。第一版 init 的 java sourceSet 未讓 Kotlin 找到測試，已改用 kotlin sourceSet後成功發現與執行。

## Pixel 7 Pro 實機

裝置2A151FDH300HUZ、Android17，已安裝App 1.4.3／10403、minSdk32。未比對 APK 與 main 的完整來源身分，所以不宣稱手機與單元測試使用同一提交，也未重新驗收 PR66。

初次開始被健康檢查擋住：定位、通知、mock app皆未授權。mock app操作前顯示 No operations／Default mode deny。為測試暫時授權後，由UI開始單點模擬，畫面確認模擬中，dumpsys確認gps/network mock overrides、各provider mock位置。

按停止後畫面回到開始按鈕；gps/network override均移除、其last location為null。但passive/fused last location仍標mock，按定位畫面維持原模擬點。這是本App定位按鈕停在舊點的實機觀察，不能單靠Android系統fused快取判定Google FLP仍在mock mode。

開啟Google Maps後，四種provider都出現較新的非mock位置、無mock override。證明這次Pixel可恢復非模擬定位；沒有重現持續困在Google模擬模式。Maps後回到App再按定位的UI dump未取得座標文字，該次鏡頭恢復未完成驗證，不列成功。

去座標狀態存於device-summary.json；私人截圖及原始定位dump只留/tmp，不加入專案。最後mock app還原default，撤回此次定位／通知測試授權。沒有卸載、清資料、修改位置清單或正式帳號。

## 主機複核與交付

人工複核測試失敗與實際程式行為一致；本輪是測試診斷，無產品實作或修正完成聲明。既有全套測試有skip、隔離探針2失敗，不能標示修復完成；Samsung Android16仍無實機證據。未跑lintDebug／assembleDebug：未修改產品或產出APK，也沒有提出合併。未commit/push，保留既有A077測試限制及當前失敗證據。
