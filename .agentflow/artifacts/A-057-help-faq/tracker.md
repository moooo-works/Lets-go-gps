# Tracker

## Identity

- **Work key:** A-057-help-faq.
- **Active Ask:** A-057.
- **Goal:** 製作可回看／搜尋的四語App說明與FAQ，接設定與浮窗tip教學，安裝驗證。
- **Last update:** 2026-10-13 10:12:01 +0800.
- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.
- **Reason:** 功能、四語測試、正式導航與資料已通過，審查與交付待完成。
- **Total:** 3.
- **Completed:** 2.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 製作14篇四語離線操作說明／FAQ、搜尋與3分類、原主題／返回／圖解，設定入口與浮窗tip直接教學且保存ack。Scope: MainActivity、SettingsScreen、MapScreen、FloatingToolsTipDialog、ui/help/HelpContent.kt與HelpScreen.kt、四values*/help_strings.xml；不改VMowner／engine／schema／Pro來源。Proof: UI內容對照現有按鈕／route模式／Pro時效與steps額度；required Gradle三項及AndroidTest建置成功。失敗修原scope並保留log。Source: A-056、A-057.

- [x] **T-2:** Pixel保留資料更新并測正式Settings導航、四語tip／search／empty／FAQ／back與既有浮窗回歸。Scope: 新HelpGuideTest.kt＋公開UI操作／備份，不觸發付款廣告，不改user locale prefs。Proof: adb install -r、公開UI／instrument log與DB筆數/hash。失敗先區分裝置／測試／產品，記錄真實限制。Source: A-057與既有手機授權.

- [ ] **T-3:** 固定產品與ROOT接受證據由Claude Code獨立審查，host核對後建立FAQ單主題草稿PR（base PR62），保存紀錄並push。Scope: root .agentflow紀錄與Git／PR；不合併、不改原PR58-62／不修範圍外發現。Proof: 真CLI模型／report／clone無變更、host範圍核對、tracker3/3、GHbehind0。未過保留active。Source: A-057與原Claude review選擇.

## Accepted scope changes

- None.

## Current recovery

- **Current item:** T-3.
- **Last proven result:** 新獨立help-faq分支自最新main建立後承接floating-dock依賴；新UI與四語已寫。
- **Active blocker or running process:** none; 固定審查前接受證據已齊。
- **Next safe action:** 固定ROOT與產品派Claude審查。
- **Expected changed files:** MainActivity.kt、SettingsScreen.kt、MapScreen.kt、FloatingToolsTipDialog.kt、ui/help兩檔、四語help_strings.xml、HelpGuideTest.kt；root只devlog／hook archive與本tracker／接受證據／review紀錄。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-3.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- Tracker保存實際進度與未完成事項，不代表停止。
- 完成時Evidence commit必須是實際Git接受證據；各checkbox另需真實測試與report。
- 未完成項繼續執行，除非有獨立停止條件。
