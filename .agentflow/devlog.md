# STATUS

Project: Lets-go-gps

Notebook: .agentflow/devlog.md — root.

Current commit: Unity查核與Pixel測試結果見Git history.

Tests/scenarios: Maps與DB、Google範例廣告、Unity SDK4.20.1／4.21.0初始化、app-ads.txt官方清單及Unity接受狀態.

Configuration: ag.json — schema v8; validated for codex this round.

Proven: 圖資恢復與資料保留；Unity ID已申請；網站與app-ads.txt檢查通過；正式同意草稿保存.

Open: Unity Game ID仍被拒絕；工單發送授權及Mintegral共用名單範圍回答；正式CMP發布與實機驗證.

Next: 依回答處理工單及CMP，Unity供應通過前維持正式群組暫停.

Artifacts: A-036-pixel-test/tracker.md；/private/tmp備份／截圖／工單草稿；網站PR#2；App PR#58.

Archived eras: .agentflow/devlog.archive.md.

Streams: none.
---

# → Ask / A-035 (Takeshi Mori)

+ 我接上實機了，你測試看看

+ 好了，手機是google pixel 7 pro

---

## [RUN-001] Event — 2026-10-08 12:33:12 +0800 (A-035)

- Pixel 7 Pro 尚未被 ADB 或 macOS USB 裝置清單偵測。ADB start／kill-server／start-server 後仍空白，shell 回 no devices/emulators found。wait-for-device 已隨 server 重啟結束，沒有背景測試。
- 未安裝 APK、未操作手機、未啟用中介群組。請使用者直接接 Mac、換資料線／孔、檢查 USB 用途與配件允許提示後接續。

# ← Reply / A-035

* _2026-10-08 12:33:12 +0800 (gpt-6.1-sol/low)_

## [SUMMARY]

- 實機測試尚未開始：電腦未偵測到手机，需先恢復 USB 連線。

- 已確認目標 Pixel 7 Pro；ADB 重啟後仍無裝置，macOS USB 清單亦未列出手機。

## [FINAL REPORT]

1. 「我接上實機了，你測試看看」

   - 已嘗試偵測與等待連線，但 adb devices -l 只有標頭、沒有裝置；未能安裝或測試 App。沒有修改程式、後台或開啟生產 Unity 群組。已確認 Unity worktree 有 debug APK，但该包的 debug Pro override 與 Google sample unit 不足以證明 Unity 生產版位供應，連線恢復後需使用合適測試方式。

2. 「好了，手機是google pixel 7 pro」

   - 目標已記為 Pixel 7 Pro；收到回覆後重試仍空白，adb shell 回 no devices/emulators found。ADB server 重啟一次仍相同，ioreg IOUSBHostDevice 亦沒有 USB 裝置項目。這是目前電腦連線阻塞，尚未到 App／廣告測試，不能宣稱測試通過或失敗。

   - 請直接連到這台 Mac，先避開 Hub，換可傳資料的線／連接埠；手機 USB 用途選檔案傳輸，查看 Mac 的允許配件連接及手機 USB 偵錯提示。重接後回覆「重接好了」，沿用實機測試授權繼續，不需重新批准測試。沒有背景程序。

## Questions (batched — each with a suggested default)

- None.


---

# → Ask / A-036 (Takeshi Mori)

+ 備份替換測試版

+ 你安裝新版本進去後，地圖看不到圖資

+ 所以我還要進行什麼才能啟用unity ads的功能？

+ 是不是要這個app為遊戲才能使用unity ads？

+ 你去unity確認一下有沒有申請id，自行想辦法解決這些問題，如果不行，可以跟claude code討論

---

## [RUN-001] Event — 2026-10-08 12:38:57 +0800 (A-036)

