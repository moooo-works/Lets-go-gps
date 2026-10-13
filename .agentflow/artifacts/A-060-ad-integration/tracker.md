# Tracker

## Identity

- **Work key:** A-060-ad-integration.
- **Active Ask:** A-060.
- **Goal:** 整合完整廣告分支，交付1.4.1 signed Release AAB與Pro Debug APK。
- **Last update:** 2026-10-13 12:22:55 +0800.
- **Evidence commit:** 0804c8bccfb125cf7e3dc96e36de902b6f443685.

## Overall state

- **State:** active.
- **Reason:** 廣告合併與focused tests成功，完整雙產物建置中。
- **Total:** 4.
- **Completed:** 3.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 從最新main325a3ba新建獨立worktree，承接release00bf功能，整合Unity／UMP／請求前privacy修正及PR58 adaptive／bounded retry，版本1.4.1。Scope: app/build.gradle、libs.versions、Main/Manifest/Settings及廣告類／四語strings／對應unit；保持PR原狀與Mintegral排除。Proof: cherry-pick功能同tree、衝突union、focused20tests green，失敗先限定重疊scope修。Source: A-060與既有PR58保留.
- [x] **T-2:** required test／lintDebug／assembleDebug＋AndroidTest／bundleRelease成功，保留失敗與重跑原因。Scope: 原Gradle與ignored map／signing配置，不改測試掩蓋缺陷。Proof: tasklog/XML；失敗定位必要整合邊界。Source: A-060與AGENTS.md.
- [x] **T-3:** 驗真Release AAB簽章／version／Unity及adapter；驗Debug APK Pro=true／Release=false、APK簽章与現有Help／浮窗基本回歸。Scope: ignored產物／公開UI與必要備份，不觸發付費／生產廣告、不動後台。Proof: 包內內容／generatedFlags／adb必要UI及檔案SHA。失敗保留現象，不拿module宣告冒充打包結果。Source: A-060及先前手機測試授權.
- [ ] **T-4:** 凍結產品／產物證據由Claude full獨立審查，host核對後push整合branch與root紀錄交付。Scope: root A060原report/dispatch/runner/tracker/devlog；不merge舊PR或發版。Proof: 原verdict／模型／clone無改／branchbehind0，未通過保持active。Source: A-060及原Claude review選擇.

## Accepted scope changes

- 廣告分支涵蓋PR58已完成AdMob優化，須保留其程式與原PR；只接SDK／UMP會仍漏廣告功能。Effect: 合併adaptive與retry，加入與同意交界的兩項測試。Source: A-060及原保留PR58／廣告優化授權.

## Current recovery

- **Current item:** T-4.
- **Last proven result:** focused RewardedAdManagerTest20/0，source fbd8a84；Debug原Pro flag仍true／Release false。
- **Active blocker or running process:** none.
- **Next safe action:** freeze code/artifact evidence for final full Claude review.
- **Expected changed files:** 17 app/gradle整合路徑及沿用原SDK設計紀錄，ROOT僅A060紀錄；ignored產物與local.properties不Git。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-4.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- 此為恢復點，不是停止訊號；按下一項繼續，尊重独立停止條件。
- 完成需Git接受證據与實際雙產物，source module宣告不足以證明打包正確。
