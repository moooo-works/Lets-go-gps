* _2026-10-12 19:57:56 +0800 (gpt-6.1-sol/high)_
Reviewed commit: 70c1ec78f404949557403b1ff458a22b95185df9
Product implementation commit: 85ca56486538913ff64cf17e86d75eace4173579

- **審查通過。** 固定產品符合使用者最後選擇：懸浮工具與搖桿各有入口、都沿用 Pro 解鎖，同時只保留一種視窗；收合會回到邊緣，沒有發現必須修正的阻擋問題。

- **證據有範圍。** 最新公開實機測試支持鍵盤、視窗縮小、模式文字與兩種面板關閉；最新跨 App 逐鍵重測未完成，不能宣稱所有 Pikmin 按鍵、正式交易或其他 Android 版本均通過。

- **下一步仍由主持者處理。** 此結果是固定版本的產品審查；產品尚未合併 main，PR 分支對齊與提交流程不在本次只讀審查中代辦。

Outcome: PASS
Minimality: PASS
Conformance: PASS
Verdict: PASS

## 固定輸入與需求重建

- 審查開始時，根目錄 HEAD 是指定的紀錄 commit；產品 worktree HEAD 是指定的實作 commit，產品工作目錄乾淨。全部產品檢查以 PR61 base `2d8e3ebdbb6aded2ac4ebdf5e0d85d604a4cf5dd` 到固定產品的差異為準，沒有把 root main 的舊 App 當成產品。

- 從固定 root 的 [原始 Ask](/Users/mori/side-project/Lets-go-gps/.agentflow/devlog.md:312) 重建要求：Pen 協作重新配置、關閉／收合回邊緣、修復靠邊後底層按鍵失效、實作完成的設計、對齊原稿、背景半透明、路線箭頭可辨識、兩個 Pro 入口。最後「一次顯示一種，開另一種時切換」取代早期同時顯示假設；歷史提案不是額外需求。

- 直接讀固定版本的 design、device-acceptance、data-preservation、review-facts、review-plan、Pen JSON 及兩張 exports，並看過兩張圖。Pen／exports 現行 bytes 與固定 root 相同；凍結 `source.diff` 與產品 base..implementation 的 `-- app` diff 逐 byte 相同，皆 114,152 bytes，SHA-256 `b4c74cd5c0bf4590d09224f87e425803efceec15323fef8559e598cecc6a3e54`。19 個 App 檔案、888 行新增／335 行刪除，與 facts 相符。

## 使用者結果與 UI

- **Pen 配置已對應採用項目。** [FloatingCompanionView.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/main/java/com/moooo_works/letsgogps/ui/map/FloatingCompanionView.kt:82) 有 48dp 收合圖示、358dp 上限面板、22dp 圓角、6dp 間距、48dp 工具列／搜尋、56dp 五等分入口、更多選單與獨立完全關閉。結果直接複製不選點；選點先進小地圖預覽，再按 52dp 主按鈕才定位（103、148、294 行）。真實地圖 220dp，左右速度／搖桿控制及滿寬停止保留在獨立搖桿。

- 原 Pen 有示意距離、ETA、不同預設速度、探索開關、完全關閉後停止定位與二次確認；固定 [design.md](/Users/mori/side-project/Lets-go-gps/.agentflow/artifacts/A-051-floating-layout/design.md:37) 明列不採用這些新增行為，保留既有完整 Stop／只關浮窗語義。後來透明背景與拆開搖桿也覆蓋原稿；目前控制頁的「開啟獨立搖桿」符合最新選擇。此 PASS 不表示原提案每個示意與橫向／大字細節都逐像素還原。

- **靠邊與觸控。** [JoystickOverlayManager.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/main/java/com/moooo_works/letsgogps/ui/map/JoystickOverlayManager.kt:85) 首次量測吸附，後續寬高改變重算同側位置；拖曳解除吸附，結束再吸附（163、178、200 行）。視窗維持 WRAP_CONTENT 與 FLAG_NOT_TOUCH_MODAL；沒有把 WindowManager alpha 降低或放寬安全 flags。可見浮窗內由浮窗接收操作，修復目標是浮窗外的底層區域。