- Pixel 7 Pro 已於 A-035 收尾瞬間連線成功，先前未連線 Reply 被後續直接證據取代；沿用實機測試授權繼續。
- 現有 App 簽章 5a4ea674… 與本機 debug efb89733… 不同，先備份本機資料與原 APK；使用者批准「備份替換測試版」後移除、安裝、還原成功。
- 初始安裝的 stream debug APK 使用編譯用 Maps 佔位值；使用者回報圖資空白，已承認測試版本選擇錯誤。正在 /private/tmp 獨立測試快照重建，重用原 APK 的 Maps 設定，關閉 debug Pro override 並加入測試專用 inspector／UMP 地區入口，不修改並行 stream 原始碼。
- 啟動已取得 UMP 非 EEA 狀態，Unity 本機 public storage 可見 privacy.consent=false；這只证明本機狀態，非伺服器訊號送達或 Unity 供應。手機資料備份約1.5MB，未上傳。

## [RUN-002] Event — 2026-10-08 12:47:49 +0800 (A-036)

- 暫存測試版建置成功，重用原 APK Maps 設定後圖資恢復；已儲存點與 Google 測試橫幅可見。
- Google 範例 rewarded 播放完成，關閉顯示 Pro 解鎖剩6h；測試 EEA 表單可見，選 Do not consent 後設定頁有廣告隱私選項。這些是實際裝置畫面證據。
- 正式群組仍暫停，因此 AdInspector 單一來源列表僅AdMob。依實機測試授權建立隔離新單元 Banner8648995174、Rewarded8563391382；Banner測試群組2920871738僅含新單元及AdMob＋Unity，綁800390974／BP_Banner_Android。Rewarded群組準備中。
- 根目錄只有 notebook／tracker 記錄變更；正式程式／並行 stream 未修改，test-only snapshot在/private/tmp。範圍與tracker一致。

## [WIP-001] Checkpoint — 2026-10-08 12:47:49 +0800 (A-036)

**Finished:** 備份替換與資料還原、修正Maps測試版本、Google橫幅／獎勵6h實機測試、EEA表單可見。

**Running now:** 隔離的Unity bidding測試群組準備與單一來源驗證。

**Still to do:** Unity實際供應、CMP正式App設定與地圖mock smoke、還原測試環境與保存結果。

**Next work action:** 完成Rewarded測試群組，更新只用測試單元的APK並檢查Inspector。

- Checkpoint records current testing scope; Unity validation remains incomplete.

## [RUN-003] Event — 2026-10-08 13:01:00 +0800 (A-036)

- 正式App ID的EEA UMP回Publisher misconfiguration: no form(s) configured；先前成功的EEA表單是Google範例App設定，不能作為正式CMP通過證據。
- 隔離Rewarded群組7981624829、Banner2920871738僅含新測試單元、AdMob＋Unity，無Mintegral。清除SDK暫存設定後Unity adapter可見，SDK4.20.1以800390974初始化，但gateway回PUBLIC_ERROR_CODE_INIT_NOT_FOUND、Invalid Game ID。Unity後台相同Game ID已核對；尚無Unity實際廣告供應通過證據。
- 兩個正式群組維持暫停，兩測試群組現也暫停並保存；列表直接顯示四群組皆已暫停，截圖/private/tmp/unity-groups-paused-final.png。
- 地圖平移、儲存列表選取置中與座標更新、marker名稱泡泡無經緯度snippet、冷啟動不跳設定已驗證；mock AppOps因重裝為deny，engine開始／停止尚未測。
- DB備份前後筆數相同：375儲存點／6路線／591路線節點。清除test-only入口並還原7fa932f原始檔，保留正確Maps設定與DEV_FORCE_PRO=false，assembleDebug成功9秒。
- 最後乾淨APK安裝、資料還原與冷啟動命令均回device not found，沒有執行成功；等待使用者重接，手機暫留先前instrumented測試版，不能宣称最終清理完成。

## [RUN-004] Event — 2026-10-08 13:18:45 +0800 (A-036)

