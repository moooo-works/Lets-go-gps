* _2026-10-08 16:51:15 +0800 (gpt-6-astra/medium)_

Reviewed commit: f1ae4f367d2127c35df9a9bdf59c2376e1b37ef0

Review range: d01db7d9900d30f2473ab9da36317ed11ad21230..f1ae4f367d2127c35df9a9bdf59c2376e1b37ef0

- **目前不能接受交付。** 兩個程式問題會讓舊載入覆蓋新指令，或讓已消失的浮窗仍被當成開啟。先修正並補上對應測試。

- **裝置接受仍未完成。** 主地圖可見及安裝成功，不證明背景浮窗、鍵盤、小地圖、旋轉與關閉正常；指定手機的權限仍待使用者設定。

- **改動範圍合理。** 延用既有資料、地圖與模擬服務，沒有新增依賴、資料庫或遊戲資料入口。必要修正限定在本次新增功能。

## Verdict

- **Outcome: BLOCKING** — F1、F2 與 F3 尚未滿足 R3、R4、R6 及必要裝置接受證據。

- **Minimality: PASS** — 檢視 18 個變動檔案、1,503 行新增與 94 行刪除；新增功能與必要接線仍屬單一主題，未發現必須先刪掉的無關行為。

- **Conformance: BLOCKING** — 舊 route 讀取與 window 自行關閉尚未接入完整取消契約；必要驗證仍缺少實機證據。

- **Overall Verdict: BLOCKING** — 可繼續在授權範圍內修正；本報告不支持 Result Go、宣稱完整接受或要求合併。

## Findings

### F1 — P1 / BLOCKING：較早的路線讀取可覆蓋較新的定位或停止

- **Trigger:** 沒有活動 route 時點選已存路線，repository 讀取尚未返回；接著選另一地點並定位，或按停止；最後舊 route 讀取返回。

- **Impact:** 舊結果仍能執行 `UseRoute`。較新的單點模擬會收到 STOP 並被舊路線取代；按停止後也可能再被套入未完成的舊載入。使用者最後一個動作沒有優先權。

- **Evidence:** `FloatingCompanionController.kt:164–181` 只以 `loadGeneration` 和活動 route 版本防晚回；只有另一個 load 或 `close()` 增加該 generation。`requestLocate/requestExplore/applyQueue` 不作取消。單點模擬前後 `activeRouteKey()` 均是 null，版本檢查不能阻止這條路徑。`MapViewModel.kt:220–224` 只取消 VM 命令，不能取消 controller 的 route 讀取；晚回會在 `123–126` 建立全新有效命令，`153–170` 可 STOP 並套入舊路線。

- **Action:** 在新的明確執行、停止及關閉時，使尚未完成的 controller route 讀取及相關確認失效。沿用現有序號/取消方式即可，不需要新架構。保留收合及有效回 App 待辦的既定語義。

- **Required proof:** 用可延後完成的 route repository 測試「load → 新 locate → 舊 load 返回」及「load → stop → 舊 load 返回」。斷言舊結果沒有新增 action、STOP、setRoute 或扣次數；至少一個整合測試驗證 VM 服務命令。這是可由程式路徑確定的缺口，本 reviewer 未執行新增重現測試。

### F2 — P2 / BLOCKING：視窗更新失敗後，控制器仍認為工具開著

- **Trigger:** 浮窗已開啟後，切輸入焦點或拖曳的 `updateViewLayout` 失敗，例如 overlay 權限被撤銷。

- **Impact:** manager 清掉視窗與 owner，但 `isJoystickEnabled` 仍為 true，ticker 尚未停止，controller 與 VM 待辦仍有效。下次按入口先走關閉而非重開；已消失面板的晚回命令仍可能執行。

- **Evidence:** `JoystickOverlayManager.kt:103,155` 直接呼叫 `hide()`，`120–135` 沒有通知 controller/VM。`JoystickController.kt` 只有 toggle、show 拋錯及 onCleared 會停止 ticker/更新啟用狀態。一般關閉所需的 `MapViewModel.kt:252–256` 取消流程不會走到。`JoystickOverlayView` 的 onDispose 已能把方向歸零，因此不宣稱這條路徑必然持續移動；確定問題是啟用狀態、ticker 及待辦沒有同步清理。

