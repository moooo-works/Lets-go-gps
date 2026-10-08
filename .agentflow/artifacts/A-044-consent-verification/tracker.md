# Tracker

## Identity

- **Work key:** A-044-consent-verification.
- **Active Ask:** A-044.
- **Goal:** 在完整App驗證正式廣告隱私同意流程，保留PR58與原資料，排除Mintegral。
- **Last update:** 2026-10-08 14:38:14 +0800.
- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.
- **Reason:** 實機與必要檢查完成，正在保存交付證據。
- **Total:** 3.
- **Completed:** 2.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 獨立worktree自最新origin/main帶入Unity既有三個功能提交，確認產品來源一致；完整App暫存測試版使用正式AppID、Google範例廣告與僅測試裝置EEA設定，備份裝置APK／資料；不得修改Unity外部session。證據為Git preflight、app/gradle差異及備份；失敗恢復已驗證APK。Source: A-044、A-042。 Proof: Git source88bc790 app/gradle identical，私有APK及資料備份已建立。
- [x] **T-2:** Pixel7Pro完整App驗證同意／拒絕、選擇前阻擋廣告、重啟保留選擇、設定頁管理並重新選擇、Google測試廣告與基本地圖；記錄UMP狀態及畫面。測試設定僅tmp；若產品缺陷則先失敗測試再最小修正。Pro略過表單本輪延後，Unity供應不列通過條件。Source: A-044、A-043。 Proof: results.md實機情境與私有log／畫面，UMP與TCF選擇正確；沒有測出新產品缺陷。
- [ ] **T-3:** 執行test／lintDebug／assembleDebug必要檢查，恢復一般測試版及裝置原資料，核對Mintegral不在依賴與PR58保留；保存可重現結果，不開正式Unity群組、不發布App。Source: A-044、AGENTS.md。

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-3.
- **Last proven result:** 完整App同意／拒絕／管理／重啟完成；30廣告測試通過；一般APK與原資料已恢復。
- **Active blocker or running process:** none.
- **Next safe action:** 保存結果與交付紀錄。
- **Expected changed files:** root notebook、此tracker及測試結果；產品worktree僅既有程式提交；/private/tmp測試fixture及截圖／備份。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-3保存交付證據。
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
