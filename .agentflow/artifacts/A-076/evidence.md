# A-076 遷移證據

實體筆記本：`/Users/mori/side-project/Lets-go-gps/.agentflow/devlog.md`；Ask：A-076。來源／備份／預定輸出的身分與 SHA-256 見 migration-state.json。原始內容與預定輸出位於 Git 外私有備份，不收錄原始設定或 hooks。

Preflight：branch `main`；status `## main...origin/main`；fetch 成功；origin/main `202580ccb6b9a2f1d61424c1cbb38e8a67936a23`；最近五筆 `202580c`、`17d6d6a`、`0135214`、`bc94c12`、`58a8f37`。origin/main 含 AGENTS.md。

來源形狀已證明為 v8：schema 8、v8 專用 switches 及四層 profile tiers 一致。`.claude/settings.json` 不存在；無自訂 hooksPath；有效 Git guard 是 `.git/hooks/pre-commit`。兩個 Codex 註冊符合舊安裝 install-hook.js 的完整腳本路徑及指令形狀；舊安裝保留於 `agentflow.pre-update-8.4.15-20261028`，原命名路徑腳本已不存在。pre-commit 完整五行吻合已知簽名；無 foreign sibling。沒有舊 owner／plan queue，`.worktrees` 僅剩 Finder metadata。

規範與不變條件：已讀共用 baseline；本專案沒有 local rules、backlog 或 canonical registry。產品規範不受設定遷移影響；不新增或修改保證紀錄。AGENTS 既有規範逐位元保留，只增補啟動及 baseline 指向。

計畫：Claude CLI `claude-opus-5-5/high`，使用 safe-mode、tools 空集合、strict-mcp-config、停用 slash commands、無持久 session；輸入只有非秘密的授權與必要來源描述，於 Git 外無 remote 的 task-owned checkout 執行。exit 0，回覆已由 host 核對。非 OS sandbox，未驗證實際供應模型。規劃原文私存於備份。修正其建議：notebook-name 必須保留完整 `devlog.md`；依 authoritative migration 先無覆寫搬移原始 bytes 再原子改鍵；空 hook event／完整 guard 移除已有技能授權，不新增核准；狀態由 host 直接更新。這是單一設定遷移，不改產品行為，無需產品 design。

發行檢查：GitHub `agfnow/agentflow` latest stable 為 v9.1.0；本次只遷移專案至已安裝 v9.0.0（schema 13），沒有安裝替換。下次檢查日期設為查詢 UTC 日期加兩天。

首次 Gradle 因缺少 MAPS_API_KEY 在配置階段失敗。將以明確佔位值驗證且不交付該 APK；保全並恢復本地既有 Debug APK。

主機驗證通過：單一 canonical config、schema 13、路由、每個模型／effort／command、priority、pipeline-role 與新舊 switch 對應；AGENTS 原始 bytes 保留；既有歷史除授權 input 與 STATUS 以外保留；archive SHA-256 相等；三個 hook 表面均已驗證。config 不受 ignore 規則排除。

Gradle：以環境佔位 Maps key 執行 `./gradlew test lintDebug assembleDebug`，exit 0，BUILD SUCCESSFUL in 1m16s。測試統計 {"tests": 311, "failures": 0, "errors": 0, "skipped": 60}；lint 統計 {"Error": 34, "Warning": 101, "Hint": 3}（沿用既有 abortOnError=false，不宣稱 lint 零問題）。原始 Debug APK 已從私有備份恢復並驗 SHA-256 相同；新產生 `.kotlin/` 快取保全至同私有備份，未入 Git。沒有實機測試；本次未改產品。

退役揭露：`away-gates:on` 改由現行 owner-only 邊界處理；`auto-reply:on` 不再使建議預設值成為核准；`notebook-ownership:off` 改為強制認領；`streams:off` 維持不自動建立（功能需明確 lifecycle 請求）。完整 executor 集合的 `allowed-worker`、`cli-provider:on`、`allow-ag:on`、`review-policy:prefer-independent` 退役，不保留已無效的開關。`large-work-minutes:120` 不再決定拆工／時限、`git-timeout-ms:30000` 不再控制 Git timeout、`completion-cleanup:off`／`completion-cleanup-interval-days:7` 不再控制清理排程或保留期；本次不清理歷史或備份。這些舊值沒有必須另設替代物的額外 standing constraint。

獨立審查：Claude CLI `claude-opus-5-5/high`，safe-mode、Read/Glob/Grep-only、strict MCP、停用 slash commands／session persistence，在 Git 外無 remote 的 snapshot checkout 中完成，exit 0。審查來源為批准的最終非秘密設定／AGENTS／evidence／tracker 快照，沒有傳送原始 config/hooks；實際供應模型未驗證，工具限制並非 OS sandbox。原始回覆見 review.md。兩項紀錄缺陷已修正：tracker T-2 DONE、T-3 DOING，刪除過時 Recovery 句；evidence 與本輪 Reply 補齊退役揭露。host 核對修正，屬狀態／說明更新，產品來源與已審設定未變，不重啟審查。reviewer 未直接觀察的歷史／hook metadata／archive 身分由 host 私有原始與預定輸出比對補足。

本次 hooks 為 ignored 的本機 `.codex/hooks.json` 與 `.git/hooks/pre-commit`，已實際更新／移至 Trash；前者不強制加入 Git。已載入舊 hooks 的其他既有工作階段可能需要重開才能刷新，未宣稱其記憶體設定已更新。`local.properties` 未寫入佔位值；產品來源相對 HEAD 無修改。既有 lint 問題與 POCO 待測不在此次修復範圍。

Git preflight 原始輸出（fetch exit 0，無 stdout）：

```text
$ git rev-parse --abbrev-ref HEAD
main
$ git status --short --branch
## main...origin/main
$ git fetch origin --prune
$ git rev-parse origin/main
202580ccb6b9a2f1d61424c1cbb38e8a67936a23
$ git log --oneline -5 origin/main
202580c 說明Debug APK原始輸出與交付副本位置
17d6d6a 交付含前景服務修復的1.4.3 Debug APK
0135214 記錄前景服務當機修復與PR66交付
bc94c12 記錄POCO前景服務啟動逾時診斷
58a8f37 說明Unity工單提醒與建議結案回覆
```

最終範圍：工作流程設定／AGENTS 最小增補／本輪紀錄；無產品來源改動、無部署、無合併 PR66。沒有 canonical registry 可比較，未建立保證紀錄。筆記本未滿 750 行，不需封存；既有 archive bytes 完全相同。交付來源為已同步 main 的單一設定維護，沒有 feature coding，不建立產品 feature 分支。
