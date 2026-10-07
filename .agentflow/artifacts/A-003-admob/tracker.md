# Tracker

## Identity

- **Work key:** A-003-admob.

- **Active Ask:** A-003.

- **Goal:** 保留 AdMob，改善廣告可用性與橫幅尺寸，讀取收益報表後接入可核准競價來源。

- **Last update:** 2026-10-07 15:00:52 +0800.

- **Evidence commit:** bb58f9a1244f8ce6c1d32cd39ae532c15157ad27.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 3.

- **Completed:** 2.

- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 在 feat/admob-ad-optimization 改善自適應橫幅、載入診斷與獎勵廣告有限次重試；限 BannerAdView.kt、RewardedAdManager.kt、RewardedAdManagerTest.kt，必要時更新測試 banner ID。先新增失敗測試，驗證自動重試、次數上限、手動預載不重複、成功後重設；跑 test、lintDebug、assembleDebug 與審查。失敗時修復本主題，環境失敗保留實際證據。Source: A-002、A-003。 Proof: bb58f9a1244f8ce6c1d32cd39ae532c15157ad27 .agentflow/artifacts/A-003-admob/review.md
- [x] **T-2:** 已嘗試瀏覽器讀取與下載，兩次儲存權限拒絕；改唯讀分析使用者提供的兩份 CSV。第二份包含國家/格式，計數及加權率已重算；CSV 與私人收益未提交 Git。日期未提供，不能推算每日收益或比較不同期間；此限制交 T-3。Source: A-003。 Proof: .agentflow/devlog.md A-003/RUN-005

- [ ] **T-3:** 根據報表及核准來源接入競價 adapter 與後台 mapping，再驗證實際供應；缺報表或來源保持未完成，平台條款在具體步驟交使用者確認。Source: A-002。

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-3.

- **Last proven result:** test、lintDebug、assembleDebug 命令成功；test 實際 185 個通過、66 skipped；lint 34 errors 位於未改檔案，廣告修改檔案無列出問題。已讀兩份使用者 CSV，第二份有國家與格式，僅日期仍缺。

- **Active blocker or running process:** T-3 尚無核准來源；Mintegral 申請頁與 AdMob 後台皆遭瀏覽器政策拒絕。日期未提供，沒有測試或 worker 在背景執行。

- **Next safe action:** 使用者先完成 Mintegral 發布者申請，核准後沿用 A-002 授權串接及驗證；現有廣告優化交付 draft PR，發布前補實機測試，補報表日期供後續 A/B 比較。

- **Expected changed files:** .agentflow/devlog.md、.agentflow/artifacts/A-003-admob/tracker.md、app/src/main/java/com/moooo_works/letsgogps/ui/components/BannerAdView.kt、app/src/main/java/com/moooo_works/letsgogps/data/billing/RewardedAdManager.kt、app/src/test/java/com/moooo_works/letsgogps/data/billing/RewardedAdManagerTest.kt、必要時 app/build.gradle.kts、.agentflow/artifacts/A-003-admob/review-facts.json、.agentflow/artifacts/A-003-admob/review.md。

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** none.

- **Operation running:** no.

- **Next action remaining:** T-3.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
