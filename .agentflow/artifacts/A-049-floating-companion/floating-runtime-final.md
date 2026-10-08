* _2026-10-08 19:36:44 +0800 (gpt-6.1-sol/high)_

Reviewed commit: ddeb51cae1227fccb6efb4defd70635d1eef770a

Review range: c0e520f1732ee690ce4e29a45dba708e44fd59cd..ddeb51cae1227fccb6efb4defd70635d1eef770a

- **本範圍 Acceptance：PASS。** 解鎖後補齊路線取消、控制、收藏、旋轉、冷啟動、背景小地圖及清理證據；配合已執行的單元測試，原實機證據 blocker 可解除。

- **橫向修正符合最小範圍。** 只將橫向小地圖移入既有可捲動內容，使用中心按鈕已能到達；直向結構維持，未發現新程式 blocker。

- **收尾已完成。** 測試收藏移除、資料紀錄前後一致、注音及旋轉還原、Mock service 不存在。Host 接續更新提交、notebook、STATUS、route 與 PR 紀錄；接受範圍及限制見下文。

## Verdict

Outcome: PASS

Minimality: PASS

Conformance: PASS

Verdict: PASS

Blocking findings: none for this bounded runtime acceptance stage.

- PASS 適用於已授權的懸浮工具、指定 Pixel 7 Pro debug 測試版及下列程式/單元/實機證據組合。它不宣稱每個排列都在手機測過。

- [前輪 BLOCKING 報告](floating-runtime-acceptance.md) 保留歷史結論；當時的鎖屏及剩餘實測不足，已由本輪解鎖後的新證據處理。既有浮窗 Code PASS 重用 915546401df32839243034deb6185a1b36794364 的 [審查](review-final.md)，Android 17 修復重用 1ecc8a4c4d9e675971778fa87e580fd96b9da18f 的 [審查](android17-review.md)。不重新廣泛審查未變 source。

## 新差異與最小性

- 直接核對 HEAD、乾淨 source status 及精確 diff：FloatingCompanionView.kt 單一檔案，5 行新增、1 行刪除。只在 landscape 將 CompanionMap 放進既有 body scroll；portrait 仍在原位置，沒有新增 owner、引擎、資格或資料庫行為。

- 直接讀取 [實際 dispatch](floating-runtime-final-dispatch.md) 與 [planner facts](floating-runtime-final-facts.json)：target/worker/唯讀範圍與本報告一致。Facts 的單一檔案、6 changed lines、非 broad change/非新增 trust boundary 與精確 diff 相符；這些是採 targeted 審查的範圍依據，不取代行為證據。

- 若刪除本次改動，橫向固定地圖占用高度，使用中心及後續內容會無法到達。重用既有捲動區即可處理，不需要第二套視窗、全面改排版或新增依賴。

- 已檢查刪除、合併與重用的替代：刪掉地圖會失去已授權能力；另放一個中心按鈕會重複操作，也不處理其他被截斷內容。將所有方向一起改成捲動會擴大手勢改動；本次只在重現失敗的橫向重用現有 scroll，兩個條件互斥、每次只建立一個 map，較符合最小修正。

- 直接檢視 [橫向修正畫面](landscape-actions-fixed.png)：Calculator 可見，面板在畫面內，收合/關閉與使用地圖中心按鈕均可見。地圖部分離開捲動視區是目前捲動位置的結果；搭配 host 的上滑、水平拖曳與按鈕操作紀錄，支持原永久截斷問題已解決。

## R1–R6 接受證據

實機操作由 host 執行；本 reviewer 讀取 [實機紀錄](device-acceptance.md)、安全截圖、非私密資料核對紀錄、build log 與測試 XML，沒有親自操作手機。前輪 IME 修正及原程式安全結論沿用，以下明確區分手機與自動測試。

| 需求 | 支持接受的證據 | 判定 |
| --- | --- | --- |
| R1 | Calculator 背景泡泡開合、拖曳吸邊及多次關閉/重開；右下泡泡及展開 frame 保持在安全範圍。Single 模式搖桿 hold 使 longitude -122.088585→-122.088494，放手後下一張穩定。Pro/overlay gate 沿用原 source 與已執行拒絕測試 | PASS |
| R2 | 手機負座標 Search、明確複製/貼上、Taipei101 地名及完整 PlusCode 849VCWC8+R9；收藏 query37.422 及最愛篩選成功。Controller 查詢競速/交叉篩選、parser 短 PlusCode 均有真正執行證據，沒有捏造手機上的空資料夾操作 | PASS |
| R3 | 搜尋/小地圖只改 selected；保存同點兩次只新增一筆。兩點 queue 套用只 prepare。活動 Tokyo 路線 paused 時選第二條 73 點路線要求取代；第二輪單獨 cancel 後原路線仍 paused。確認/snapshot/晚回取消沿用原 Code PASS 與測試 | PASS |
| R4 | 119 點已存路線 load→prepare→play→pause→resume；speed19→48、loop 與主 App 同步；close 不停止 service、reopen 位置繼續；明確 Stop 顯示未啟用。Explore 在 selected 附近啟動路線。資格及 pending 邊界由原受檢入口及 VM/controller 測試支持 | PASS |
| R5 | 目前負座標與選中臺北正座標分開；無模擬顯示未啟用並停用目前複製。今日寫入/額度/次數畫面可見；小地圖道路、容器置中準星、camera 拖曳/使用中心及直接點擊可用，沒有新增 app marker。主地圖四項 UX 見下述紀錄 | PASS |
| R6 | 聚焦框避開 IME、Search/Done 釋放輸入、收合後 NOT_FOCUSABLE，Calculator 可算2+2=4；直橫向及重複啟閉完成，冷啟動不開浮窗/不跳設定。Manager owner/window failure 與雙 hide 測試、VM dismiss/stop 接線沿用，最後正常停止及關閉後無 Mock service | PASS |