- **鍵盤與透明區。** View 的 67–71、99–106、253 行只在輸入聚焦時加鍵盤 padding；收合／換頁先釋放輸入，鍵盤出現時暫藏五入口。最新 [FloatingLayoutTest.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/androidTest/java/com/moooo_works/letsgogps/FloatingLayoutTest.kt:71) 檢查的是實際 AccessibilityWindowInfo bounds，不只是圖示大小；111 行起確認真實 IME 存在且輸入欄底部在鍵盤上緣以上，工具與搖桿收合後實際窗寬高都 ≤64dp。此 UI 測試用示意資料與 callback，並非完整主 App 的付費／定位端到端測試。

- **半透明、對比與四語。** 工具與獨立搖桿展開 82%、收合 90%；搜尋、主要操作維持不透明色，未選導航背景透明。[FloatingOverlayTheme.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/main/java/com/moooo_works/letsgogps/ui/map/FloatingOverlayTheme.kt:23) 局部修色，沿用主題／語言監聽，不寫回設定。已通過的 FloatingOverlayColorsTest:19–40 以深／淺色、黑／白底層合成背景驗證主要／次要／accent／error 文字 ≥4.5；不是任意遊戲畫面的人工可讀性保證。繁中、英、日、韓 strings 均有新短標籤、入口、關閉、速度、Pro 工具說明（values:488，其他三語:485 起），未缺新 key。

- **路線辨識。** View:187–201 使用「一次／循環／折返」既有四語文字，對應 ArrowForward／Repeat／SyncAlt，並有 stateDescription；仍接原 cycleLoopMode，沒有變更播放引擎。最新公開測試逐次確認可見文字並回到一次；先前無障礙快取造成的失敗沒有冒充成功。

## Pro、互斥切換與清理

- [MapOverlays.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/main/java/com/moooo_works/letsgogps/ui/map/MapOverlays.kt:188) 的 NearMe 與 ControlCamera 為兩個獨立 FAB，名稱與啟用狀態各自對應。MapScreen:327 接上工具入口；MapState:63 新增工具旗標。實機接受紀錄另有主 App 兩入口名稱／bounds，沒有把示意測試當成主 App 入口證據。

- [MapViewModel.kt](/Users/mori/side-project/Lets-go-gps/.worktrees/floating-dock/app/src/main/java/com/moooo_works/letsgogps/ui/map/MapViewModel.kt:731) 每次新開先同時核對 UI 及 repository 當下 Pro，再檢查 overlay permission；免費或 UI 尚未收到撤銷時，回既有升級對話框且不開窗。通過的 MapViewModelTest:145、159 明確涵蓋這兩條拒絕路徑。

- 沿用 ProRepositoryImpl:67–84 的「訂閱或廣告未過期」來源，廣告時效仍採既有每分鐘更新節奏；本次沒有另創權限時鐘。VM:801–813 只在原 SDK onReward 後 grantAdUnlockHours(6)，ProUpgradeDialog:68–74 同列搖桿／工具；RewardedAdManager:66–71 原防重複 reward 邏輯未改。付費商品、價格、reward、步數錢包與 Billing SDK 均不在 diff。

- **最多一種。** VM:738／749 在 show 新模式前關舊模式；工具關閉走 261–267，搖桿關閉走 JoystickController:70–74。Manager:55–56 本身只容許一個 ComposeView。VM 測試:175–195 驗證工具→搖桿→工具、兩 flags 互斥及撤銷後關窗；測試以呼叫次數作斷言，先 hide 再 show 的順序另外由固定程式直接確認。

- **撤銷／待辦／移動工作。** VM:421–428 收到 Pro false 即關目前模式；工具關閉及系統 dismiss 都取消 companionJob、使 request 失效、清待辦 dialog，Controller.close:239–244 取消晚回搜尋／路線載入與替換確認。Manager.hide:141–159 清 pendingInputAction／focus、關鍵盤、dispose composition、removeViewImmediate 及 destroy owner；JoystickController:31–34、101–105 清 tickerJob 和移動向量。搖桿內容 dispose 亦歸零，收合先 releaseMovement；VM:1008–1012 disposal 清全部控制器。關閉／撤銷不呼叫停止定位服務，保留既有服務語義；已通過 controller 晚回結果、替換撤銷與 Manager add／update 失敗清理測試支持此邊界。

