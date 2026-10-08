* _2026-10-08 18:46:40 +0800 (gpt-6.1-sol/high)_

Reviewed commit: c0e520f1732ee690ce4e29a45dba708e44fd59cd

Review range: 63030d7..c0e520f1732ee690ce4e29a45dba708e44fd59cd；既有浮窗程式重用 915546401df32839243034deb6185a1b36794364 的 Code PASS，Android 17 修復重用 1ecc8a4c4d9e675971778fa87e580fd96b9da18f 的獨立 PASS。

- **Acceptance 仍為 BLOCKING。** 已有真實鍵盤、背景 Calculator、明確定位、座標來源分離與小地圖中心選點證據；路線、收藏、旋轉及完整關閉流程仍未驗證。

- **本次鍵盤修正沒有新的程式 blocker。** 單一畫面檔增加鍵盤避讓及 Search/Done 動作，範圍符合已授權功能，不需要旁支重構。

- **下一步由 host 接續實測。** 手機鎖定，等待使用者解鎖；測試 service 已終止、原資料核對相同，但輸入法尚待還原。此報告不供 Result Go 或合併建議。

## Verdict

Outcome: BLOCKING

Minimality: PASS

Conformance: BLOCKING

Verdict: BLOCKING

- Outcome 與 Conformance 指完整實機接受尚未滿足。新增程式差異的限定審查為 PASS；缺證據不能判成已確認的程式錯誤，也不能判成全功能通過。

- 這是 Android 17 修復、使用者已設定系統權限後的新 runtime acceptance 階段，不是第四次重新審查相同浮窗程式。原 F1/F2 Code PASS 沿用；原 F3 已補部分實機證據，剩餘條件仍 BLOCKING。

## 新差異與最小性

- 直接核對凍結 HEAD、乾淨 source status、精確 diff 與畫面周邊接線：只有 FloatingCompanionView.kt，8 行新增、1 行刪除。未新增依賴、資料庫變更、廣告流程、設定導向或 service 入口。

- `imePadding()` 讓面板避開輸入法；搜尋欄的 Search 先呼叫既有 `releaseInput()` 再提交搜尋；收藏欄的 Done 只釋放輸入。該 helper 清 Compose 焦點、隱藏鍵盤並通知 manager 還原 `FLAG_NOT_FOCUSABLE`。搜尋仍只走選點資料流程，沒有直接定位或繞過資格檢查。

- 若移除避讓，已重現的輸入框遮擋會回來；若移除 Search 動作，鍵盤提交與收焦點的已驗證流程會失去。Done 重用同一釋放入口，不增加命令。沿用既有 helper 比增加第二套視窗控制更簡單；沒有必要抽取新架構。

- 直接讀取 `ime-visibility-check.py`：它要求唯一聚焦的 EditText 與唯一 InputMethod 視窗，從實際 bounds 及鍵盤 touchableRegion 計算矩形交集。本次 reported red 的垂直重疊是 1328–1485；修正後輸入框底部 1307、小於鍵盤頂部 1328，因此該檢查可支持輸入框未被鍵盤遮住。它沒有檢查整個面板、所有旋轉配置或背景是否能點擊；那些須用各自實測證據。

## 證據與接受範圍

- 實機操作者是 host；本 reviewer 沒有操作手機或自行執行上述 script。本次讀取 [實機紀錄](device-acceptance.md)、root devlog 與 host 更新；沒有取得本輪獨立畫面重播證據。因此下表的手機結果明確屬 host 的已記錄觀察，不宣稱 reviewer 親測。

- 裝置為 Pixel 7 Pro、Android 17/API 37、CP3A.260905.009。使用者已設定 overlay/mock 權限；host 未代改安全設定。Android 17 App UID 修正後 OK (1 test) 與 health 結果沿用 [Android 17 獨立審查](android17-review.md)，不以該測試取代浮窗或路線實測。

| 需求 | 本輪已有支持 | 完整接受仍缺 |
| --- | --- | --- |
| R1 | Calculator 上泡泡可展開/收合並保持可見；背景可算 2+2=4；原 Pro/overlay gate 重用既有 Code PASS | 泡泡拖曳吸邊、明確關閉、原搖桿實機流程 |
| R2 | Search 鍵盤提交負座標；選點複製及明確貼上格式正確；既有查詢競速/parser/filter 測試與 source 結論沿用 | 一般地名、PlusCode、收藏及資料夾交叉篩選實機證據；收藏 Done 動作實測 |
| R3 | 搜尋只選點，沒有啟動 service；明確定位後 service 與系統 location 含同一測試座標；小地圖使用中心只改選點 | 收藏保存、待走列表、活動 route 替換取消/確認的裝置流程 |
| R4 | 明確定位成功；資格、健康、次數、不可變目標及 pending 取消沿用已通過程式審查與測試 | 已存 route 載入、播放/暫停/繼續/停止、速度/循環、探索/搖桿及回 App 待辦完整流程 |
| R5 | 選中臺北正座標時目前模擬仍是負座標；小地圖道路圖資可見、準星在容器中心、拖曳及使用中心不改模擬、不新增 app marker | 小地圖直接點擊、無 mock 顯示與配額的裝置確認；主地圖四項 UX |
| R6 | 輸入框避開鍵盤；Search/收合後 IME 關閉且 NOT_FOCUSABLE；背景 Calculator 可點；owner/window 清理測試沿用 | portrait/landscape、重複啟閉、冷啟動不開浮窗/不跳設定、離開輸入與正常關閉、Activity/Map owner 生命週期實測 |

