* _2026-10-08 16:07:12 +0800 (gpt-6.1-sol/high)_

# Final requirements summary

- **需求可接受。** 將已授權的懸浮搜尋、收藏與控制做進 App，採單一側邊泡泡配置，重用現有資料與模擬引擎。

- **實作仍有必要條件。** 搜尋選點不能改模擬位置；活動路線的替換需要確認；浮窗鍵盤及小地圖必須有裝置證據才算完成。

- **下一步。** Host 依 spec 補上新動作的安全入口、視窗清理及必要測試，不需要再次詢問同一功能的授權。

## Owner decisions and source

- 使用者 A049「好，那將這功能開發到app上」，承接 A047–A048；本次以 host 提供的 `design.md` 作為已捕捉需求。未自行重新讀取其他對話記錄。

- 安全預設為側邊收合泡泡；不增加第二套底部布局、DB schema、依賴、廣告、下一站、倒數或遊戲資料接口。主頁既有廣告/計次流程只做新浮窗入口所必要的接線，不全面重構。

- **Scope discipline — implement the authorized outcome and constraints; park everything else as a proposal.** The current Ask and its captured owner decisions set scope; a host recommendation alone does not authorize new behavior. Include necessary tests, commits, notebook, STATUS, and route records. Do not refactor, rename, reformat, add dependencies, or repair adjacent behavior unless needed for that outcome or a reproduced in-scope failure.

## R1–R6 acceptance

| ID | 使用者可看到的結果 | 必要接受證據 |
| --- | --- | --- |
| R1 | 由既有 Pro 搖桿入口開啟工具；泡泡展開、收合、拖曳吸邊、關閉；原搖桿仍可操作 | Pro 拒絕測試、泡泡/搖桿背景 App 操作 |
| R2 | 地名搜尋、座標/PlusCode及明確貼上；搜尋收藏、收藏篩選、資料夾篩選 | 新舊查詢競速測試；負座標、短 PlusCode、資料夾/收藏交叉查詢 |
| R3 | 選點显示名稱、說明與座標，可定位、存收藏或加入待走列表；選取不定位 | 選取零引擎副作用；活動路線替換取消完全保留；確認後只執行原選定目標 |
| R4 | 已存路線載入、播放、暫停、繼續、停止、速度、循環/往返、探索及搖桿 | 現有 gate 與命令測試；不足權限/次數零啟動；回 App 後只恢復仍有效的新浮窗待辦 |
| R5 | 明確分開目前模擬與選中座標，複製已知來源；显示今日寫入、上限及次數；小地圖點擊選點 | 無活動模擬不显示假位置；Locale/負號複製；實機 Google Map 可見且不改 route |
| R6 | 只在輸入時接收鍵盤焦點；收合/關閉可操作背景 App；面板在可用螢幕內；重複啟閉、撤銷權限與 owner 清理不崩潰 | 視窗/清理測試，以及 IME、旋轉、重複啟閉裝置 smoke；冷啟動不開浮窗、不跳設定 |

## INV1–INV6 contract

- **INV-1：選點沒有執行副作用。** 搜尋、貼上、資料夾切換與小地圖移動/點擊不寫 currentMockLocation、不呼叫 setRoute、不切主頁 mapMode。

- **INV-2：活動路線不被暗中替換。** PLAYING、PAUSED 及仍在注入的完成路線均須保護；待走列表獨立於活動 waypoints。取消確認不停止、不重設、不載入路線。

- **INV-3：新增入口守住既有資格。** Pro、位置/通知權限、AppOps 錯誤分流、健康檢查及 StepSyncGate 仍有效；新浮窗待辦在恢復執行前重新判斷，禁止從 UI 直接呼叫未檢查的 service helper。

- **INV-4：焦點與資源可釋放。** 收合、離開搖桿分頁及關閉先清方向；hide 清 composition、owner 與 window。關閉/onCleared 撤銷新浮窗待辦，晚到結果不能再啟動。

- **INV-5：座標來源正確。** 目前位置只來自有活動模擬的 currentMockLocation；選點保持獨立。複製使用固定小數格式與負號，不拿中心當目前位置。

- **INV-6：工作隔離與單一主題。** Host 已指定 `.worktrees/floating-companion`、基底 `2c03c72`；不改 PR58、Unity、DB/廣告或裝置原資料，不輸出 key/備份。本 worker 只寫指定三份文件。

## Status

- **Requirements: PASS。** 需求、單一配置與範圍清楚，沒有必要的未決 owner 選擇；技術接受條件見 spec。

- **Completion: NOT VERIFIED。** 本 worker 未執行測試、未操作裝置、未 fetch 或改 source；host 的 Git preflight、三項 Gradle、資料計數、裝置驗證及最終接受證據不能由本報告代替。

Self-check: R1–R6 與 INV-1–6 均已列出；owner 決策與 source 事實分開；只寫授權文件，沒有擴充行為。
