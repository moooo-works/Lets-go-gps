# Tracker

## Identity

- **Work key:** A-001-unity-sdk.

- **Active Ask:** A-001.

- **Goal:** 接續 Unity SDK、同意流程與驗證；保留 PR #58、排除 Mintegral.

- **Last update:** 2026-10-07 23:07:00 +0800.

- **Evidence commit:** 7fa932facb9289ac5bd14a7880412c68277c8042.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 3.

- **Completed:** 1.

- **Remaining:** 2.

## Accepted task checklist

- [x] **T-1:** 相容 SDK／adapter 與 legacy 排除，僅改兩個 Gradle 檔；test、lintDebug、assembleDebug 與依賴圖驗證. Proof: /private/tmp/letsgo-unity-validation.log BUILD SUCCESSFUL；review-report.md PASS；commit 5adca013828aeddc21f462ea0edbff30ab967848. Source: A-001.

- [ ] **T-2:** 依 design.md 取得 Design Go 後接入 UMP、同意限制與隱私入口；當前未改此流程，需單元與實機驗證。Source: A-001／主 A-026.

- [ ] **T-3:** AdMob 條款批准後完成 Unity bidding 對應與實機單一來源驗證；Game ID 800390974，兩個 BP 版位，Mintegral 排除。Source: A-001 (接續主 A-026).

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-2.

- **Last proven result:** SDK 配對及 legacy／Mintegral 排除確認；三項 Gradle 任務成功，248 tests（66 skipped），lint 34 個既有 Error 與先前報告一致；獨立審查 PASS。

- **Active blocker or running process:** Design Go已收到；同意程式與單元驗證完成，剩Result Go、AdMob條款與實機／供應驗證，無背景程序。

- **Next safe action:** 接續A-002-consent成果；Result Go與AdMob條款待答，實機／供應未證明，條款批准前不保存中介來源。

- **Expected changed files:** app/build.gradle.kts、gradle/libs.versions.toml、ag.json、stream notebook、此工作資料夾.

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** 程式成果Result Go、AdMob出價條款批准、實機／供應證據。

- **Operation running:** no.

- **Next action remaining:** T-2、T-3.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
