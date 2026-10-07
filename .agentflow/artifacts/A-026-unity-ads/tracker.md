# Tracker

## Identity

- **Work key:** A-026-unity-ads.

- **Active Ask:** A-026.

- **Goal:** 完成可先執行的 Unity Ads 接入，保留 PR #58、排除 Mintegral.

- **Last update:** 2026-10-07 20:00:00 +0800.

- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 3.

- **Completed:** 0.

- **Remaining:** 3.

## Accepted task checklist

- [ ] **T-1:** Unity Android App 與橫幅／獎勵 bidding 版位；以後台 Store ID、Game ID 與版位清單驗證，不使用錯誤 com.moo App。Source: A-026.

- [ ] **T-2:** AdMob Unity bidding 來源、兩種廣告單元對應與群組；條款接受待使用者批准，驗證保存後畫面。Source: A-026.

- [ ] **T-3:** 在最新 origin/main 的獨立分支準備相容 SDK／adapter 與必要同意訊號，PR #58 保留；跑 test、lintDebug、assembleDebug，供應狀態須實機驗證。Source: A-026.

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-2.

- **Last proven result:** 正確 Store ID、Game ID 800390974、兩個 BP 版位已確認；錯誤 App 已改名。

- **Active blocker or running process:** AdMob 出價條款批准與工作分頁選擇待答；沒有背景程序。

- **Next safe action:** 等待 AdMob 條款及工作分頁選擇，繼續完整接入。.

- **Expected changed files:** .agentflow/devlog.md、此 tracker；SDK 分支尚未建立。.

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** none.

- **Operation running:** no.

- **Next action remaining:** T-2、T-3.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
