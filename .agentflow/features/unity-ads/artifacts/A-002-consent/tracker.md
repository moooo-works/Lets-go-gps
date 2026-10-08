# Tracker

## Identity

- **Work key:** A-002-consent.

- **Active Ask:** A-002.

- **Goal:** 實作已批准的Unity同意流程、保留PR#58、排除Mintegral.

- **Last update:** 2026-10-07 23:36:00 +0800.

- **Evidence commit:** 7fa932facb9289ac5bd14a7880412c68277c8042.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 3.

- **Completed:** 2.

- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 固定requirements/spec與美國州訊號契約，沿用已批准design；以官方與本機SDK證據確認. Proof: contract-report.md，commit02774b4；Design Go5adca01. Source: A-002.

- [x] **T-2:** 完成UMP更新／表單／隱私選項、初始化與橫幅／獎勵限制，失效舊廣告；focused tests及test/lintDebug/assembleDebug驗證。Proof: /private/tmp/letsgo-consent-reviewed-validation.log exit0，269 tests無失敗、66既有略過，commit7fa932f；source實作完成，實機由T-3追蹤。Source: A-002 (批准design5adca01).

- [ ] **T-3:** security／程式acceptance已PASS，7fa932f Result Go已收到並已遠端交付；AdMob合作有效且對應／暫停群組完成；R-7實機由使用者A-003延期，不能聲稱完整供應。Source: A-002 (沿用design門檻).

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-3.

- **Last proven result:** 7fa932f完成兩項security finding修正；269 tests（203執行）無失敗、66既有略過；34既有lint Error無新增；manifest配對正確。

- **Active blocker or running process:** 程式security/acceptance PASS、source已推送；Result Go已收到，R-7實機／CMP及訊號送達由使用者A-003延期，合作關係有效且暫停群組已保存，無背景程序。

- **Next safe action:** Result Go已收到；兩個群組已保存且暫停，接續A-003-result的實機／CMP驗證；未完成前不啟用生產供應。

- **Expected changed files:** MainApplication.kt、MainActivity.kt、AdMobInitializer.kt、RewardedAdManager.kt、新AdConsentManager.kt、BannerAdView.kt、SettingsScreen.kt、manifest、app/build.gradle.kts、各語言strings、相關測試、stream notebook與本工作資料夾.

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** 實機／CMP與供應證據；Result Go已收到、後台合作有效且對應存在。

- **Operation running:** no.

- **Next action remaining:** T-3.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
