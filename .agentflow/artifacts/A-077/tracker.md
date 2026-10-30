# A-077 — 升級 v9.1.0 並處理 Hook failed

來源：使用者「升級到v9.1.0，[Image #1] 然後處理掉這個錯誤」。進度 2/3，安裝完成；原 TUI 驗證及套件環境檢查尚未通過。

## Tasks

- [DONE] T-1 — 升級兩份已知安裝

  - 官方 v9.1.0 staged release；原安裝工具更新 `.agents`，未被工具更新的 `.codex` unmanaged 副本以私有備份＋完整替換完成。兩份均逐檔與 tagged release 相等。

- [DONE] T-2 — 查驗 hook 失敗來源與修復狀態

  - 使用者層及專案層 hooks 均空；inline 設定／plugin 未找到 Agentflow 舊命令。新啟動 Codex app-server 對本專案 hooks/list 回覆 hooks/warnings/errors 皆空。

  - 截圖只有 exit 1，現存日誌／該 thread transcript 未提供可歸因的 hook stack。舊工作階段快取為推論，不能聲稱已在既有 TUI 驗證錯誤消失。需由使用者關閉並恢復此工作階段以刷新執行中 hooks；主 agent 不自行終止其宿主。

- [BLOCKED] T-3 — 套件測試、獨立審查與交付

  - 執行官方套件 npm test、版本／全檔雜湊／schema 核對；保留專案 config、產品及開發歷史，最終明示重開步驟。

## Recovery

更新：2026-10-29 17:43:41 +0800。沒有執行中程序。安裝已完成，兩份逐檔與官方 v9.1.0 相等；fresh Codex hooks/list 回覆0 hooks／0 warnings／0 errors。原TUI仍未重開，不能證明畫面錯誤已消失。

套件診斷：預設Git 305測試234通過71失敗；系統Git305測試239通過66失敗，主要受macOS .Trash EPERM限制。沒有通過全套檢查，故本輪紀錄暫留本機、不commit/push。安裝不回退，原官方來源未改。這不是產品App測試或App當機修復。

下一步：使用者退出此工作階段，在本專案以 `codex resume 01a186e9-8f4d-7ea3-8608-debc0406351d` 恢復並完成一回合，確認Hook failed是否仍出現；若仍出錯，取得hook名稱／完整輸出再定位。套件feature/cleanup操作另需可存取原生Trash的環境與支援worktree -z的Git；未要求或擅改系統權限。

待交付紀錄的精確paths與來源見[evidence.md](evidence.md)；私有備份／release身分見[state.json](state.json)。仍保留PR66／POCO待測，無本輪合併授權。

## 使用者驗證更新 — 2026-10-29 18:17:06 +0800（A-079）

使用者回報「錯誤訊息已經沒有出現了，poco也實測過可用了」。原 TUI 的 Hook failed 不再列為待驗；POCO 可用性已由使用者實測確認。此回報不證明歷史根因，也不代表完整 smoke checklist 各項皆逐一通過。

套件診斷環境限制及待提交紀錄仍保留；PR66 本輪未查詢或合併。後續恢復以本更新取代上方要求再次重開與等待 POCO 回報的舊狀態。