- **Action:** 為 manager 非預期消失加入一次性的上層通知，停止 ticker、清啟用旗標並撤銷新浮窗待辦；避免 hide/通知互相遞迴。既有 owner/composition 清理可保留。

- **Required proof:** 測試 show 成功後 update 失敗，檢查 owner 銷毀、狀態關閉、ticker 停止、晚回無命令，且重新啟用只需一次。現有 manager 兩個測試只覆蓋雙 show/hide 及 addView 失敗。

### F3 — Acceptance / BLOCKING：尚無浮窗真實裝置證據

- **Evidence:** coordinator 提供 Pixel7Pro 同簽章安裝、原資料 375/6/591 及主地圖可見；mock/overlay 權限仍未開。尚無背景 App 上的鍵盤、小地圖、拖曳、旋轉、反覆開關與原搖桿證據。

- **Action:** 權限設定後，完成 spec S6 的非遊戲背景 App smoke 與主地圖四項 UX 檢查。記錄裝置/API及各結果，不能用 Robolectric 或主地圖截圖替代。

- **Focused risk:** manager 目前依 displayMetrics 夾位置，UI 高度依 screenHeightDp 減固定值；未見系統列與 IME 可用區域的直接處理。這不能從 source 宣稱裝置會失敗，也不能當成已證明不超出可用區域，需在鍵盤展開和橫向時核對。

## Reconstructed normal journey and coverage

1. 由主地圖既有 Pro 入口，經 Pro 與 overlay 權限檢查開泡泡；展開後切搜尋、收藏、路線、控制或小地圖。視窗透過同一 VM 狀態與純參數 view 顯示。

2. 搜尋提交先解析座標/PlusCode，否則使用既有地名 repository；收藏搜尋在完整既有清單上交叉過濾。選點、小地圖點擊與獨立待走列表不呼叫引擎。貼上只有明確按鈕取得焦點後讀剪貼簿。

3. 定位、探索與套用 route 走 controller；有活動 route（含完成但仍注入）先確認。VM 捕捉目標與 token，健康檢查後重查 Pro、permissions/AppOps 與步數 gate。套用 route 先 STOP、等待 IDLE 再 prepare；播放是另一個明確動作。

4. 次數不足時回 App 使用原對話框。有效 companion payload 恢復前重查 gate；取消對話框/明確關閉/VM 清理會使該 VM token 失效。F1 是這套保護尚未涵蓋的 controller 讀取邊界。

5. 目前座標只取活動 mock 的 currentMockLocation，選點獨立且複製格式固定。收合/換頁釋放方向與輸入；一般 close 清 pending、composition、owner 與 window。F2 是非預期 hide 沒有走完相同上層清理。

| Requirement | Source / available proof | Acceptance |
| --- | --- | --- |
| R1 | Pro/overlay checks、泡泡、wrapper 與搖桿；裝置互動未測 | BLOCKING：F2/F3 |
| R2 | parser、query generation、明確貼上、交叉篩選；controller 9 tests PASS | 部分證明；短 PlusCode/舊 failure/裝置輸入未完整證明 |
| R3 | 選點獨立、route confirmation、snapshot；取消/版本測試 PASS | BLOCKING：F1、route 晚回缺測 |
| R4 | VM gates、resume action、速度/loop、pending token；VM suite 11 tests PASS | BLOCKING：F1/F2；實機完整流程未測 |
| R5 | 來源分離、Locale.US、配額 Flow、小地圖只 select | BLOCKING：F3；overlay map 未驗證 |
| R6 | owner destroy、composition dispose、焦點 flags、非負夾限；manager 2/position 1 PASS | BLOCKING：F2/F3 |

