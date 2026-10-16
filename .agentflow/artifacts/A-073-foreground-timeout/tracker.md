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
- **Completed:** 1.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 確認啟動延遲風險，限MockLocationService及必要啟動測試，先失敗後修復；不得更改模擬權限或自動啟動政策。以啟動順序測試證明通知先於依賴工作，無法重現原裝置時明列限制。Source: A-073. Proof: SDK28 Robolectric新服務測試6項全過、0skip；stopId修復前expected2 actual0失敗，修復後通過。
- [ ] **T-2:** 執行test/lintDebug/assembleDebug及Claude獨立審查，提交並推送單主題分支，交付可審PR。Source: A-073.

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-2.
- **Last proven result:** stopSelf編號測試先失敗（expected2 actual0）；修復後4個SDK28測試通過且零跳過，新增2個驗證正隨完整test執行。
- **Active blocker or running process:** gradlew test lintDebug assembleDebug。
- **Next safe action:** 驗收完整測試並獨立審查。
- **Expected changed files:** MockLocationService.kt、服務測試、A073紀錄。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** yes.
- **Next action remaining:** T-2.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.
- Work continues with the next unfinished item unless an independent stop condition applies.
