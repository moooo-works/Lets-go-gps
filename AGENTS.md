AGENTS.md — Jules / Codex 協同開發規範（Lets-go-gps）

目標：避免「Codex 的功能已合併 main，但 Jules 未拉取 main 就在舊 branch 繼續開發」導致回歸與整合失敗。
適用對象：google-labs-jules / Codex（含其 bot 連接器）。

0. 合併門檻（Hard Gates）

未滿足以下條件，不得要求合併 PR：

分支必須與 main 對齊

GitHub 分支狀態：behind main = 0

若 behind > 0：先 rebase/merge main，直到 behind = 0

必跑驗證

./gradlew test

./gradlew lintDebug

./gradlew assembleDebug

PR 必須單一主題

一個 PR 只做一個主題（例如 P1-3 路徑儲存）。

禁止混入不相關修正或大範圍重構。

1. 每次開工前 Git Preflight（必做）

任何開始 coding 前，先執行並在 PR 或工作回覆中貼出輸出：

git rev-parse --abbrev-ref HEAD

git status --short --branch

git fetch origin --prune

git rev-parse origin/main

git log --oneline -5 origin/main

Preflight 判讀規則

若分支基底為 Initial commit 或明顯不是最新 origin/main：停止，先切回 main 同步。

若 git status 顯示 behind：停止，先 rebase/merge。

若 diverged：依第 4 節處理（禁止用 dummy commit 硬推）。

2. 分支策略（防止「未拉 main 繼續寫」）
2.1 分支建立規則（必遵守）

所有 feature/fix 分支必須從最新 origin/main 開出：

同步 main：

git fetch origin --prune

git checkout main

git reset --hard origin/main

開新分支：

git checkout -b feat/<topic> 或 git checkout -b fix/<topic>

2.2 開發期間同步規則（必遵守）

只要出現以下任一情況，必須先同步 origin/main 再繼續：

另一個 agent 的 PR 已 merge 到 main

GitHub 提示分支 behind main > 0

你要開始一個新任務（即使在同一分支）

同步方式（優先用 rebase）：

git fetch origin

git rebase origin/main

3. Jules / Codex 分工與互踩規則
3.1 建議主責分工

Jules 主責

UI 流程、Compose screen、Navigation、端到端串接

Codex 主責

系統/平台：Foreground service、AppOps、Lifecycle edge cases

Data layer / 測試補強：DAO/Repo/ViewModel tests

3.2 併行開發規則（重要）

若兩個 agent 需要同時工作：

必須事先約定「檔案範圍」互不重疊（Disjoint file sets）

任何會同時改到 MainActivity/NavHost/MapScreen/MapViewModel 的任務，不可併行（先完成一項再開始下一項）

4. Diverged / 無法 force-push 的處理（必遵守）

某些環境無法 git push --force（credential 限制或 submit tool 不支援）。遇到 branch diverged 時，禁止：

用 dummy commit 嘗試讓工具「剛好能推」

在 diverged 分支上反覆 submit

正確流程（必用）

建立新分支自最新 origin/main，只 cherry-pick 功能 commits：

新分支：

git fetch origin

git checkout main

git reset --hard origin/main

git checkout -b feat/<topic>-on-main

cherry-pick：

git cherry-pick <commit1> <commit2> ...

跑驗證後建立新 PR 指向 main。

原 diverged 分支：標記 DO NOT MERGE 並關閉 PR（如有）。

5. Navigation / ViewModel Owner 規範（避免 SAVED_STATE_REGISTRY_OWNER_KEY crash）
5.1 禁止寫法

禁止在 Screen composable 預設參數呼叫 hiltViewModel() / viewModel()

禁止在 NavHost 外層（或非 route composable scope）建立需要 SavedState 的 ViewModel

5.2 必須寫法（NavHost）

在 route composable 內用 backStackEntry 取得 VM：

composable(route) { backStackEntry -> val vm = hiltViewModel<VM>(backStackEntry); Screen(vm) }

5.3 SavedStateHandle result 規範

使用 SavedStateHandle.getStateFlow(...) 監聽結果

消費後清除 key，避免重複觸發

6. Mock Location 權限與錯誤分流規範（避免誤導）
6.1 權限判斷

isMockingAllowed() 以 AppOpsManager OPSTR_MOCK_LOCATION 為主判斷，必須可分辨三態：

Allowed

NotAllowed（未選 mock app）

CheckFailed（AppOps 取值失敗/例外）

6.2 UI 分流規則（必遵守）

NotAllowed → 顯示「請到 Developer Options 選擇 mock app」+ 按鈕導向設定

CheckFailed → 顯示「Permission check failed / unknown」，不得導向設定

Engine failure（setup/setLocation/teardown）→ 顯示 engine failure，不得誤導為未設定

6.3 設定頁導向規則（必遵守）

禁止冷啟動自動跳設定

只能在使用者按下按鈕時導向

「Go to settings」按鈕需有防誤觸（例如 500ms delay enable）

7. 地圖 UX 規範（避免回歸）

移動地圖中心不得出現不預期的藍色 pin/marker

準星以地圖可視容器置中（非整個螢幕置中）

點擊儲存 marker 只顯示名稱（不顯示經緯度 snippet）

從列表選取儲存點 → 地圖必須 animate 置中 + 更新中心座標顯示

8. PR 必填 Checklist（PR description 必須包含）

 Git preflight 輸出（branch/status/origin/main hash）

 GitHub 分支狀態 behind main = 0（截圖或文字）

 ./gradlew test 結果

 ./gradlew lintDebug 結果

 ./gradlew assembleDebug 結果

 實機 smoke test（若環境無法測，需註明並由提交者補測）

mock app 可選

冷啟動不跳設定（防誤觸有效）

Map 4 項 UX（pin/準星/snippet/列表置中）

新功能基本流程（依 PR 主題）

9. 文件維護策略

本文件屬於「穩定規範」，除非遇到新回歸類型或架構重大變更，否則不頻繁改動。

每次新增規範，優先以「最小新增條款」方式擴充，避免整份重寫。
