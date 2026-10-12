# 懸浮外觀與語系獨立審查

- Timestamp: 2026-10-12 10:41:01 +0800（Asia/Taipei）。

- Reviewer: native worker `/root/floating_appearance_review`；同一 provider、共享 filesystem，沒有 OS 強制唯讀隔離。

- Model-effort: `gpt-6.1-sol / high`；host 已確認實際 spawn 選值，`fork_turns:none`。

- Reviewed commit: `eb12d49e7d18edccd81fbcb62032c4680da265c5`（root main 的接受證據 snapshot；root app **尚未包含產品修正**）。

- Product implementation commit: `5c9f34f39549b959471a80ecf1af797348d4febc`；worktree `.worktrees/floating-appearance`，base `ba2541f`（未合併 PR59）。兩個目標共同構成本次審查，不能只引用 root commit 冒稱產品已進 main。

**摘要**

- 主題與四語同步修正成立，正常畫面的文字、泡泡外框與搖桿辨識度明顯改善；root 凍結資料與產品 patch 一致。

- **仍需修正一項：**深色模式的授權／額度提示文字對比只有 3.36，未達設計要求 4.5。Verdict 為 BLOCKING，先做局部色彩修正與相應驗證。

- 沒有發現需改引擎、權限、資料庫或主程式主題的問題。既有成功測試可重用；本次沒有操作裝置、網路或重跑 Gradle。

## Outcome: BLOCKING

### F1 — BLOCKING / 中：深色模式的狀態提示仍然低對比

- **觸發：**浮窗展開且 `showStepSyncCreditDialog`、`showHealthCheck`、`showProUpgrade` 或 `mockError` 任一成立。這是目前就會顯示的提示，不需要新增行為或改權限判斷。

- **影響：**使用者較難讀到「需要在主程式完成授權或額度處理，定位尚未啟動」。這段一般文字沒有停用，不適用停用按鈕的對比豁免。

- **證據：**[FloatingCompanionView.kt](../../../.worktrees/floating-appearance/app/src/main/java/com/moooo_works/letsgogps/ui/map/FloatingCompanionView.kt) 第 82–84 行以 `colorScheme.error` 顯示 `floating_blocked`。產品 wrapper 沿用主題 `error = Red500 = #DC2626`，深色 surface 改為 `#18212C`，標準相對亮度公式 `(較亮 + 0.05)/(較暗 + 0.05)` 算出 **3.36365**，低於 design AC1 的 4.5。淺色同組為 4.59049；停止按鈕的 `onError/error` 為淺色 4.82940、深色 4.62117，沒有相同失敗。

- **測試缺口：**`FloatingOverlayColorsTest` 實際 PASS，但只檢查 onSurface、onSurfaceVariant、primary、selected、onPrimary 與 outline；未檢查 `error/surface`，且以 Material3 預設 scheme 為 base，未揭露本 App 的 Red500。正常畫面截图也未顯示上述提示，不能覆蓋此條件。

- **最小處理：**只在浮窗內為該提示採用足夠對比的文字顏色，或局部調整深色 error 並同時保持 `onError/error >= 4.5`。補一項真實使用色組的對比檢查；不用改 main theme、錯誤分流或 engine。修正後只重驗受影響項；若產品 source 變更，更新產品 pin 與精確 patch 後另做修正審查，保留本首次報告。

### 已成立的行為與證據

- **解除原原因：**Manager 在唯一 ComposeView 外包 `FloatingOverlayTheme(context)`；MapViewModel 已移除內層預設 SYSTEM 的 `MockGpsTheme`。ApplicationContext 仍負責視窗，但 Compose 內容取得依 App 偏好建立的 localized Context；不靠 Activity 的 locale，也不重建視窗。

- **目前 Compose 可更新字串：**本機 Compose UI 1.11.4 的 bytecode 顯示 `LocalResources` 是 computed default：讀 `LocalConfiguration` 取得更新依據，再取 `LocalContext.resources`；`stringResource` 讀 `LocalResources`。因此同時提供 localized Context 與 Configuration 已足夠，不需增加第三個 provider。四語 AppUID instrumentation `OK (1 test)` 與 Ja→Ko→繁中→En 的持續開窗實測互相支持。

- **偏好監聽與競態：**`DisposableEffect(prefs)` 持有 listener，只在 theme/language key 變更時更新 revision；註冊後額外加一次 revision，讓首次讀取與 listener 註冊之間的變更也會再讀。SharedPreferences 通知與 composition 在主執行緒，不見新跨執行緒寫入競態。`onDispose` 解除 listener；Manager hide 明確 `disposeComposition()`，失敗／detach 路徑也會 hide。沒有額外 listener owner 或 window。

- **選項與隔離：**LIGHT/DARK 明確指定，不受系統相反設定覆蓋；SYSTEM 使用目前 Configuration 的 uiMode。缺失或無效 theme 值回 SYSTEM；空白 language 保留 base locale。`Configuration(base)` 複製再 setLocales，不修改 Application Resources。既有設定四語為 zh-TW/en/ja/ko，wrapper 使用 language tag；instrument 另驗 zh-Hant/en/ja/ko 的既有 title 與 base 不變。SYSTEM 分支已完成程式檢查；沒有把未直接截圖的系統切換情境當成實機 PASS。