- 最終乾淨測試APK已於13:02重接後安裝、還原資料及冷啟動成功；道路地標marker正常，前次斷線紀錄由成功證據接續。
- Unity正確App／Google Play Store／Game ID800390974與兩個active版位已重新核對；專案舊營利設定URL會重導向v2，未見可修改mediation伙伴入口。
- Claude Code唯讀診斷已完成，exit0，requested claude-sonnet-5-5/high；其後端同步假設未有官方證據，host不當成事實。獨立App只跑Unity SDK4.20.1也回相同Invalid Game ID，排除必須經AdMob才能重現。
- 已保存開發者網站https://moooo-works.github.io，Unity badUrlForm消失，改顯示缺授權行。官網repo最新main ec00b7e開獨立worktree，app-ads.txt補161條Unity官方清單、保留原AdMob；格式與來源比對通過，獨立Claude review進行中。
- tracker已擴充使用者新授權，scope含網站單一app-ads檔與/tmp診斷、不動PR58／unity-ads程式。正式及測試四群組繼續暫停。

## [RUN-005] Event — 2026-10-08 13:26:16 +0800 (A-036)

- Unity app-ads.txt缺口已解決：網站單一檔案PR#2合併f82981b2495fe31d6290616bf857b1234785882c，Pages built；Unity後台顯示up-to-date／no action needed。161條官方清單保留原AdMob，Claude獨立窄範圍比對PASS，host也逐行與格式核對。
- Unity SDK4.20.1及4.21.0獨立診斷App均回Invalid Game ID；app-ads修正後4.21.0重測仍失敗。診斷App套件不同於正式App，作為輔助證據；正式同套件AdMob測試的同樣失敗仍是主要證據，不宣稱已排除所有整合因素。
- AdMob正式App同意表單草稿Lets-go-gps — European consent已保存，只有Lets-go-gps選取，既有公開隱私網址、英文、同意／拒絕／管理按鈕；尚未發布。Unity Technologies SF（GVL1549）原已選取。常用伙伴也含Mintegral（GVL867），移除會影響全帳戶；已取消暫存變更、原設定保留，向使用者詢問範圍。
- 已準備/private/tmp/unity-id-support-draft.txt，含組織／App／Game ID、SDK版本及公開錯誤，未發送，沒有私人手機ID／位置／金鑰。因對外送訊息需明確授權，已詢問能否送給Unity Ads Support。
- 診斷App已移除，Lets-go-gps正常測試版冷啟動成功；PR58仍OPEN draft，四Unity正式／測試中介群組維持暫停。Claude兩個程序均exit0，沒有背景工作。tracker核對3/5完成、T3／T5待續，scope與實際網站單檔＋本repo記錄相符。

## [WIP-002] Checkpoint — 2026-10-08 13:26:16 +0800 (A-036)

**Finished:** 備份替換與Maps恢復、資料保留、Google範例實機測試；Unity ID／版位核對、Claude診斷、app-ads.txt發布且Unity檢查通過、正式同意草稿保存。

**Running now:** 無程序；等待已提出的工單發送授權與Mintegral共用同意伙伴範圍回答。

**Still to do:** Unity後端Game ID查核與供應通過；依範圍決策發布正式CMP並實機重測。

**Next work action:** 依使用者回覆處理技術工單及CMP；保持正式群組暫停。

- **Checks:** [x] tracker.md | [x] devlog RUN | [x] scope matches tracker

# ← Reply / A-036

* _2026-10-08 13:27:24 +0800 (gpt-6.1-sol/low)_

## [SUMMARY]

- Unity已申請正確App與Game ID800390974，兩個版位active；不是缺少申請，也沒有證據指向App非遊戲。
- 開發者網站及app-ads.txt已修正，Unity後台檢查通過；SDK初始化仍回Invalid Game ID。Claude Code已完成診斷與清單比對。
- 正式同意表單草稿已保存，待Mintegral全帳戶範圍回答；Unity支援工單已備妥，待發送授權。正式群組保持暫停。

## [FINAL REPORT]

