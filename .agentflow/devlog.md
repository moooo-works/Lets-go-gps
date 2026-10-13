# STATUS

Project: Lets-go-gps

Notebook: .agentflow/devlog.md — root.

Current commit: 產品7d93b76未變；來源分支64ff603同步紀錄；本輪FAQ建議見Git.

Tests/scenarios: 本輪只讀Settings／Onboarding／tip flow；重用A055建置285/0fail/60skip与Pixel OK1，不新增測試.

Configuration: ag.json — schema v8; validated for codex this round.

Proven: 現有提示為首次／一次性、步數局部說明；統一可回看的FAQ／功能教學入口尚未實作.

Open: FAQ功能為建議尚未開發；原UnityGameID／merge smoke／交易及多語dark橫向限制保留.

Next: 等待使用者對App內使用說明與FAQ範圍的決定；PR62及既有PR保持草稿.

Artifacts: .agentflow/artifacts/A-055-integer-route-speed/review.md、validation.json；本輪回答在Reply A-056.

Archived eras: .agentflow/devlog.archive.md.

Streams:

stream: unity-ads — active — .agentflow/features/unity-ads/unity-ads.devlog.md
---

# → Ask / A-056 (Takeshi Mori)

+ 有沒有需要製作一份faq或功能說明，上次有人說有功能不知道怎麼用

# ← Reply / A-056

* _2026-10-13 09:39:10 +0800 (codex/unknown)_

## [SUMMARY]

- 有必要：現有教學以首次設定與一次性功能提示為主，缺少可隨時回看的完整操作說明；建議在設定頁新增使用說明與FAQ。

## [FINAL REPORT]

1. 「有沒有需要製作一份faq或功能說明，上次有人說有功能不知道怎麼用」

   - 有必要。已核對現行產品分支：首次教學主要介紹開發者選項與模擬位置App；各功能有一次性tip、步數同步有局部設定說明，但設定頁沒有統一FAQ／功能操作教學入口。新浮窗Dialog確認後保存已讀，目前沒有重開教學的入口，因此忘記內容後找不到回看方式。

   - 建議做App內「使用說明與FAQ」，固定放設定頁，可離線閱讀、沿用四語與主題。新功能Dialog保留作更新通知，內容以簡短重點並連到對應教學；使用者遇到問題時再看完整操作，不增加一連串強制彈窗。

   - 第一版分三類：快速上手（設定、搜尋／選點、開始／停止）；功能教學（收藏與資料夾、建立／匯入路線、一次／循環／折返與跳點、懸浮工具與搖桿、步數同步、Pro解鎖）；FAQ（不能定位、浮窗不出現、選點為何未移動、兩種工具為何不能同時顯示、廣告解鎖時效等）。每篇用入口位置、3–5操作步驟、預期結果，加少量對照圖；不要只寫功能名稱。

   - 優先整理最容易不懂的懸浮工具、路線模式與Pro解鎖，並用遊戲中搜尋地点→預覽→複製座標或明確移動的實際情境串接教學。說明應對應真正按鈕名稱與行為，與功能修改一起維護。

   - 這輪只檢查現況並提供建議，尚未新增FAQ檔案或App頁面，沒有改程式／安裝手機／觸發廣告付款／合併PR；原草稿與worktree保持現況。這是設計討論，不把建議自動當成新功能開發指令。

## Questions (batched — each with a suggested default)

- None.

---

# → Ask / A-057 (Takeshi Mori)

+ 好，開始製作

---

## [RUN-001] Event — 2026-10-13 09:51:43 +0800 (A-057)

- 接受A056方案並依A057製作：新worktree help-faq／feat/help-faq從最新main2cae356建立後承接floating-dock既有依賴；保留PR62，FAQ另作single-topic stacked草稿。
- UI入口／14篇四語離線教學與FAQ／搜尋分類／兩種圖解已寫；真Dialog增加查看教學并ack，無engine／schema／權限自動跳轉或付款變更。第一次compile因onNavigateHelp誤接RootApp外層失敗，已移至Settings route；原log保存private/tmp，未冒稱先前成功。
- 新四語UI instrument只切test composition locale，測tip連結／搜尋／empty／FAQ／back，不改手機prefs。required Gradle正在跑；tracker0/3，尚未驗收。[tracker](artifacts/A-057-help-faq/tracker.md)

## [RUN-002] Event — 2026-10-13 10:12:01 +0800 (A-057)

- T1/T2完成：285/0fail/60skip、四語guide UI OK1 8.976s、正式Main設定FAQ搜尋文章與返回PASS，既有浮窗回歸成功單獨記錄。測試query誤點与Locale資源bridge兩問題已診斷補正；成功後移除臨時private診斷，只重建lint／TestAPK，不重跑有效套件。
- 日韓英操作名稱對齊實際R.string；產品922bcdd已push／安裝，資料375／6／591全部rows/hash與language/theme prefs一致。所有含地點圖／原備份／診斷log只留private/tmp，未進Git。
- **Scope check:** 本次11個App差異與tracker吻合：Main/settings/map/tip四串接、Help兩檔、四語help_strings及HelpGuideTest；root僅notebook／hook archive、tracker、接受證據。沒有付款／廣告或引擎schema變更。root接受JSON首次stdin UTF8錯誤未寫檔，改ASCII-safe JSON轉送原中文成功，不重跑產品。

## [WIP-001] Checkpoint — 2026-10-13 10:12:01 +0800 (A-057)

- **Finished:**

  1. 四語14篇FAQ與教學、搜尋分類、原生圖解、設定與tip連結。

  2. required Gradle、四語UI與正式App導航、安裝與資料保留。

- **Running now:**

  1. 固定ROOT接受證據準備Claude審查。

- **Still to do:**

  1. Claude原報告與host核對。

  2. FAQ單主題草稿PR與closeout／同步main記錄。

- **Next work action:** 派固定root／product，只讀獨立clone審查，不再重跑成功套件。

- **Checks:** [x] tracker.md | [x] devlog RUN | [x] scope matches tracker
