# Tracker

## Identity

- **Work key:** A-049-floating-companion.
- **Active Ask:** A-049.
- **Goal:** 開發獨立Pro懸浮搜尋、收藏/資料夾、路線/控制、座標複製與小地圖選點，保留既有模擬與資料。
- **Last update:** 2026-10-08 16:08:28 +0800.
- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.
- **Reason:** 需求/規格與系統邊界正在查核，尚未開始source修改。
- **Total:** 6.
- **Completed:** 0.
- **Remaining:** 6.

## Accepted task checklist

- [ ] **T-1:** 在root artifact保存R1-R6/INV1-6的需求、current codewalk與spec/Minimality，接受plan commit及既有away-gates Design Go後才source。獨立floating-companion worktree自2c03c72；不混Unity/PR58/DB/新依賴。Source: A-049與A-047/A-048。
- [ ] **T-2:** 新增純state/controller與真正執行的red-first JUnit tests，支援地名/座標/PlusCode、最新查詢、收藏/資料夾交叉篩選、目前與選點來源、待走路線、明確取代確認，取消不dispatch。限定新增懸浮狀態，不修無關引擎。Source: A-049；design R2/R3/R5。
- [ ] **T-3:** 純參數Compose面板：泡泡、搜尋/收藏/路線/控制/小地圖、複製與說明/狀態；沿用搖桿UI，不建SavedState/Hilt VM；4語字串。UI檔可由隔離native worker單獨實作，其他核心檔不併行修改。Source: A-049；design R1-R5。
- [ ] **T-4:** host整合JoystickController/OverlayManager/MapViewModel，動態焦點、owner/composition清理、拖曳邊界、當前模擬座標與不可變目標/pending、Pro/Mock/Health/Step gates；活動route變更需確認；主地圖UX維持。先失败test再修改，權限不足導向主App由明確按鈕處理。Source: A-049；design R4/R6/INV1-6。
- [ ] **T-5:** test/lintDebug/assembleDebug及Pixel7Pro備份/同簽章測試版驗證搜尋/IME/外部App觸控/小地圖/座標/收藏/route/關閉；測試前取得必要使用者系統授權，另一Pixel10不操作。保留原DB/路線/point計數及Mapskey。失敗時恢復已保存APK與資料。Source: A-049、既有備份替換授權與AGENTS。
- [ ] **T-6:** 獨立最終防守性security/acceptance review覆蓋R/INV並引用精確source；處理必要發現、Result Go、同步main/behind0、單一主題draft PR（不合併）、push與完整交付。保留PR58及Unity群組。Source: A-049、Agentflow與AGENTS。

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-1.
- **Last proven result:** 已建floating-companion worktree；Git/AGENTS/current source查核完成；Pixel7Pro連線但mock deny/overlay default。
- **Active blocker or running process:** native floating_contract只讀需求/codewalk/spec，寫指定3份docs；沒有產品程式執行。
- **Next safe action:** 接受規格与plan commit/Design Go，寫行為失敗測試。
- **Expected changed files:** worktree ui/map新state/controller/view及JoystickController/OverlayManager/MapViewModel/MapOverlays或字串、對應tests；rootartifact/design/requirements/codewalk/spec/reports/tracker及devlog。不修改外部Unity stream。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** yes.
- **Next action remaining:** T-1至T-6.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