| Invariant | Review result |
| --- | --- |
| INV-1 | Source 支持搜尋/選點/小地圖沒有直接引擎副作用；正常零 action 測試支持部分契約 |
| INV-2 | 活動 route 確認及取消路徑成立；F1 的非同步命令替代仍需修正 |
| INV-3 | 新啟動與新 pending 走重查 gates，AppOps 仍保留既有分流；未看到 UI 直接繞過服務檢查 |
| INV-4 | BLOCKING：F1/F2 取消涵蓋不完整，實機輸入與背景觸控未證明 |
| INV-5 | Source 與測試支持目前/選點分離及負座標固定格式；真實 map 顯示仍待驗證 |
| INV-6 | 本次 diff 限定浮窗及必要接線/tests/四語字串，無 DB、manifest、依賴或 Unity/PR58 改動；資料計數引用 coordinator，不聲稱 reviewer 查過手機 |

## Minimality and defensive security check

- **Deletion attempted:** 刪除 CompanionExecutionRequests、controller query generation 或 route confirmation 會失去目標快照、晚回防護或取消保證，不能安全刪除。刪除新增 map/control 分頁不再滿足已授權結果。

- **Combination attempted:** 把待走列表直接合入 active waypoints，會經既有 setRoute 停止模擬；把整個 companion 放回主頁無法滿足背景操作。維持分開 state 是必要的。

- **Reuse attempted:** 已重用 repository、parser、GoogleMap、搖桿與 service actions。直接重用既有 loadRoute/startSingle helper 會先清路線或讀可變中心，新增有限命令入口有理由。沒有要求全面重構主頁的舊 pending 制度。

- **Trust boundaries:** 外部文字只供 parser/既有搜尋；未見 shell、任意 intent、遊戲讀取或新的網路出口。剪貼簿只有明確貼上/複製；浮窗在 owner 內不建立 Hilt/SavedState VM。安全檢查聚焦本次改動，並非整個 App 或第三方 SDK 的安全認證。

## Evidence and limits

- **Tests:** 重用 coordinator 的 `./gradlew test lintDebug assembleDebug` exit 0 與 `/private/tmp/floating-required-checks.log`。總數為 269 tests、0 failures、60 skips；未重跑 broad suite。已讀 XML 確認 controller 9、helper 4、manager 2、position 1、VM 11，各 0 failures/errors/skips；27 是這五個 suite 的總數，包含 VM 原有測試，不全是新案例。

- **Lint:** 34 errors、95 warnings、3 hints；既有 34 errors 留存，另增 3 warnings。Gradle exit 0 不代表零 lint 問題；不要求本功能順手修基線。

- **Missing meaningful tests:** 除 F1/F2，尚缺新 pending 成功恢復時 Pro/AppOps/health 已變、晚回廣告在 close 後、UseRoute 等待 STOP/timeout、resume 不重扣次數的直接命令驗證。現有 snapshot 與取消測試有價值，但不能代替上述跨邊界案例。新增拒絕測試應明確驗證零 consume，現有 VM 斷言主要是零 service。

- **Review method:** 實讀 Ask、design、requirements、spec、codewalk、tracker、review-facts.json、target source/diff 及重點測試。此次 source review 以呼叫路徑推演發現問題，沒有在手機或新增測試中動態重現 F1/F2；沒有操作網路、Git mutation 或修改 source/config/log。Git source 開始時乾淨；之後修正須以新 commit 重新核對。

- **Isolation:** 只有本 review.md 是 reviewer 寫入範圍；shared filesystem 並非 OS 強制唯讀。沒有委派、啟動另一個 Agentflow 或操作 PR58/Unity/手機。最終 behind-main/PR/notebook/STATUS/route records 由 coordinator 完成，這次 snapshot 審查不替代那些交付記錄。

Self-check: 已固定 Reviewed commit 與 diff；重建正常旅程；列全 R1–R6/INV-1–6；嘗試 deletion/combination/reuse；Outcome/Minimality/Conformance 與 Overall Verdict 明確；兩個程式 blocker 與缺裝置證據分開；测试結果未等同接受；只寫指定 review.md。