- **短碼與資料夾證據有明確邊界。** 原資料夾為 0，host 未為測試改動原資料。直接讀取 controller 測試：query「park」+folder7+favoritesOnly 只留下 id1，並驗證零執行動作；當前 XML 該案例已執行。Parser 短碼有 reference 時解析成功、無 reference 時回錯；該 suite 12 tests、零 failure/error/skip。這支持相應邏輯，沒有聲稱手機實際點過資料夾或提交短碼。

- **主地圖四項 UX。** host 記錄移動/點圖改 center 沒有額外藍 pin；準星位於地圖可視容器中心；saved marker 泡泡顯示保存名稱與描述 849VCWC8+R9，沒有另加座標 snippet；列表選 test point 使主圖 animate 至37.4221,-122.0841並更新座標。本 reviewer 以該實機紀錄及原 source 結論接受，未取得四項全部獨立截圖。

- **小地圖直接畫面核對。** [選點截圖](mini-map-selection.png) 同時顯示 camera37.423763,-122.088494、selected37.425425,-122.091109及主圖 center37.4238,-122.0885，支持選點來源獨立。Host 記錄點 blank area 時目前未啟用、未啟動 service，補上前輪缺的直接點擊流程。

## INV-1–INV-6

| 契約 | 本輪及沿用證據 | 判定 |
| --- | --- | --- |
| INV-1 選點零執行副作用 | 負座標搜尋未啟動 service；小地圖 camera/中心/點擊不改目前或主圖；queue prepare 不 play；交叉篩選零 actions 測試 | PASS |
| INV-2 路線替換需確認 | 119 點 Tokyo 路線 paused→第二路線確認→單獨 cancel 保持；F1 舊讀取取消及 route 版本/snapshot 測試沿用 | PASS |
| INV-3 新入口守住資格 | 本次 UI 差異沒有命令入口；AppOps 三態與 Android 17 App UID 修復已證實；Pro/permission/pending gate source 與 VM 測試沿用，沒有代改系統權限或自動導設定 | PASS |
| INV-4 焦點及資源釋放 | Search/Done、tab、收合及正常關閉；多次重開、旋轉、搖桿放手後不漂移；owner/composition failure 清理與 VM 取消測試沿用 | PASS |
| INV-5 正確座標來源 | selected/current/camera 分開、負號與固定格式複製貼上、inactive 不用中心冒充目前；來源 helper/Locale 測試沿用 | PASS |
| INV-6 隔離及單一主題 | 新 diff 只處理浮窗橫向截斷；精確清理新增 test row427，原資料 counts/rows hash 一致；未讀備份、未操作其他手機/PR58/Unity | PASS |

## 必要檢查與清理

- 直接讀 `/private/tmp/floating-landscape-final-build.log`：test、lintDebug、assembleDebug 最終 BUILD SUCCESSFUL。當前 XML 總計279 tests、0 failures、0 errors、60 skipped；VM13、Manager3、Controller11及 parser12 均零 failure/error/skip。未重跑已通過的 broad checks。

- Lint XML 為34 Errors、95 Warnings、3 Hints；原 Error 基線沿用，命令成功不代表 lint 清潔。本範圍沒有順手修既有基線。

- 直接核對 [資料保留紀錄](data-preservation.json)：六個 table 的 before/after count 與 SHA256 全部相同，identical=true；主要 counts375 saved_locations、6 routes、591 route_points、0 location_folders。這是核對 host 保存的非私密摘要，不是 reviewer 重讀原 DB 或重新計算 rows hash。唯一測試收藏427已移除。

- 直接檢視 [輸入法還原畫面](ime-restored.png)：注音字母及空白鍵「注音」可見，聚焦框位於鍵盤上方；host 完成 Done/close，旋轉 accelerometer1/user0 還原。最後主 App 明確停止、浮窗關閉，service 檢查沒有 MockLocationService，不以早先 force-stop 取代正常停止驗證。

- 第一輪 route cancel 後批次座標誤觸 stop、PlusCode 輸入附加文字、初始 joystick 座標未穩定及第一次未命中 patch 的 build，均被 host 辨識並排除；接受採後續明確重試的結果，不把誤操作當成功證據。

## Limits and handoff

- 真實撤銷安全權限未測；以 Manager add/update failure、owner 清理、dismiss/VM 取消的已執行測試與原 source 審查支持相應契約。沒有為驗證代改 AppOps。

- 未實測 older Android、release 付費/廣告環境或 Pikmin 遊戲端到端流程。安裝版使用 debug sample ads/Pro override；Pro 拒絕與次數邊界以測試和原 source 接線支持，不宣稱手機付款或遊戲效果已驗證。

- 本階段未發現需再修 source 的 blocker，無需無限重測已成立項目。最新 main/behind=0、PR 單一主題、commit、notebook、STATUS、route 與 Result Go 記錄由 host 按既有規範維護，本 reviewer 沒有執行合併或發布。

- 只寫 floating-runtime-final.md，保留所有舊報告；沒有 source、Git、device、network、config 或其他 log mutation、Agentflow invocation 或 delegation。shared filesystem 靠指示限制唯讀，並非 OS 隔離；同 provider worker 的獨立判讀不宣稱跨供應商驗收。

Self-check: 精確 Reviewed commit/new diff 已固定；四個 Verdict fields 完整；R1–R6/INV-1–6 均連到具體實機或已執行測試；安全截圖/摘要已獨立核對，手機非 reviewer 操作；舊 blocker 的新增證據與歷史錯誤分開；撤權/older Android/release/Pikmin 限制保留；只寫指定 final 報告，未擴張審查或偽造手機測試。
