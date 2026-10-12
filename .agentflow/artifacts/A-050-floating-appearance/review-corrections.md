# 懸浮外觀 F1 修正審查

- Timestamp: 2026-10-12 11:19:37 +0800（Asia/Taipei）。

- Model-effort: `gpt-6.1-sol / high`；同 worker `/root/floating_appearance_review` 續接既有 context，屬 shared context，**不是 fresh review**。同 provider、共享 filesystem，沒有 OS 強制唯讀隔離。

- Reviewed commit: `2a29ab93335d31b419addfd468c147b820192d32`（ROOT 證據 snapshot；main app 未包含產品修正）。

- Product implementation commit: `a6cf172deadfbf4f3bc376d53d48edd7e5417a71`（`.worktrees/floating-appearance`；完整產品 patch 的 base 為未合併 PR59 的 `ba2541f`）。

- 前次報告：[review.md](review.md)，仍固定 ROOT `eb12d49...`／產品 `5c9f34f...`，原 Verdict BLOCKING 不改寫；原 bytes SHA256 為 `a76b2288700a3b279153a92a7de9bf91b5b763a1c6c485065e7962c768d90078`，與新 ROOT 內保存的首次報告一致。

**摘要**

- F1 已解除：深色狀態提示對比由 3.36 提高到 9.57，停止按鈕文字仍清楚；修正只增加浮窗局部兩個色值。

- 新測試先失敗、修後實際通過，三項必要 Gradle 成功。主題／四語／輸入／資料保留的先前接受證據可重用。

- 沒有新 blocker。手機鎖定，最新提示畫面未再實測；凍結驗收文件的改動行數錯字需由 host 作 record-only 更正，不影響產品判斷。

## Outcome: PASS

- **F1 resolved：**`FloatingOverlayTheme.kt` 只在 `dark=true` 時改用 `error=#FFB4AB`、`onError=#690005`；LIGHT 沿用 base.error/base.onError。原 `floating_blocked` 仍讀 error，既有搖桿停止按鈕仍讀 onError/error，沒有改觸發條件、錯誤文字、permission 分流或 engine。

- 使用與首次相同的相對亮度公式獨立計算，與實際執行的 assertions 相符：

| 色組 | LIGHT | DARK | 門檻 |
| --- | ---: | ---: | ---: |
| error / surface | 4.59049 | 9.56763 | ≥4.5 |
| onError / error | 4.82940 | 7.71664 | ≥4.5 |
| onSurfaceVariant / surfaceVariant | 6.18041 | 8.72991 | ≥4.5 |
| outline / surfaceVariant | 3.95364 | 5.73179 | ≥3 |

- 新 ColorsTest 將 base.error 指定為 App 實際 `Red500`，base.onError 沿用 DARK `V2BgLight`／LIGHT `V2SurfaceLight`，不再以 Material 預設 error 代替 App 顏色；加入表中四組檢查，保留先前 enabled labels 與 outline/surface 檢查。

- `/private/tmp/appearance-error-red.log` 顯示此測試 **1 executed／1 failed**，AssertionError 位於色彩門檻檢查；新修正後 XML 顯示 ColorsTest **1 executed／0fail／0skip**。紅證據不是環境略過，綠證據確實執行新 assertions。

- `/private/tmp/appearance-error-final.log` 含 `test`、`lintDebug`、`assembleDebug` 與 `BUILD SUCCESSFUL`；XML 總計 **280 tests／0fail-or-error／60skip，即 220 實際執行**。Lint 成功執行仍不是 clean lint，沿用 34 既有 Error／95 Warning／3 Hint 的限制。

- Locale wrapper、監聽註冊／釋放、Configuration 複製、SYSTEM/LIGHT/DARK 選擇、main Theme 隔離、Manager/VM caller 與 joystick 繼承皆未改。首次已查核的 Compose UI 1.11.4 LocalResources 更新機制、四語 AppUID instrumentation `OK1`、持續開窗切語系與正常畫面實測仍適用。

