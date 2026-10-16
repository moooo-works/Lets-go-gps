# Tracker

## Identity

- **Work key:** A-073-foreground-timeout.
- **Active Ask:** A-073.
- **Goal:** 修復模擬定位服務前景啟動逾時的可防範路徑。
- **Last update:** 2026-10-16 23:40:00 +0800.
- **Evidence commit:** 20a18edb1618009bca137405013ab440d8ff8ea1.

## Overall state

- **State:** complete.
- **Reason:** 修復已提交並交付PR66，實機限制列於驗證紀錄。
- **Total:** 2.
- **Completed:** 2.
- **Remaining:** 0.

## Accepted task checklist

- [x] **T-1:** 確認啟動延遲風險，限MockLocationService及必要啟動測試，先失敗後修復；不得更改模擬權限或自動啟動政策。以啟動順序測試證明通知先於依賴工作，無法重現原裝置時明列限制。Source: A-073. Proof: SDK28 Robolectric新服務測試6項全過、0skip；stopId修復前expected2 actual0失敗，修復後通過。
- [x] **T-2:** 執行test/lintDebug/assembleDebug及Claude獨立審查，提交並推送單主題分支，交付可審PR。Source: A-073. Proof: test/lintDebug/assembleDebug成功；317tests/60既有skip/0failure，新增6tests/0skip；Claude對20a18ed三項PASS，PR66已OPEN，未merge／上架。

## Accepted scope changes

- None.

## Current recovery

- **Current item:** none.
- **Last proven result:** 6新測試全過；必要3項指令成功；Claude三PASS；PR66已建立。
- **Active blocker or running process:** None.
- **Next safe action:** none.
- **Expected changed files:** MockLocationService.kt、服務測試、A073紀錄。

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