1. 「備份替換測試版」

   - 原APK及資料已備份於/private/tmp，經授權替換不同簽章版本並還原。375儲存點、6路線、591節點筆數保留。最終乾淨測試APK安裝與冷啟動成功，移除Inspector／EEA入口。Google範例banner及rewarded解鎖6h通過，範例EEA與隱私入口可見；不作為Unity正式供應通過證據。重裝mock AppOps未允許，engine未測。本輪正式App原始碼未修改，未重跑test/lint。

2. 「你安裝新版本進去後，地圖看不到圖資」

   - 初始APK使用Maps佔位設定，是我選擇測試包疏漏；保留原APK Maps配置重建後，實機道路、地標及儲存marker正常，最後乾淨版冷啟動確認。A035未連線結論被後续實機連線證據取代；A036最後短暫斷線已恢复並成功完成安裝還原。

3. 「所以我還要進行什麼才能啟用unity ads的功能？」

   - 尚需Unity初始化與實際供應通過、正式同意表單发布及重測、再啟用生產群組並交付正式版本。網站與app-ads.txt現已完成。Google bidding收益由Google支付，現有錯誤不能推定收款身分造成。[Unity官方bidding說明](https://docs.unity.com/en-us/monetization/bidding/in-app-bidding/google)。

4. 「是不是要這個app為遊戲才能使用unity ads？」

   - Unity營利說明適用game or app，並有原生Android SDK；Game ID是識別名稱，不能據此要求把工具改成遊戲。現有錯誤不足以證明類別限制。[官方說明](https://unity.com/solutions/app-monetization)。

5. 「你去unity確認一下有沒有申請id，自行想辦法解決這些問題，如果不行，可以跟claude code討論」

   - 已直接核對Unity後台正確Store ID com.moooo_works.letsgogps、App ff5e9ffd-33d6-41c6-a120-6767685d195b、Game ID800390974及BP_Banner_Android／BP_Rewarded_Android。舊專案營利設定入口重導向v2，未找到可修正的mediation伙伴開關。
   - 已保存開發者網站，獨立網站worktree新增161條Unity後台官方seller行、保留原AdMob。精確逐行、格式、diff check通過，Claude Code窄範圍比對PASS；網站PR#2已合併f82981b2495fe31d6290616bf857b1234785882c，Pages built，Unity直接顯示app-ads.txt up-to-date／no action needed。[網站變更](https://github.com/moooo-works/moooo-works.github.io/pull/2)。
   - Claude Code診斷已完成，requested claude-sonnet-5-5/high，exit0；後端同步假設只有推論，未當成已確定原因。獨立native測試SDK4.20.1／4.21.0均回Invalid Game ID；app-ads修正後4.21.0重測仍失敗。診斷套件與正式App不同，是輔助證據；同正式套件經AdMob的失敗仍是主要證據，不能宣稱排除所有整合因素。診斷App已移除、正常App冷啟動成功。
   - 正式App的英文同意草稿Lets-go-gps — European consent已保存，只有本App、公開隱私網址、同意／拒絕／管理按鈕，尚未发布。Unity原已列同意伙伴。Mintegral也在既有198家common清單，移除必須改全帳戶設定；取消暫存變更，沒有改共用清單，已詢問範圍。SDK／版位仍無Mintegral，PR58仍OPEN draft，正式／測試四群組保持暫停。
   - 技術工單草稿/private/tmp/unity-id-support-draft.txt已備妥，僅App／組織／Game ID、SDK版本及公開錯誤；未送出，未附私人手機資料或金鑰。需要明確授權才能對外發送。沒有背景程序執行。

## Questions (batched — each with a suggested default)

1. 是否授權將已備妥的技術工單發送給Unity Ads Support，請其查核Game ID後端啟用狀態？

   - Suggested default: 授權送出技術工單；未回覆不視為授權。
   - ans:

2. Mintegral要從全帳戶共用同意伙伴清單移除，或維持原清單、只在本App SDK／版位排除？

   - Suggested default: 維持既有清單，只排除本App SDK／版位；發布前等範圍回答。
   - ans:

---

# → Ask / A-037 (Takeshi Mori)

+