- design、六張安全圖片、data-preservation 在新 ROOT 與原 ROOT 證據 commit 的 bytes 相同；原 English/DARK、DB375/6/591 與所有表 rows hashes 的保留證據可重用。Host 回報最新 APK `adb install` Success；本 reviewer 未操作裝置。手機鎖定，**最新 warning UI 未再實測**，不能以安裝成功或舊照片冒稱該畫面實測 PASS。

## Minimality: PASS

- 嘗試只提高 error 而沿用 onError：不充分。既有深色 onError 為近白色，對新淺紅背景的文字對比會不足；兩個配對色值都需處理，才能同時修提示與保留停止按鈕可讀性。

- 嘗試改 main Theme 或改提示／停止按鈕個別顏色：前者會擴大到主程式，後者把同一 error 色組分散到 caller。現方案直接在既有浮窗 palette 內增加兩行，保留 LIGHT fallback，已是充分且更集中的局部修正。

- 補測覆蓋首次漏掉的實際 error base 及搖桿文字／边框背景，與授權的可辨識結果直接相關；没有 refactor、rename、reformat、新 dependency 或相鄰行為修復。更小替代不足，所以不列 Minimality BLOCKING。

## Conformance: PASS

- 本次只查新固定來源的兩個修正檔、相對產品 diff、精確 ROOT patch／facts／接受紀錄與指定測試證據，重用首次審查；沒有擴大功能、委派或啟動 Agentflow。使用者原 Ask 與已接受 design 定義範圍，host 建議沒有被當成新增行為授權。

- ROOT 的 `source.diff` 與 `git diff ba2541f a6cf172... -- app` **byte-identical**；SHA256 為 `62e8990bd6b57d3e006d81b0f03cd31a1fa2b9c8cc718f44815bbe4c89fa8f27`。完整產品為 **7 paths、162 additions／16 deletions、178 changed lines**，符合 review-facts 的 178。

- 相對原產品 `5c9f34f...`，實際產品差異僅 Theme **2 additions／0 deletions**、ColorsTest **10 additions／2 deletions**，合計 **12 additions／2 deletions**；合併的 root records 未增加其他 app 改動。

- **非阻擋的紀錄錯字：**固定 ROOT device-acceptance 寫「164add18del」，与 Git 實際 162add16del 不符。以精確 patch／Git／facts 為準，host 已承諾 record-only 更正；首次報告仍保留當時原結果，不藉本次改寫。此文字更正不會使產品測試失效。

- ROOT notebook 的 A050 Ask/RUN/WIP 仍如實表示原產品與待 review/PR/closeout；STATUS 尚為前輪摘要。host 仍需將最新 pin、修正結論與未重測限制寫入正常收尾與 route records，不能將本 code/evidence PASS 當成已完成 PR／合併 gate。本 worker 只寫本報告，不更新其他紀錄，也未查遠端 behind 狀態。

## Verdict: PASS

- 原 F1 已有最小產品修正、先紅後綠與量化色彩證據，沒有剩餘 code/acceptance blocker。首次 Verdict BLOCKING 僅對原固定版本成立，並保留原文。

- 可由 host 接續必要紀錄更正與單一主題 draft 交付；本結論不批准合併或發布。Pikmin Bloom 本身、older Android、release／付費與最新 warning 真機畫面均維持未測，不冒稱 PASS。

Self-check:

- [x] ROOT 與 product 精確 pin 清楚，main 無新產品 code 保持透明；source.diff byteexact。

- [x] 首次 review.md bytes 未改；本次是同 worker shared-context correction review，非 fresh。

- [x] Outcome／Minimality／Conformance／Verdict 明確，F1 的量化結果、必要配對色值與真實 base 測試皆查核。

- [x] Git 162add16del／178lines 優先於文件錯字；沒有接受 host 的錯誤統計。

- [x] 沿用證據與最新執行結果分清；60skip、安裝成功、鎖屏未測都未冒稱實機 PASS。

- [x] 未重跑 broad suite、未操作裝置／網路／設定、未改 source／Git／log／首次報告；唯一寫入為 review-corrections.md。
