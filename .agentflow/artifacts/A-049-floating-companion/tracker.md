# Tracker

## Identity

- **Work key:** A-049-floating-companion.
- **Active Ask:** A-049.
- **Goal:** 開發獨立Pro懸浮搜尋、收藏/資料夾、路線/控制、座標複製與小地圖選點，保留既有模擬與資料。
- **Last update:** 2026-10-08 19:48:28 +0800.
- **Evidence commit:** ddeb51cae1227fccb6efb4defd70635d1eef770a.

## Overall state

- **State:** complete.
- **Reason:** Android17 fix and floating runtime acceptance PASS; draft PR59/60 delivered, original data preserved.
- **Total:** 7.
- **Completed:** 7.
- **Remaining:** 0.

## Accepted task checklist

- [x] **T-1:** 在root artifact保存R1-R6/INV1-6的需求、current codewalk與spec/Minimality，接受plan commit及既有away-gates Design Go後才source。獨立floating-companion worktree自2c03c72；不混Unity/PR58/DB/新依賴。Source: A-049與A-047/A-048。 Proof: 需求/spec PASS與codewalk已讀，plan d01db7d已push，away-gates Design Go綁此commit。
- [x] **T-2:** 新增純state/controller與真正執行的red-first JUnit tests，支援地名/座標/PlusCode、最新查詢、收藏/資料夾交叉篩選、目前與選點來源、待走路線、明確取代確認，取消不dispatch。限定新增懸浮狀態，不修無關引擎。Source: A-049；design R2/R3/R5。 Proof: Source9155464；controller11/helper4純JUnit實際執行全PASS，red4中3fail及reviewred13中2fail已記錄；query/folder/current/queue/confirmation及late路線取消核對。
- [x] **T-3:** 純參數Compose面板：泡泡、搜尋/收藏/路線/控制/小地圖、複製與說明/狀態；沿用搖桿UI，不建SavedState/Hilt VM；4語字串。UI檔可由隔離native worker單獨實作，其他核心檔不併行修改。Source: A-049；design R1-R5。 Proof: Source9155464；4語44keysXML/duplicate檢查及整合assemble成功，純參數view無VM；完整runtime歸T5，未冒稱通過。
- [x] **T-4:** host整合JoystickController/OverlayManager/MapViewModel，動態焦點、owner/composition清理、拖曳邊界、當前模擬座標與不可變目標/pending、Pro/Mock/Health/Step gates；活動route變更需確認；主地圖UX維持。先失败test再修改，權限不足導向主App由明確按鈕處理。Source: A-049；design R4/R6/INV1-6。 Proof: Source9155464 Code Outcome/Minimality/Conformance PASS（review-final.md）；VM13/0/0、manager3/0/0、position1/0/0，F1/F2 red/green與caller接線已驗證；實機歸T5。
- [x] **T-5:** test/lintDebug/assembleDebug及Pixel7Pro備份/同簽章測試版驗證搜尋/IME/外部App觸控/小地圖/座標/收藏/route/關閉；測試前取得必要使用者系統授權，另一Pixel10不操作。保留原DB/路線/point計數及Mapskey。失敗時恢復已保存APK與資料。Source: A-049、既有備份替換授權與AGENTS。 Proof: ddeb51c Gradle279/0fail/60skip and device-acceptance.md/data-preservation.json; test427 removed, all DB rows identical, mock stopped and input/rotation restored.
- [x] **T-6:** 獨立最終防守性security/acceptance review覆蓋R/INV並引用精確source；處理必要發現、Result Go、同步main/behind0、單一主題draft PR（不合併）、push與完整交付。保留PR58及Unity群組。Source: A-049、Agentflow與AGENTS。 Proof: floating-runtime-final.md Outcome/Minimality/Conformance PASS at exact ddeb51c source; away-gates Result Go after host inspection, source pushed, PR59/60 draft, PR58 untouched.

- [x] **T-7:** 先修Android17/API37 developer設定向一般App回0導致誤判；獨立fix/android17-developer-status worktree/PR，限engine、health consumer、必要測試，AppOps三態不得放寬，修復實機證實後才續T5浮窗。Source: A-049新追問原文及android17-fix-design.md；Proof: source1ecc8a4/獨立review PASS；純health9/engine3全PASS、完整253/0fail/66skip；真機App UID global0 mockAllowed，instrument修前1fail修後OK1，健康UI全部通過。整合source63030d7完整279/0fail/60skip與realAppUID再OK1。

## Accepted scope changes

- Change: 新增Android17開發者選項誤判修復。 Source: A-049「你先檢查android 17的版本，我已經開啟開發者選項，但是app這邊監測結果是「未開啟」，這是bug需修復，修復完成後再來測試懸浮工具」。 Effect: T7先於T5，獨立fix PR避免與PR59混題。

## Current recovery

- **Current item:** none.
- **Last proven result:** 279 tests/0fail/60skip, required Gradle successful, independent final acceptance PASS. Original DB375/6/591 rows hash identical; mock stopped, keyboard and rotation restored.
- **Active blocker or running process:** none.
- **Next safe action:** none.
- **Expected changed files:** worktree ui/map新state/controller/view及JoystickController/OverlayManager/MapViewModel/MapOverlays或字串、對應tests；rootartifact/design/requirements/codewalk/spec/reports/tracker及devlog。不修改外部Unity stream。

## Completion proof

- **All accepted tasks checked:** yes.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** none.
- **Evidence status:** complete.
- **Judgment:** complete.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
