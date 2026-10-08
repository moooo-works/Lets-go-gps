# Tracker

## Identity

- **Work key:** A-036-pixel-test.

- **Active Ask:** A-038.

- **Goal:** 沿用 A-035 實機測試授權，備份替換測試版並修正地圖圖資.

- **Last update:** 2026-10-08 13:51:04 +0800.

- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.

- **Reason:** Smoke test finished; Unity supply and production CMP remain failed.

- **Total:** 7.

- **Completed:** 6.

- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 備份、替換與還原現有 App，恢復地圖圖資；檢查 DB 資料存在、實機畫面與 Maps 認證；失敗時使用原 APK／備份恢復。Source: A-036. Proof: original APK/data archive, successful install/restore/cold launch and final map screenshot.

- [x] **T-2:** 以 7fa932f 暫存測試快照驗證同意流程、Google 範例橫幅／獎勵與實機基本 map/mock UX；只在 tmp 修改測試入口，記錄與正式程式差異。Source: A-035、A-036. Proof: observed test banner, rewarded 6h and EEA sample form; production CMP failure recorded in RUN-003.

- [ ] **T-3:** 驗證 Unity adapter／單一來源測試，依目前後台群組暫停狀態如實報告限制；不開啟生產群組，不擴充到 Mintegral。Source: A-035、A-036.

- [x] **T-4:** 修正Unity組織開發者網站及官方app-ads.txt授權清單；網站repo獨立worktree，保留原AdMob，核對正式URL部署。Source: A-036. Proof: website PR#2 merged f82981b2495fe31d6290616bf857b1234785882c; GitHub Pages built; Unity dashboard says app-ads.txt up-to-date.

- [x] **T-5:** 設定Lets-go-gps正式AdMob歐洲同意表單，核對App與隱私網址及Unity伙伴；必要時保留草稿，避免影響其他App。Source: A-036. Proof: 正式App表單已發布；隔離UMP4.0.0正式AppID＋EEA測試顯示表單，拒絕後status3/canRequestAds=true/privacyOptions=REQUIRED。

- [x] **T-6:** 依明確授權送出Unity技術工單，核對成功頁／工單編號／狀態，不附手機資料或金鑰。Source: A-037. Proof: 使用者完成CAPTCHA後成功頁與工單00939092／Open。

- [x] **T-7:** 全帳戶移除Mintegral同意與資料共用設定，確認SDK／對應／waterfall均無Mintegral；營運排除已完成；依A038未使用服務的說明，合作記錄／供應商帳戶刪除不列本次必要工作，不寄終止信。Source: A-037、A-038. Proof: GDPR197／US333排除、共用停用、三App0對應、無waterfall及SDK。

## Accepted scope changes

- Change: 查核Unity已申請ID、修正網站與同意表單；Source: A-036「自行想辦法解決這些問題」；Effect: 增加T-4、T-5，必要時諮詢Claude Code。

- Change: 授權Unity工單與全帳戶Mintegral移除；Source: A-037「1，同意／2，完全移除」；Effect: 增加T-6、T-7並發布正式CMP。

- Change: 明確區分停止使用與刪除合作記錄；Source: A-038「只有註冊、尚未使用」及是否需寄信的詢問；Effect: T7營運排除完成、不發Mintegral信，供應商帳戶／合作記錄保留。

## Current recovery

- **Current item:** T-3.

- **Last proven result:** Unity00939092已送出Open；GDPR197／US333伙伴均排除Mintegral；Mintegral NPA信號與PFID關閉；正式CMP發布及拒絕回呼通過。

- **Active blocker or running process:** Unity Game ID仍待客服查核；沒有程序執行。Mintegral不寄信。

- **Next safe action:** Unity00939092有回覆後依精確修正重測，正式群組維持暫停。

- **Expected changed files:** 本repo notebook／tracker；網站repo僅app-ads.txt已發布；/private/tmp診斷APK及支援草稿／截圖；unity-ads程式與PR58保留。

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
