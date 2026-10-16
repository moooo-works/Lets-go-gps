# Tracker

## Identity

- **Work key:** A-073-foreground-timeout.
- **Active Ask:** A-073.
- **Goal:** 修復模擬定位服務前景啟動逾時的可防範路徑。
- **Last update:** 2026-10-16 23:40:00 +0800.
- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.
- **Reason:** 修復及驗證進行中。
- **Total:** 2.
- **Completed:** 0.
- **Remaining:** 2.

## Accepted task checklist

- [ ] **T-1:** 確認啟動延遲風險，限MockLocationService及必要啟動測試，先失敗後修復；不得更改模擬權限或自動啟動政策。以啟動順序測試證明通知先於依賴工作，無法重現原裝置時明列限制。Source: A-073.
- [ ] **T-2:** 執行test/lintDebug/assembleDebug及Claude獨立審查，提交並推送單主題分支，交付可審PR。Source: A-073.

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-1.
- **Last proven result:** 原服務測試因legacy resources而跳過。
- **Active blocker or running process:** Claude唯讀討論。
- **Next safe action:** 建立確實執行的服務啟動測試。
- **Expected changed files:** MockLocationService.kt、服務測試、A073紀錄。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** yes.
- **Next action remaining:** T-1.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
