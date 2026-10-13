# Tracker

## Identity

- **Work key:** A-058-release-aab.
- **Active Ask:** A-058.
- **Goal:** 最新功能升1.4.0後建立並驗證Release AAB。
- **Last update:** 2026-10-13 11:43:32 +0800.
- **Evidence commit:** e5580b6c754222bd4ad36924c2a45bbcedf8e680.

## Overall state

- **State:** active.
- **Reason:** AAB已產出，版本／簽章／CRC通過，正完成紀錄驗證與交付。
- **Total:** 3.
- **Completed:** 2.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 自最新main建立獨立release worktree，FF承接help最新已驗證功能；只升app/build.gradle.kts兩行為1.4.0／10400並commit，不合併原PR。Proof: e5580b6差異4lines與原2e7b99b基底。Source: A-058及先前獨立worktree授權.
- [x] **T-2:** 完成required test／lintDebug／assembleDebug。Scope: 原構建，不改App功能或測試。Proof: Gradletasks成功與285/0fail/0error/60skip XML；整批首次僅signReleaseBundle失敗，分開記錄，不冒稱整批成功。Source: A-058與AGENTS.md.
- [ ] **T-3:** 產出沿原key的Release AAB，驗證版本／JAR簽章／zipCRC及SHA256，複製明確檔名到dist，push版本分支並完成紀錄。Scope: ignored本機簽章配置與AAB、root A058紀錄；不產新key、不上傳Play、不mergePR。Proof: bundleRelease成功、直接AAB檢查與實檔link；失敗先診斷必要構建邊界。Source: A-058.

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-3.
- **Last proven result:** 版本已commit；test／lintDebug／assembleDebug成功。
- **Active blocker or running process:** none.
- **Next safe action:** 保存最新AAB接受證據並完成紀錄交付。
- **Expected changed files:** release worktree app/build.gradle.kts；root devlog與A058 artifact；local.properties／dist AAB均ignored不提交。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-3.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- 此為恢復點，工作依T3繼續，非停止信號；獨立停止條件仍須尊重。
- 完成需真實AAB檔案／驗證與Git證據，不以已編譯替代完整打包。
