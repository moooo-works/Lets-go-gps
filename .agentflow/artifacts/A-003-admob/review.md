* _2026-10-07 14:58:55 +0800 (inherited/inherited)_
Reviewed commit: bb58f9a1244f8ce6c1d32cd39ae532c15157ad27

- 本次審查通過：變更符合保留 AdMob、依容器寬度調整橫幅、限制獎勵廣告自動重試及加入本機診斷的範圍，未發現阻擋問題。

- 編譯與單元測試證據可支持程式整合，但沒有連接裝置，不能確認實機橫幅尺寸、旋轉後顯示或真實廣告載入；發布前仍需補實機 smoke test。

- 第三方競價來源仍等待國家／格式資料與帳戶核准。瀏覽器權限仍阻擋 AdMob 後台存取，這次沒有完成帳戶設定，也沒有證明收益增加。

Outcome: PASS

- 已直接檢查指定基準 fb49d2fb1456716d44d98e6d9db05f3b104f424e 到 reviewed commit 的完整四檔差異，共 197 行增刪；另閱讀該 commit 的初始化閘門、地圖橫幅位置及四個 ViewModel 的獎勵廣告呼叫。

- 橫幅以 BoxWithConstraints 的實際可用寬度取得 anchored adaptive 尺寸，依廣告高度保留空間。寬度或方向改變時重新建立廣告，仍保留 destroy() 釋放；不使用整個螢幕寬度替代容器寬度。

- 載入失敗後依序等待 5、15、30 秒，最多自動重試三次。明確呼叫 preload() 可以取消等待並開啟新一輪；正在載入或已有快取時不重複載入，成功後重設等待次數。這是每輪有上限，並非整個程序生命週期只有四次請求。

- 原有「不可用時通知呼叫端」、「關閉後預載下一支」與獎勵只發一次的流程保留。診斷只記錄載入、曝光、關閉、獎勵及錯誤代碼，未加入營收匯出、帳戶憑證或第三方競價設定。

Minimality: PASS

- 已嘗試刪除／合併新增設計：若直接讓自動重試呼叫 preload()，每次都會重設計數，失敗時無法停止，因此獨立的 loadAd() 有必要。移除 retryJob 會使手動預載無法取消既有等待；移除寬度／方向 key 則無法確保容器變動後重新請求正確尺寸。

- 沿用既有廣告元件、初始化閘門與 fake loader；注入 CoroutineScope 讓時間相關測試不必實際等待。新增三個測試各自檢查停止上限、手動取消及成功後重設，沒有加入新依賴或另造廣告抽象層。Debug 廣告單元改用 adaptive 範例，配合本次尺寸改動。

Conformance: PASS

- 審查事實：behavior_change=true、trust_boundary=false、broad_change=false、consequential_change=false、owner_control=default；採 targeted 層級，直接檢查受影響行為與呼叫端。只寫入此報告，沒有修改實作、重跑套件或讀取 Downloads/admob-report.csv。

- 重用協調者在 exact commit 的驗證證據：test SUCCESS，XML 共 251 項，其中 185 執行、66 跳過，0 failures、0 errors。實作前的新獎勵測試在基準版本跑 12 項，出現 2 個預期 assertion failures，原因為尚無自動重試／重設；這是先前對照結果，不是本 commit 的失敗。

- lintDebug SUCCESS，但 abortOnError=false；報告有 34 個錯誤，全部位於未改動檔案，兩個已改廣告 Kotlin 檔無 findings。因此不能把任務成功解讀為整個專案沒有 lint 錯誤。assembleDebug SUCCESS。

- 上述驗證使用 JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home、MAPS_API_KEY=build-validation-placeholder，沒有使用真實地圖金鑰。無連接裝置，實機 smoke test 不可執行；本報告也不認證 GitHub behind main=0 或具備合併資格。

Verdict: PASS

Self-check: 已核對指定 commit、四檔差異與受影響呼叫端；已評估刪除／合併／沿用方案，保留驗證與實機限制，僅寫入授權報告。
