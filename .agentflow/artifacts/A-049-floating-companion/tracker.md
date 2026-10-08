# Tracker

## Identity

- **Work key:** A-049-floating-companion.
- **Active Ask:** A-049.
- **Goal:** 開發獨立Pro懸浮搜尋、收藏/資料夾、路線/控制、座標複製與小地圖選點，保留既有模擬與資料。
- **Last update:** 2026-10-08 17:09:16 +0800.
- **Evidence commit:** 915546401df32839243034deb6185a1b36794364.

## Overall state

- **State:** active.
- **Reason:** 程式與Code review完成，草稿PR59已建立；完整Acceptance等待Pixel7Pro系統授權及實機。
- **Total:** 6.
- **Completed:** 4.
- **Remaining:** 2.

## Accepted task checklist

- [x] **T-1:** 在root artifact保存R1-R6/INV1-6的需求、current codewalk與spec/Minimality，接受plan commit及既有away-gates Design Go後才source。獨立floating-companion worktree自2c03c72；不混Unity/PR58/DB/新依賴。Source: A-049與A-047/A-048。 Proof: 需求/spec PASS與codewalk已讀，plan d01db7d已push，away-gates Design Go綁此commit。
- [x] **T-2:** 新增純state/controller與真正執行的red-first JUnit tests，支援地名/座標/PlusCode、最新查詢、收藏/資料夾交叉篩選、目前與選點來源、待走路線、明確取代確認，取消不dispatch。限定新增懸浮狀態，不修無關引擎。Source: A-049；design R2/R3/R5。 Proof: Source9155464；controller11/helper4純JUnit實際執行全PASS，red4中3fail及reviewred13中2fail已記錄；query/folder/current/queue/confirmation及late路線取消核對。
- [x] **T-3:** 純參數Compose面板：泡泡、搜尋/收藏/路線/控制/小地圖、複製與說明/狀態；沿用搖桿UI，不建SavedState/Hilt VM；4語字串。UI檔可由隔離native worker單獨實作，其他核心檔不併行修改。Source: A-049；design R1-R5。 Proof: Source9155464；4語44keysXML/duplicate檢查及整合assemble成功，純參數view無VM；完整runtime歸T5，未冒稱通過。
- [x] **T-4:** host整合JoystickController/OverlayManager/MapViewModel，動態焦點、owner/composition清理、拖曳邊界、當前模擬座標與不可變目標/pending、Pro/Mock/Health/Step gates；活動route變更需確認；主地圖UX維持。先失败test再修改，權限不足導向主App由明確按鈕處理。Source: A-049；design R4/R6/INV1-6。 Proof: Source9155464 Code Outcome/Minimality/Conformance PASS（review-final.md）；VM13/0/0、manager3/0/0、position1/0/0，F1/F2 red/green與caller接線已驗證；實機歸T5。
- [ ] **T-5:** test/lintDebug/assembleDebug及Pixel7Pro備份/同簽章測試版驗證搜尋/IME/外部App觸控/小地圖/座標/收藏/route/關閉；測試前取得必要使用者系統授權，另一Pixel10不操作。保留原DB/路線/point計數及Mapskey。失敗時恢復已保存APK與資料。Source: A-049、既有備份替換授權與AGENTS。
- [ ] **T-6:** 獨立最終防守性security/acceptance review覆蓋R/INV並引用精確source；處理必要發現、Result Go、同步main/behind0、單一主題draft PR（不合併）、push與完整交付。保留PR58及Unity群組。Source: A-049、Agentflow與AGENTS。

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-5.
- **Last proven result:** 完整274tests0fail60skip，相關32實際執行；最新VM13已補Allowed再PASS；build/lint命令成功但34既有lint errors。Source9155464 Code review PASS、PR59 draft；備份安裝後DB375/6/591不變，主地圖有圖資。
- **Active blocker or running process:** 沒有執行中工具/worker；Pixel7Pro overlay/mock授權async問題待使用者回覆。
- **Next safe action:** 收到手機設定完成後先讀AppOps確認，再執行overlay/IME/外部觸控/小地圖/route/主地圖4UX；不自動grant。
- **Expected changed files:** worktree ui/map新state/controller/view及JoystickController/OverlayManager/MapViewModel/MapOverlays或字串、對應tests；rootartifact/design/requirements/codewalk/spec/reports/tracker及devlog。不修改外部Unity stream。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-5及T-6完整Acceptance/Result Go；PR59保持draft，不要求merge。
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
