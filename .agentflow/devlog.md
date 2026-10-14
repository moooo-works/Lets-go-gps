# STATUS

Project: Lets-go-gps

Notebook: .agentflow/devlog.md — root.

Current commit: 產品 built31b528c；正式1.4.3/10403；公告交付紀錄見Git.

Tests/scenarios: APK apksigner與manifest金鑰摘要、Cloud同金鑰Android白名單唯讀核對；未新增實機測試.

Configuration: ag.json — schema v8; validated for codex this round.

Proven: 已核對交付debug APK真Maps金鑰與Cloud相符，其B2EF簽章未在白名單.

Open: 朋友實際APK／手機／Android／錯誤訊息待補；Maps授權未修，模擬失敗未定因；原Unity限制保留.

Next: 取得朋友裝置資訊後繼續診斷；若要補debug Maps授權須於變更時確認.

Artifacts: 本輪devlog唯讀診斷紀錄；原交付debug APK保留.

Archived eras: .agentflow/devlog.archive.md.

Streams:

stream: unity-ads — active — .agentflow/features/unity-ads/unity-ads.devlog.md
---

# → Ask / A-062 (Takeshi Mori)

+ 新版本已過審上架，我在google chrome中有登入threads帳號，你參考我之前發布的貼文語氣來發布新版本的公告文，如果可以的話附上實際截圖。

+ 不附圖

---

## [RUN-001] Event — 2026-10-14 09:50:04 +0800 (A-062)

- 已閱讀 Chrome @moooo.works 的 1.3.4、步數同步與跳點更新貼文，沿用口語開頭、使用情境、限制及下載連結；本次公告草稿493字已填入，尚未發布。

- Pixel 1.4.3 真實使用說明首頁與懸浮工具教學截圖已取得並目視確認，沒有私人地點或座標，保存於 artifacts/A-062-threads-announcement/。

- 點附加影音後開啟原生選檔，但 extension filechooser 超時並重設 runtime；重新辨識 Chrome（ID 改變）與草稿分頁。原生 Google Chrome 操作被工具拒絕核准，沒有繞過；已請 owner 選兩圖或選擇不附圖直接發布，分頁 markHandoff，等待回覆。

## [RUN-002] Event — 2026-10-14 09:53:31 +0800 (A-062)

- 依 owner「不附圖」移除附圖敘述；@moooo.works 新版公告發布成功，實際貼文 DedPxQAmuVX 全文、版本及下載連結核對完成。公告純文字，只有商店連結預覽；公開頁已 markDeliverable，發布畫面保存。

# ← Reply / A-062

* _2026-10-14 09:53:31 +0800 (codex/unknown)_

## [SUMMARY]

- 已參考既有語氣，在 @moooo.works 成功發布 1.4.3 公告與更新連結。

- 已依「不附圖」改成純文字公告，移除附圖敘述。

## [FINAL REPORT]

