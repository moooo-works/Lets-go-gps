# STATUS

Project: Lets-go-gps

Notebook: .agentflow/devlog.md — root.

Current commit: 本輪諮詢紀錄見 Git history.

Tests/scenarios: 官方文件與廣告程式唯讀檢查；未執行產品測試.

Configuration: ag.json — schema v8; validated for codex this round.

Proven: 提供 AdMob 中介與現有廣告優化建議.

Open: 尚無收益報表，無法排序來源或估計增幅.

Next: 等待使用者提供近 28 天廣告數據.

Artifacts: none.

Archived eras: none.

Streams: none.
---

# → Ask / A-001 (Takeshi Mori)

+ 現在這個專案要使用admob的中介服務，但是之前申請applovin說已經不再開放新戶，那接下來要怎麼提升廣告效益

---

## [RUN-001] Event — 2026-10-07 14:22:39 +0800 (A-001)

- 查核 Google 最新中介與 A/B 文件，檢查現有橫幅及獎勵廣告程式；採直接諮詢路線，未修改產品。

# ← Reply / A-001

* _2026-10-07 14:22:39 +0800 (gpt-6.1-sol/low)_

## [SUMMARY]

- 建議保留 AdMob，先測試其他 bidding 廣告來源，再改善現有廣告呈現與載入；AppLovin 無法開戶不會阻止這條路。

## [FINAL REPORT]

1. 「現在這個專案要使用admob的中介服務，但是之前申請applovin說已經不再開放新戶，那接下來要怎麼提升廣告效益」

   - **建議：保留 AdMob 中介，從一個可核准的 bidding 來源開始測試。** AppLovin 是其中一個廣告來源，沒有它仍可做中介。未找到官方公開公告足以證實「全面停止新戶」；以你收到的拒絕通知為目前帳戶限制，不必等待它。
   - **可申請的候選：Unity Ads、Mintegral、Pangle。** Google 的新一代 SDK 中介清單列有這些來源，但清單不代表保證開戶、支援所有市場或一定提高收益。先確認是否接受你的 GPS 工具類 App、主要用戶國家與所需廣告格式；Pangle 要額外確認區域來源配置。[Google 支援清單](https://developers.google.com/admob/android/next-gen/mediation/choose-networks)、[bidding 合作設定](https://support.google.com/admob/answer/9842838)。
   - **先查後台能直接申請的免第三方 SDK bidding 來源。** Google 清單也列有這類來源，可降低接入成本；仍需合作核准與格式支援，不是勾選就一定開始供應廣告。是否先採用，取決於你的後台可用選項。
   - **專案現況：**使用 Next-Gen SDK 1.2.1，目前 Gradle 未看到第三方 mediation adapter；橫幅採固定 AdSize.BANNER 與 50dp 容器，載入失敗回呼沒有記錄；獎勵廣告已有預載與關閉後再載入，但失敗後未看到自動退避重試。程式檢查不能確認 AdMob 後台是否已配置免 SDK 來源。
   - **程式改善順序：**先補載入錯誤、展示與獎勵完成的統計，再測試自適應橫幅並同步調整容器高度；獎勵廣告評估有限次退避重試與可用狀態提示。這些是待測的改善方向，尚未修改程式，也不能保證收益增幅。接第三方 adapter 時要依 Next-Gen 文件核對版本，並驗證初始化與實際供應。[中介接入文件](https://developers.google.com/admob/android/next-gen/mediation)。
   - **驗證收益：**先整理最近 28 天、按國家及廣告格式拆分的收益、請求數、match rate、show rate、eCPM；新增一個來源後做 A/B 比較。主指標用每位每日活躍用戶廣告收益（ARPDAU），並看留存、載入等待與訂閱轉換，避免單看 eCPM。Google 建議一次改一個設定、至少測兩週，且需至少 10,000 次廣告請求才可判定結果；這是最低要求，不保證足夠統計把握。[官方 A/B 測試說明](https://support.google.com/admob/answer/9572326?hl=en-GB)。
   - **不要先大幅拉高底價或增加強制插頁。**例如原本 10,000 次曝光、eCPM $1，收益 $10；提高底價後只剩 4,000 次曝光、eCPM $2，收益反而 $8。GPS 工具操作頻繁，建議先改善現有橫幅與使用者主動觀看的獎勵廣告，再評估新的打斷型版位。
   - **下一步需要的資料：**近 28 天 DAU、前三大用戶國家及比例、橫幅與獎勵廣告各自的收益／請求數／match rate／show rate／eCPM；可貼數值即可。有這些資料才能判斷應優先補供應、改善展示，或提升廣告需求競爭。
   - 本輪完成官方資料查核與本地程式唯讀檢查；未改 App、未操作廣告後台，未跑 Gradle 驗證，因本輪是諮詢。Git fetch 成功，close 前檢查只見既有未追蹤工作流程檔案。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-002 (Takeshi Mori)

+
