# STATUS

Project: Lets-go-gps

Notebook: .agentflow/devlog.md — root.

Current commit: 產品 built31b528c；正式1.4.3/10403；公告交付紀錄見Git.

Tests/scenarios: fetch、PR58 close後state/closedAt讀取及發布分支adaptive/retry實作核對.

Configuration: ag.json — schema v8; validated for codex this round.

Proven: GitHub PR58 CLOSED；1.4.3包含後續整合的AdMob優化及Unity.

Open: main尚未合併發布整合分支，其他既有待辦保留.

Next: 等待owner新任務.

Artifacts: GitHub PR58關閉狀態.

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

+ 先處理debug版的授權缺漏

+ 確認

---

## [RUN-001] Event — 2026-10-14 11:57:21 +0800 (A-064)

- 已重新開啟正確Maps key、核對原四笔簽章與32 API不變，僅在新增表單填入com.moooo_works.letsgogps與交付Debug SHA1 B2:EF:2A:B6:C7:B8:54:6A:A4:1D:3D:83:D7:1C:BB:8D:95:87:EA:4F，尚未按完成或儲存。

- 瀏覽器Computer Use Confirmation Policy要求Creates or materially expands security-sensitive access必須action-time確認，即使此前指示處理仍需本次儲存前確認；已以精確package／fingerprint／destination／purpose詢問，沒有擅自擴大限制。表單markHandoff，prepared.jpg保存可審畫面，待owner確認後儲存並重讀白名單。

## [RUN-002] Event — 2026-10-14 11:59:08 +0800 (A-064)

- Owner確認後完成／儲存debug B2EF授權，儲存中後頁面意外about:blank；未重按Save，重新開同key後五筆授權持久存在。原四筆、Android限制及32 API維持；saved.jpg公開指紋圖目視核對無金鑰。僅修後台授權，未重建APK或測朋友實機。

# ← Reply / A-064

* _2026-10-14 11:59:08 +0800 (codex/unknown)_

## [SUMMARY]

- Debug APK 地圖授權已補上並重新讀取確認；請朋友等待最多5分鐘後完全關閉再開App，無需重裝。

- 已依確認套用精確套件與簽章，原四筆授權與限制保留。

## [FINAL REPORT]

1. 「先處理debug版的授權缺漏」

   - 成功：Google Cloud lets-go-fake-gps 的 Maps Platform API Key 已新增 com.moooo_works.letsgogps／B2:EF:2A:B6:C7:B8:54:6A:A4:1D:3D:83:D7:1C:BB:8D:95:87:EA:4F。儲存後頁面曾轉about:blank，我重新開啟同憑證，確認五筆授權存在、Android應用程式限制仍checked、原32 API及四筆簽章保留。沒有再送一次儲存或公開金鑰。[授權完成畫面](artifacts/A-064-debug-maps/saved.jpg)。

   - 這是後台修正，既有letsgo-1.4.1-debug-pro.apk可繼續用，無需build或重新安裝。Console提示最多5分鐘生效，請朋友完全關閉再開App重試地圖；尚未取得朋友實機成功回報，不能稱地圖恢復實測通過。無法飛是前輪另外待查的症狀，此次未改模擬引擎。

2. 「確認」

   - 已依action-time確認完成本次授權；我核對授權範圍與實際儲存結果，保留[待套用畫面](artifacts/A-064-debug-maps/prepared.jpg)及完成畫面。未改APK、產品程式、Play版或其他後台。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-065 (Takeshi Mori)

+ release版會有這個狀況嗎？

# ← Reply / A-065

* _2026-10-14 12:01:41 +0800 (codex/unknown)_

## [SUMMARY]

- Google Play 正式1.4.3簽章已授權，不會因這次debug簽章漏登而地圖空白；自行發送本機release APK需另核對簽章。

## [FINAL REPORT]