- **局部配色：**`base.copy` 在 Manager 內容下的 MaterialTheme 內生效，主 App 的 Theme.kt 未改；joystick lambda 在同一 provider 下執行，所以圈、齒輪、停止按鈕與握柄繼承此 scheme。已驗測色組 >=4.5、outline/surface >=3；額外計算 outline/surfaceVariant 淺色 3.9536、深色 5.7318，齒輪文字/surfaceVariant 為 6.1804、8.7299。F1 是目前唯一確認未達標的可見文字組。

- **照片與操作接受：**六張安全圖片已逐一看過：修前主 App Ja/LIGHT、泡泡繁中深色；修後 Ja/LIGHT 泡泡暖白黑字與橘框，Ja map panel 清楚，Ko／繁中 DARK 泡泡白字，En DARK Control 新搖桿圈與齒輪可見。IME bounds `[261,1132,1059,1307]` 在鍵盤 `[0,1328,1080,2340]` 上方、診斷 PASS；close／服務空由 host device-acceptance 紀錄支持，本 reviewer 未另操作裝置。

- **資料保留：**data-preservation 的 before/after 各表 count 與 rows SHA256 全部相同，包括 saved_locations 375、routes 6、route_points 591；原 English/DARK 設定前後相同。此結論是凍結查核資料的接受，不宣稱 reviewer 重讀裝置資料庫或 private backup。

## Minimality: PASS

- **嘗試刪除 wrapper，只沿用現有 Theme：**不充分。Manager 使用 ApplicationContext，既有 Theme 不監聽偏好；保留 MapVM 的預設 wrapper 又會覆蓋強制 LIGHT/DARK，原語系原因仍在。

- **嘗試刪除局部 palette，只加外框：**不充分。現有 App 的淺色 primary/surface 對比約 3.16、深色 secondary/surface 約 3.87，低於接受要求；只加邊框不能修文字辨識度。現有字串、偏好檔與 Theme/typography 已沿用，不新增翻譯、Repository 或 dependency。

- **嘗試結合／搬移至原 View 或 ViewModel：**現設計已用一個 wrapper 同時處理 locale 與 theme；搬到 ViewModel 會增加 UI context 與 listener 生命周期責任，搬進 companion View 會讓獨立 joystick fallback 漏掉設定。沒有找到能刪減結構且仍滿足即時四語、三種主題、局部對比及釋放要求的更小設計。

- F1 可在現有局部 palette／單段文字內解決，不需要擴張架構。沒有足夠的更小替代方案，所以 Minimality 不列 BLOCKING。

## Conformance: PASS

- 已讀 root devlog A050 Ask/RUN/WIP 完整原文、design、device-acceptance、review-facts、data-preservation、tracker、完整 source.diff 與六張安全圖片；實際產品七路徑及必要 Manager caller、Theme、設定 locale 路徑已核對。沒有讀 private tar/APK。

- Root HEAD 為指定 `eb12d49...`，本次凍結文字／圖片均與該 commit bytes 一致；產品 worktree HEAD 為 `5c9f34f...`，七路徑無相對 pin 的內容差異。`source.diff` 與 `git diff ba2541f 5c9f34f...` **byte-identical**，共同 SHA256：`3cbd3c7875d845690f02da712d5f5ff27d4b2585c14807fdb22a99f9f080193d`。範圍為七 paths、152 additions／16 deletions，符合 facts 的 168 changed lines。

- Targeted planner facts 正確：behavior=true、trust_boundary=false、broad=false、consequential=false。修改僅外觀、locale context 與相關 tests；沒有 engine、permission、schema、ads 變更，也沒有新增 ViewModel owner 或改 navigation result。

- 重用 `/private/tmp/appearance-final-build.log` 的 `test`／`lintDebug`／`assembleDebug` BUILD SUCCESSFUL，以及 XML 的 280 tests／0 failures-or-errors／60 skipped，即 **220 實際執行**；新 ColorsTest 為 1 executed／0skip／PASS。Instrumentation log 為 OverlayLocaleTest `OK (1 test)`。Lint XML 為 34 Error／95 Warning／3 Hint，不能說 clean lint；60 skip 不是 PASS。

- Root 的 STATUS 尚保留前一輪摘要，A050 的 Ask/RUN/WIP 與 target artifacts 清楚區分最新產品、接受資料與待 review/PR/closeout，未把 main 當成已部署修正。最終交付仍須由 host 更新摘要及 PR 必填資訊；本報告不批准合併，也沒有重新確認遠端 behind 狀態。

## Verdict: BLOCKING

- 唯一 blocker：F1 的深色狀態文字對比低於已接受門檻。其餘已查核主題、四語、監聽釋放、Configuration 隔離、局部 palette、搖桿繼承與資料保留成立。

- 限制：Pikmin Bloom 本身、older Android、release／付費流程未實測，維持未測；圖片不證明所有第三方背景都能保持同一對比。Root 接受紀錄可如實完成收尾，但不能把此產品版本的 AC1 寫成全數 PASS。

Self-check:

- [x] 同時固定並查核 root evidence 與未合併 product commit；透明區分 main 無產品修正。

- [x] Outcome、Minimality、Conformance、Verdict 明確；F1 有觸發、影響、計算證據及最小處理。

- [x] 嘗試刪除、結合與沿用現有設計；沒有用假想更小方案當成已足夠。

- [x] 既有 tests／instrument／圖片／data 各自說明支持範圍，skip／未測／host紀錄未冒稱 reviewer 實測。

- [x] 未啟動 Agentflow、未委派、未操作裝置／網路、未改 source／Git／設定／log；唯一寫入為此 review.md。