1. 「新版本已過審上架，我在google chrome中有登入threads帳號，你參考我之前發布的貼文語氣來發布新版本的公告文，如果可以的話附上實際截圖。」

   - 成功：[新版公告](https://www.threads.com/@moooo.works/post/DedPxQAmuVX)已發布，沿用之前口語開頭、使用情境、功能限制與下載連結的寫法。內容包含懸浮工具、獨立搖桿、Pro／廣告解鎖、FAQ、Android 17 與廣告隱私流程。

   - 我核對公開貼文全文與版本、帳號、Google Play 連結，確認不是草稿或發佈中；[公告文字](artifacts/A-062-threads-announcement/announcement.txt)與[發布成功畫面](artifacts/A-062-threads-announcement/published.jpg)已保存。此次沒有修改產品程式或宣稱新增未做過的測試。

2. 「不附圖」

   - 已完成：不附手機圖片，刪除「附圖就是新版 App 的實際畫面。」；保留 Google Play 連結預覽。先前選檔受原生視窗工具核准限制，改成純文字後已成功發布，無待操作事項。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-063 (Takeshi Mori)

+ [Image #1] 我把debug版的apk丟給我朋友使用，結果出現這個回饋，你查一下狀況

---

## [RUN-001] Event — 2026-10-14 11:54:04 +0800 (A-063)

- 已核對交付的 letsgo-1.4.1-debug-pro.apk，SHA256 5d4949f6ef0940d7dd32adcf4975800d3ecd14face23899ce76cca388106beb5，簽章 SHA1 B2:EF:2A:B6:C7:B8:54:6A:A4:1D:3D:83:D7:1C:BB:8D:95:87:EA:4F；APK 有真 Maps 金鑰且與正式 worktree 相同，非先前佔位金鑰事件。

- Google Cloud 正確 lets-go-fake-gps 專案／Maps Platform API Key 唯讀核對，金鑰摘要13496733ebf5與APK相符；Android限制四筆15ED／506F／6022／C19E，皆相同package，缺少交付debug B2EF簽章，Maps SDK for Android 已允許。確定此交付APK缺少地圖授權；仍待owner確認朋友拿到的實際檔名，無朋友裝置log，不宣稱其全部症狀已驗證。

- 模擬AppOps程式維持三態與Android17修正。卸載重裝後需重新確認mock app與定位權限；無法飛尚未定因，已詢問型號／Android／實際錯誤或遊戲位置未變。未修改任何Cloud限制／新條款／App程式／Pixel安裝；原生Chrome未連線改Edge既有登入Cloud，未接受新帳號條款。

# ← Reply / A-063

* _2026-10-14 11:54:41 +0800 (codex/unknown)_

## [SUMMARY]

- 已查到交付debug APK缺少Google Maps簽章授權；無法模擬定位尚待朋友手機資訊確認，未修復或修改後台。

## [FINAL REPORT]

1. 「[Image #1] 我把debug版的apk丟給我朋友使用，結果出現這個回饋，你查一下狀況」

   - 部分查明：交付的letsgo-1.4.1-debug-pro.apk使用Android Debug簽章B2:EF:2A:B6:C7:B8:54:6A:A4:1D:3D:83:D7:1C:BB:8D:95:87:EA:4F，真Maps金鑰與正式版本設定相同；Google Cloud同金鑰僅允許四筆其他簽章，未允許這份APK，所以新裝debug版會遭Maps授權拒絕。這是交付設定遺漏；可補登此package與debug簽章解決授權，尚未套用，也未在朋友手機驗證恢復。朋友實際APK檔名仍待確認。

   - 我直接驗APK簽章、金鑰存在及摘要，並唯讀比對Google Cloud正確專案的Android限制與Maps SDK for Android選項。沒有公開金鑰、修改安全限制、接受新條款、重新安裝Pixel或改程式。Android Maps依package及簽章授權，參見[Google文件](https://developers.google.com/maps/documentation/android-sdk/get-api-key)。

   - 「今天不能飛」仍未定因：模擬引擎不依賴地圖圖資。卸載重裝後應重新確認開發者選項的模擬位置App與App定位權限；[Android官方設定說明](https://developer.android.com/studio/debug/dev-options)。若App顯示模擬中但遊戲位置不變，需要再分辨系統位置寫入或遊戲端；沒有log不能判定。設定頁顯示「已授權」是第一個檢查點。

   - 請提供朋友收到的APK檔名、手機型號／Android版本，以及開始模擬後的錯誤文字或「App模擬中但遊戲未變」狀態，才能接續定位第二個症狀。不要先反覆解除安裝，避免丟失儲存資料。

## Questions (batched — each with a suggested default)

1. 朋友實際使用哪個APK、手機型號／Android版本，開始模擬後看到什麼？

   - Suggested default: 提供檔名及App實際訊息，以判斷是否為此次交付APK與重裝後權限問題。

   - ans:

---

# → Ask / A-064 (Takeshi Mori)

+