1. 「release版會有這個狀況嗎？」

   - Google Play正式版沒有此次debug授權缺漏：已從Pixel直接讀取Play安裝的1.4.3／10403 APK，installer=com.android.vending，SHA1=50:6F:D8:3D:F1:4D:C6:60:42:83:30:91:1A:51:C9:49:D3:2B:03:2C，與本輪前實際Cloud白名單既有506F條目相符。前次Pixel此版地圖也已目視正常。不能保證其他網路或装置問題永不發生，也不代表無法飛已修復。

   - 本機Release AAB上傳金鑰SHA1=0B:9A:DB:92:36:A0:60:08:01:51:EA:64:8B:27:91:29:C8:66:BB:18，並非Play安裝簽章，且不在當前五筆地圖白名單。若用同本機release signingConfig直接build／側載APK，仍可能遇到同類授權缺漏；這與從Play下載不同。此次未建立新APK或擴大授權，手機APK唯讀pull至tmp無私人資料。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-066 (Takeshi Mori)

+ unity ads的分支是否已經跟這次新版本同時發布？有上git了嗎？

# ← Reply / A-066

* _2026-10-14 12:04:46 +0800 (codex/unknown)_

## [SUMMARY]

- Unity Ads 已包含在正式1.4.3發布包，相關程式已push GitHub；發布整合分支尚未merge main，PR58仍保留。

## [FINAL REPORT]

1. 「unity ads的分支是否已經跟這次新版本同時發布？有上git了嗎？」

   - 是：正式1.4.3／10403以產品31b528cea7278fb54650dbfab3d1711dd2d62446建置，AAB實包驗證包含Unity SDK／Google Unity adapter／同意流程，正式banner與reward版位；Mintegral排除。此前Unity測試來源與獎勵回呼已在Pixel確認；新版本上架由owner回報，正式非測試Unity供應／收益沒有宣稱驗證。[原建置紀錄](artifacts/A-061-play-unity-test/production-build.json)。

   - 有上GitHub：本輪git fetch後，origin/fix/release-ad-integration=b9868ee5c02fc3502cffc2964d5c4738c456d0f4、origin/fix/release-ad-production=3f388df5bf1e4ba9181fcf64360c28412c9324b6，均與本地工作分支head相符。正式建置產品31b528c為production遠端祖先；origin/unity-ads亦存在。可看[正式版本分支](https://github.com/moooo-works/Lets-go-gps/tree/fix/release-ad-production)。

   - 發布與merge main不同：main的app依賴尚未包含Unity，正式產品commit並非main祖先；整合版從production分支build並上架，不是main。依先前保留要求，[PR58](https://github.com/moooo-works/Lets-go-gps/pull/58)仍OPEN draft，沒有擅自merge。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-067 (Takeshi Mori)

+ pr58是什麼

# ← Reply / A-067

* _2026-10-15 12:17:01 +0800 (codex/unknown)_

## [SUMMARY]

- PR58是既有AdMob廣告優化提案，仍為未合併草稿；其功能已隨後續整合進1.4.3發布。

## [FINAL REPORT]

1. 「pr58是什麼」

   - [PR #58](https://github.com/moooo-works/Lets-go-gps/pull/58)標題為「改善 AdMob 自適應橫幅與獎勵廣告重試」。PR是把分支修改審查後合併主分支的請求；此份由feat/admob-ad-optimization指向main。

   - 功能是依可用寬度調整橫幅廣告、獎勵廣告失敗後有限次自動重試、補充錯誤與曝光診斷；不是Unity Ads整合本身。本輪gh直接確認仍OPEN／Draft。先前依owner保留要求未合併，但相關優化後續已承接進正式1.4.3整合分支，因此PR未合併不表示正式版缺這些功能。沒有進行任何merge或PR修改。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-068 (Takeshi Mori)

+ 那這個應該可以close了吧

# ← Reply / A-068

* _2026-10-15 12:18:06 +0800 (codex/unknown)_

## [SUMMARY]

- 已關閉PR58；已發布1.4.3及原遠端分支保留。

## [FINAL REPORT]

1. 「那這個應該可以close了吧」

   - 已完成：[PR #58](https://github.com/moooo-works/Lets-go-gps/pull/58)已關閉，GitHub再次讀取state=CLOSED，closedAt=2026-10-15T04:17:53Z。沒有合併main或刪除分支，也未傳送額外留言。

   - 此PR的廣告優化後續透過fbd8a84整合進release-ad-production；原PR58 head不是發布分支祖先，不能稱原PR已merge。發布分支仍有adaptive banner與有限次retry實作，1.4.3發布包保留不受關閉PR影響。此次是依owner新指示結束先前保留的草稿，沒有改產品程式。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-069 (Takeshi Mori)

+
