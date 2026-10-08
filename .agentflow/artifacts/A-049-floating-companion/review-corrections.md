* _2026-10-08 16:56:53 +0800 (gpt-6-astra/medium)_

Reviewed commit: d7078bf20f3081c388fc4daeb6762fa48e19b5f7

Review range: f1ae4f367d2127c35df9a9bdf59c2376e1b37ef0..d7078bf20f3081c388fc4daeb6762fa48e19b5f7

- **兩個程式 blocker 已修正。** 舊路線讀取會隨新的操作/停止失效；視窗消失會通知上層清理狀態、ticker 與待辦。本次修正沒有發現新的必修回歸。

- **完整接受仍為 BLOCKING。** 指定手機的浮窗、鍵盤、小地圖與背景操作尚未驗證；程式審查通過不代表功能已完成實機接受。

- **下一步：** 完成原 spec 的裝置證據與交付記錄。原 review.md 保留，這份只補充修正結果。

## Code review verdict

- **Outcome: PASS** — 本次限定修正範圍內，F1 與 F2 的確定程式缺口已封住。

- **Minimality: PASS** — 6 個檔案、87 行新增及 2 行刪除；沿用既有取消方法與 window cleanup，只補必要通知及測試，無依賴、資料庫或其他功能改動。

- **Conformance: PASS** — 就這次修正的 source 契約而言，新的明確操作/停止及非預期 window hide 已接入取消流程。此欄不把原 spec 的未完成裝置證據視為通過。

- **Code review verdict: PASS** — 未發現需要阻擋此修正的新增程式問題。

- **Acceptance: BLOCKING** — 原 F3 未解決；不得據此宣稱所有 R1–R6 已接受、Result Go 或要求合併。

## Corrections checked

### F1 — Resolved in code

- `FloatingCompanionController.cancelPendingActions()` 增加 loadGeneration、取消 loadJob 並清 replacement。select 與新的 request 會呼叫；`MapViewModel.cancelCompanionPending()` 也呼叫，因此既有的新命令、主頁啟動、明確停止及關閉均涵蓋 controller 尚未完成的讀取。

- 即使 repository 不理取消，返回後 generation 不合便無法派送 action。成功的 route load 在 request 內取消自己的舊 job，但其後只有同步檢查/派送；VM 使用 viewModelScope 開新命令，不會繼承這個已取消的 load job。未見因此阻斷正常載入的新增路徑。

- 新 controller 測試分別覆蓋「load → 新定位 → 舊結果返回」只有新 Locate，以及取消待辦後舊結果不派送 action。停止測試直接呼叫取消 helper，並非實際呼叫 VM stop；VM stop 到 helper 的接線由本次 source review 確認，沒有把它寫成服務整合實測。

### F2 — Resolved in code

- manager 的 hide 清 references、composition、window 與 owner 後，僅在確有資源時通知一次；重複 hide 不重複通知。detach listener 檢查目前 view 身分；hide 先清 composeView，因此自己的 remove 不會造成遞迴清理。

- JoystickController listener 設 isJoystickEnabled=false、停止 ticker 並通知 VM。VM 取消命令及 controller 工作，再 close companion；onCleared 在 hide 後移除 listener，不保留已清理 VM 的 callback。

- 新 manager 測試證明 updateViewLayout 拋 SecurityException 後 owner 銷毀且只通知一次。ticker、VM token 與重新開啟的整條接線尚無新增端到端測試；這些清理是 listener 的直接同步操作，本次 source 已核對。實際系統撤權與 detach 仍屬 F3 裝置驗證範圍。

### F3 — Still BLOCKING

- Coordinator 仍回報 overlay/mock 權限等待使用者設定，沒有新的背景浮窗/IME/map/旋轉/反覆開關證據。保留原 review 的裝置限制與可用畫面尺寸疑點，不將它升級為已重現問題，也不宣稱已解決。

- R3/R4 及 INV-2/INV-4 的 F1 程式缺口、R1/R6 及 INV-4 的 F2 程式缺口可關閉。R1/R5/R6 的實機接受，以及原 spec 所列完整 route/control 和主地圖 UX 驗證仍待完成；其他 R/INV 沿用原審查結論。

## Evidence and scope limits

- 已讀精確 commit diff、目標 commit 的 VM/controller 呼叫關係、red log 與修正後 XML。Red log 記錄 13 tests 中兩個失敗，分別為舊 route 覆蓋新定位和 window 更新失敗未通知；不是只增加永遠通過的斷言。

- 重用 coordinator 最新 `./gradlew test lintDebug assembleDebug` 結果；`/private/tmp/floating-required-final.log` 為 BUILD SUCCESSFUL。Coordinator 總結 272 tests、0 failures、60 skips；相關五個 suite 共 30 個實際執行通過。已直接核對 controller 11 與 manager 3 的 XML，皆零 failures/errors/skips；未重跑已通過的測試。

- Lint 仍為既有 34 errors、95 warnings、3 hints，Gradle exit 0 不是零 lint 問題。原報告列出的 gate 變更、晚回廣告、STOP timeout 等更廣測試缺口未因這三個新測試消失，也未在本次有限修正 review 中重做完整認證。

- **Minimality recheck:** 刪除 generation 增加會再允許不理取消的舊結果；刪除 dismiss bridge 會再漏掉上層清理。合併兩者沒有共同 lifecycle，反而混淆責任；重用既有 cancel/hide 已是較小修正。沒有提出額外產品行為。

- 本 reviewer 沒有修改 source/log/Git/config、執行測試、操作手機/網路或委派；只寫 review-corrections.md。Shared filesystem 並非 OS 強制唯讀。必要 commits、notebook、STATUS、route records、behind-main 與 PR 交付仍由 coordinator 記錄；本報告不取代那些證據。

Self-check: 精確 Reviewed commit 與範圍已固定；原 review.md 未改；F1/F2 source 修正與測試限制分開；F3 保持 BLOCKING；Outcome/Minimality/Conformance 僅用於限定 code review，沒有宣稱完整接受；只寫授權報告。