## 最小性與範圍

- **獨立較小設計檢查：共用原 Manager。** 我考察以單一既有視窗輪換內容是否足夠；最終已採此方案，移除原 overlayWrapper，沒有第二 Manager、service、owner、權限或付費機制。只新增工具 enabled 旗標，與既有搖桿旗標分辨 FAB／清理／ticker 所屬；改成全新 enum 會擴大既有控制器改動，沒有帶來滿足 Ask 所需的更小方案。

- **刪除吸附狀態的反證。** 我另外沿程式推演刪掉 dockedRight、只在收合瞬間 snap：螢幕 1080px、展開寬 840px 時 x=240；收合寬 126px 後一般 clamp 仍留 x=240，正確右邊應是 x=954。因此保留量測後同側重算必要；既有紅測／最終綠測亦覆蓋重新開啟、縮小、長大、左右側與拖曳解除吸附。不採固定延遲或新增動畫視窗。

- 每個新增概念都有當輪目的：bottomAligned 保留控制配置、聚焦時的 padding 修復透明觸控區、onCopyLocation 提供設計稿的直接複製、StandaloneJoystickOverlay／工具旗標實現獨立互斥入口、palette 和四語支援半透明可讀性／名稱辨識。19 檔分別是 10 個 map 主程式、ProUpgradeDialog、4 語資源與 4 個必要測試檔；沒有 engine／schema／repository／Navigation／依賴變更，沒有相鄰重構或未授權付費行為。未找到仍足以完成 Ask、卻被多餘新增設計取代的較小方案。

## 既有驗證、資料與限制

- 只讀核對 `/private/tmp/floating-exclusive-pro-final.log`：test、lintDebug、assembleDebug、assembleDebugAndroidTest 均列出，BUILD SUCCESSFUL in 51s。產品 build/test-results XML 合計 284 tests、0 failure、0 error、60 skipped，即 224 個實際執行；MapViewModel 16、Manager 4、CompanionController 11、ProRepository 16、對比 1 均無 skip／失敗。最新補強 window bounds 的 AndroidTest build 另 SUCCESSFUL in 3s，產品主程式未再改。

- `/private/tmp/floating-window-bounds-instrument.log` 為 FloatingLayoutTest、7.005 秒、OK (1 test)，與固定 200 行測試來源相符。lint XML 實際仍有 34 Error／97 Warning／3 Hint；專案原設定 abortOnError=false，所以任務成功不代表 lint 零問題。接受紀錄已揭露既有問題，不能將它們算成此次全數修復。

- 固定 data-preservation.json 的 mockgps.db 全部表 rows／hash 前後一致：收藏 375、路線 6、路線點 591。Google 傳輸事件庫有差異；語言仍 zh-TW，但主題 DARK→LIGHT 的來源不明，目前 LIGHT 被保留。本次產品差異未寫主題設定，證據不足以歸因，也不足以宣稱全設定保留；報告不暴露私密地點內容。

- 最新跨 App 逐鍵重測因手機仍被使用未完成。此前 Calculator 同側 3→3−、左右邊緣接受紀錄，搭配最終相同 Manager／focus 路徑與實際小窗 bounds，足以支持已定位的透明大窗修復；不延伸宣稱所有遊戲按鍵通過。正式付款、正式獎勵廣告、release／Play 發布、其他 Android 版本與所有橫向／大字視覺組合未驗收。這些限制沒有在固定差異中呈現已重現且未修的 in-scope failure，因此不作擴大範圍的阻擋要求。

Self-check: 已 dual pin 固定 root 與產品，逐 byte 核對 patch 並從原 Ask／最新選擇重建需求；僅寫允許的 review.md，未變更產品或紀錄、未執行 Agentflow／delegation／network／adb、未重跑既有成功套件或付款／廣告；已保留資料、主題、跨 App 與版本限制，Verdict 為本人獨立判定。
