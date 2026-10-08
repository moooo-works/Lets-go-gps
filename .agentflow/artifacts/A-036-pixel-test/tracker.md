# Tracker

## Identity

- **Work key:** A-036-pixel-test.

- **Active Ask:** A-036.

- **Goal:** 沿用 A-035 實機測試授權，備份替換測試版並修正地圖圖資.

- **Last update:** 2026-10-08 13:27:23 +0800.

- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.

- **Reason:** Smoke test finished; Unity supply and production CMP remain failed.

- **Total:** 5.

- **Completed:** 3.

- **Remaining:** 2.

## Accepted task checklist

- [x] **T-1:** 備份、替換與還原現有 App，恢復地圖圖資；檢查 DB 資料存在、實機畫面與 Maps 認證；失敗時使用原 APK／備份恢復。Source: A-036. Proof: original APK/data archive, successful install/restore/cold launch and final map screenshot.

- [x] **T-2:** 以 7fa932f 暫存測試快照驗證同意流程、Google 範例橫幅／獎勵與實機基本 map/mock UX；只在 tmp 修改測試入口，記錄與正式程式差異。Source: A-035、A-036. Proof: observed test banner, rewarded 6h and EEA sample form; production CMP failure recorded in RUN-003.

- [ ] **T-3:** 驗證 Unity adapter／單一來源測試，依目前後台群組暫停狀態如實報告限制；不開啟生產群組，不擴充到 Mintegral。Source: A-035、A-036.

- [x] **T-4:** 修正Unity組織開發者網站及官方app-ads.txt授權清單；網站repo獨立worktree，保留原AdMob，核對正式URL部署。Source: A-036. Proof: website PR#2 merged f82981b2495fe31d6290616bf857b1234785882c; GitHub Pages built; Unity dashboard says app-ads.txt up-to-date.

- [ ] **T-5:** 設定Lets-go-gps正式AdMob歐洲同意表單，核對App與隱私網址及Unity伙伴；必要時保留草稿，避免影響其他App。Source: A-036.

## Accepted scope changes

- Change: 查核Unity已申請ID、修正網站與同意表單；Source: A-036「自行想辦法解決這些問題」；Effect: 增加T-4、T-5，必要時諮詢Claude Code。

## Current recovery

- **Current item:** T-3.

- **Last proven result:** Maps恢復／資料保留，Unity網站與app-ads.txt檢查通過；SDK4.20.1／4.21.0獨立初始化皆失敗，正式同意表單草稿保存。

- **Active blocker or running process:** Unity Game ID800390974仍被gateway拒絕；待Unity支援發送授權及AdMob全帳戶Mintegral同意名單範圍回答。沒有程序執行。

- **Next safe action:** 回覆後送出已準備的技術工單，或依Mintegral範圍決策發布已保存的App同意草稿。

- **Expected changed files:** 本repo notebook／tracker；網站repo僅app-ads.txt已發布；/private/tmp診斷APK及支援草稿／截圖；unity-ads程式與PR58保留。

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** none.

- **Operation running:** no.

- **Next action remaining:** T-3、T-5.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
