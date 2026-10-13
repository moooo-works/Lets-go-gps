# Tracker

## Identity

- **Work key:** A-057-help-faq.
- **Active Ask:** A-057.
- **Goal:** 製作可回看／搜尋的四語App說明與FAQ，接設定與浮窗tip教學，安裝驗證。
- **Last update:** 2026-10-13 11:19:53 +0800.
- **Evidence commit:** c08b33d3b830c27d193dab345acfad926d8f18c9.

## Overall state

- **State:** complete.
- **Reason:** Pen四態設計與實作、Pixel接受、最後Claude四PASS與草稿PR63完成。
- **Total:** 5.
- **Completed:** 5.
- **Remaining:** 0.

## Accepted task checklist

- [x] **T-1:** 製作14篇四語離線操作說明／FAQ、搜尋與3分類、原主題／返回／圖解，設定入口與浮窗tip直接教學且保存ack。Scope: MainActivity、SettingsScreen、MapScreen、FloatingToolsTipDialog、ui/help/HelpContent.kt與HelpScreen.kt、四values*/help_strings.xml；不改VMowner／engine／schema／Pro來源。Proof: UI內容對照現有按鈕／route模式／Pro時效與steps額度；required Gradle三項及AndroidTest建置成功。失敗修原scope並保留log。Source: A-056、A-057.

- [x] **T-2:** Pixel保留資料更新并測正式Settings導航、四語tip／search／empty／FAQ／back與既有浮窗回歸。Scope: 新HelpGuideTest.kt＋公開UI操作／備份，不觸發付款廣告，不改user locale prefs。Proof: adb install -r、公開UI／instrument log與DB筆數/hash。失敗先區分裝置／測試／產品，記錄真實限制。Source: A-057與既有手機授權.

- [x] **T-3:** 固定產品與ROOT接受證據由Claude Code獨立審查，host核對後建立FAQ單主題草稿PR（base PR62），保存紀錄並push。Scope: root .agentflow紀錄與Git／PR；不合併、不改原PR58-62／不修範圍外發現。Proof: 真CLI模型／report／clone無變更、host範圍核對、tracker3/3、GHbehind0。未過保留active。Source: A-057與原Claude review選擇.

- [x] **T-4:** 依「ui有點醜」重整Help視覺：去除預設紫色大卡片、原主題中性色＋橘色圖示、分組輕列表、選取高亮與清楚圖解、Help期間隱藏底部分頁。Scope: 同HelpScreen／Main／Settings／四語helperstrings，不改其他頁或引擎。Proof: 新版Screenshot視覺核對、四語／正式回程／原浮窗必要重驗與最終Claude審查。失敗限定scope修正。Source: A-057「ui有點醜」。

- [x] **T-5:** 依「使用說明也請pen.dev設計後再一起改，也要跟主題搭配」由Pen桌面Claude Opus 5.5設計淺深目錄／文章四態，保存原稿／PNG／規格後實作並重驗。Scope: 新Pen frames與匯出、Help視覺及必要四語文案；保留原浮窗稿與App功能。Proof: 真實設計／匯出、Compose對照、主題與長文QA、必要Gradle／手機流程與最終審查。Source: A-057.

## Accepted scope changes

- 使用者追加「ui有點醜」，加入T4視覺改善。Effect: Help列表／文章／原主題色及Help底bar顯示範圍重整。Source: A-057.
- 首輪三語主地圖Start/SinglePoint名稱不符，依原操作教學正確性要求修正文案。Effect: 只對齊四語help內容，不改App功能；原report-01 bytes保留。Source: A-057及A-056已接受方案.

- 使用者追加Pen桌面協作。Effect: 新增T5，完成設計後才整合最終UI並重新驗證交付。Source: A-057.

## Current recovery

- **Current item:** none.
- **Last proven result:** 新獨立help-faq分支自最新main建立後承接floating-dock依賴；新UI與四語已寫。
- **Active blocker or running process:** none.
- **Next safe action:** none.
- **Expected changed files:** MainActivity.kt、SettingsScreen.kt、MapScreen.kt、FloatingToolsTipDialog.kt、ui/help兩檔、四語help_strings.xml、HelpGuideTest.kt；root只devlog／hook archive與本tracker／接受證據／review紀錄。

## Completion proof

- **All accepted tasks checked:** yes.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** none.
- **Evidence status:** complete.
- **Judgment:** complete.

## Update meaning

- Tracker保存實際進度與未完成事項，不代表停止。
- 完成時Evidence commit必須是實際Git接受證據；各checkbox另需真實測試與report。
- 未完成項繼續執行，除非有獨立停止條件。