- **INV-1：部分實機支持。** 搜尋改選點、小地圖 camera/使用中心後目前模擬未變；查詢、貼上、資料夾及點擊的零副作用契約沿用 Code PASS。未測項不擴大為全路徑實機證明。

- **INV-2：程式結論沿用，裝置接受未完成。** 原活動 route 替換確認、取消與晚回讀取保護已通過修正審查；本輪尚未用實際活動路線驗證取消保留。

- **INV-3：資格邊界沿用 PASS。** 新 UI 差異沒有新增 dispatch；AppOps Allowed/NotAllowed/CheckFailed 與既有 gates 保留。現在權限允許，只證明本次允許情境成功，不假稱本輪在手機重測拒絕/例外/次數不足。

- **INV-4：焦點有實機支持，完整清理仍缺。** 收合後背景操作成功。Manager failure/owner 與 VM dismiss/stop 的既有測試仍有效；force-stop 後 service 不存在，是安全收尾證據，不能代替 App 自己的關閉、ticker、composition 或 owner 清理。真實系統撤權未執行，保留測試替代範圍的限制。

- **INV-5：本輪來源分離與負號已有支持。** 選中及目前值可保持不同，選點複製再貼上保留固定小數與負號；不將主地圖中心冒充目前模擬。各來源完整複製與無活動模擬裝置流程仍按需求續測。

- **INV-6：限定範圍支持。** 精確新 diff 單一浮窗主題；host 紀錄 DB 前後各 table rows hash 完全相同，375 saved_locations、6 routes、591 route_points、0 folders。本 reviewer 未讀 private tar 或原資料，不宣稱自行重算 hash；PR58、Unity、其他手機未納入操作。

## 必要檢查與剩餘 blocker

- 直接核對 `/private/tmp/floating-ime-final-build.log`，test、lintDebug、assembleDebug 完成且 BUILD SUCCESSFUL；直接統計當前 XML：279 tests、0 failures、0 errors、60 skipped。VM 13、Manager 3、Controller 11 均零 failure/error/skip。未重跑已通過的 broad checks。

- 直接核對 lint XML：34 Errors、95 Warnings、3 Hints；host 記錄 errors 為既有基線。命令 exit 0 不代表 lint 清潔，本階段不修無關基線。

- **F3-runtime — Acceptance / BLOCKING：未完成的實機條件。** 觸發是手機鎖定導致測試中止；影響是 R1–R6 的剩餘互動與生命週期尚無充分證據。上述表格列明缺項，唯一外部阻塞是鎖屏，沒有新 source bug；host 等使用者解鎖後補原功能驗證，不繞過鎖屏。

- **清理待辦。** host 已 force-stop App、確認 MockLocationService 不存在，且原 DB rows hash 一致；English 輸入法仍待還原注音。正常停止/關閉與 rotation 復原應在下一次實機紀錄如實完成，不能把 force-stop 算作停止按鈕測試。

- **交付記錄由 host 維護。** 本報告不改 source、commits、notebook、STATUS、tracker 或 route records。最新 main/behind=0、單一主題 PR、必要測試、裝置限制與 Result Go 記錄仍由 host 按原規範更新；限縮 BLOCKING 報告不代表 A049 結案。

## Review limits

- 僅寫 floating-runtime-acceptance.md；沒有 source、Git、裝置、網路、設定或其他 log mutation，沒有新 Agentflow invocation 或 delegation。shared filesystem 的唯讀限制靠指示遵守，並非 OS 強制隔離；同 provider 的獨立 worker 不宣稱跨供應商驗收。

- 僅重用已完成的 Code PASS 與 Android 17 PASS，檢查新 UI 差異及本輪實機證據。沒有重新大範圍審查未變 source，也沒有遊戲 POI、花數或 Pikmin 實測聲稱。

Self-check: 精確 Reviewed commit 與新差異已固定；Outcome/Minimality/Conformance/Verdict 分開；R1–R6 與 INV-1–6 均對應已證據及缺項；實機由 host 操作，程式/自動測試/手機接受沒有混稱；私密資料未讀；沒有完整 PASS、Result Go 或合併建議；只寫指定報告。
