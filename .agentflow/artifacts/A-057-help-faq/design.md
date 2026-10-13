# 使用說明與 FAQ

Outcome: Settings有永久入口，新增功能提示可直接看懸浮工具教學；14篇中英日韓說明可離線回看，搜尋標題和內文、分類與返回操作正常。依使用者追加的Pen桌面設計整合橘色淺深版面。

Design source: pen/help-faq.pen、pen/lets-go-gps-help-faq-four-states.png與原始pen/lets-go-gps-help-faq-spec.md。設計先完成再整合Help；示意文案不取代已核對的App字串。

## Minimality check

最小完整結果為既有Nav內一個無VM的HelpScreen、immutable資源索引／四語文章、Settings與tip的既有導航接線。只重用tip或刪除文章不能提供可搜尋、永久回看的功能。無新依賴、後端、權限、schema或購買機制。設計需要分組清單、橫滑chips、時間軸與原生圖解；搜尋高亮／摘要重用既有query，空結果只用原clear/all操作，不新增導航。四語UI標籤為閱讀清楚所需；實機測試直接驗證入口／返回及字級，不鏡射實作。Pen全稿保留作為設計來源，圖中的推測內容與跨文章導航提案不實作。

## Invariants

- INV-1: 原mock engine、Pro 6h/cap24／步數額度、VM owner、DB schema和儲存資料不變；任何此層source差異即失敗。
- INV-2: tip已讀保存與Help route戻返保留；文章返回目錄，再回原Settings或Map。冷啟動不得新增設定跳轉。
- INV-3: 四語title/body搜尋與分類同時生效，非法topic回目錄。閱讀色適配現有theme；長標題換行、內文scroll、touch至少48dp。
- INV-4: 原PR58–62與Mintegral排除不變；FAQ獨立worktree／PR，不自動合併或發版。

## Acceptance criteria

- Pixel公開UiAutomation：四語tip ack→浮窗文章→目錄→Pro搜尋與首步內容→empty→clear→FAQ→退出callback；依App真實MockGpsTheme交替淺深，日韓130%local fontScale，無phone preference mutation（INV-2/3）。
- 正式Main Settings→FAQ→搜尋Pro→文章→目錄→Settings→Map，Help隱藏底tab、返回復原，無Nav／VM crash（INV-2）。
- 圖稿／正式Light目錄視覺核對，source採主題tokens與對比橘字／深色70%主字、分組surface及時間軸；無紫色卡片（INV-3）。
- required test/lintDebug/assembleDebug及TestAPK build通過；既有FloatingLayoutTest通過；來源diff不改engine/persistence/permissions；之前同功能DB375/6/591 hash與theme/lang證據持續適用（INV-1）。
- 真Claude完整獨立clone review記錄與單主題draftPR，behind main0；未實測支付／廣告與全部merge煙測明列限制，不要求merge（INV-4）。
