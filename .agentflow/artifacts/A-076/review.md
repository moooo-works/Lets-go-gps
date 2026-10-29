# 最終結果審查（A-076：遷移至 v9）

**結論：有條件通過。** 遷移本身符合 owner 目標，也夠精簡：只改設定與 hooks，AGENTS 只增補必要內容，沒有改到產品範圍。commit 前要先修 2 個缺陷，並等 Gradle 結果出來。

## 必修缺陷

1. **tracker 狀態落後，而且前後矛盾**（`tracker.txt:3,13,23`）
   - host 驗證已經確認 STATUS 投影完成，歷史也逐位元相等，但 T-2 還標著 `[DOING]`，進度寫 1/3。
   - Recovery 先寫「已套用 hooks、設定搬移及 STATUS 投影」，結尾卻又寫「待完成設定／掛鉤變更後驗證並交付」。這句已經過時，會誤導之後接手復原的人。
   - 修正方式：把 T-2 改成 `[DONE]`、T-3 改成 `[DOING]`、進度改成 2/3。Recovery 改寫成「Gradle 驗證與獨立審查進行中」，並刪掉那句過時的話。

2. **退役行為完全沒有揭露**（`evidence.txt` 和 tracker 都沒寫）
   - 授權要求 large-work-minutes 120、git-timeout 30000、cleanup off、interval 7 退役時「須揭露」。
   - away-gates:on、auto-reply:on、ownership off、streams off 的行為也改變了，同樣沒有記錄。
   - 修正方式：在 evidence 加一行精簡揭露，不要貼原始設定。內容涵蓋：
     - away-gates 改為只由目前 owner 核准；
     - auto-reply 不再自動核准；
     - claim 現在是強制的；
     - streams 不會自動建立；
     - allowed-worker、cli-provider、allow-ag、review-policy 已退役；
     - 上面四個數值設定已退役。
   - 最後給使用者的回報也要有同樣內容。

## 交付前的條件（不算缺陷）

- **Gradle 驗證**：`test`、`lintDebug`、`assembleDebug` 三項都過了才能 auto-commit 或 push。失敗的話就如實回報，不要 commit。
- **佔位 Maps key**：只能透過環境變數傳入。確認它沒有寫進 `local.properties` 或任何會被追蹤的檔案。驗證產生的 APK 不要交付，原本的 Debug APK 要還原並核對 hash。
- **commit 內容**：只放 `.agentflow/ag.json`（同時移除根目錄的 `ag.json`）、`.codex/hooks.json`、`AGENTS.md`、STATUS、tracker 和 evidence。私有備份、原始 hooks 和原始設定都不能進 Git。
- **給使用者的最終通知**：
  - 已安裝版本仍是 9.0.0，目前有 9.1.0 可用，這次沒有替換安裝；
  - Gradle 用的是佔位 key；
  - 規劃模型實際用了哪一個沒有驗證，執行環境也沒有 OS sandbox。

## 已確認符合

- `schema-version` 13。switches 的對應全部正確：omit-asker、omit-run、omit-wip 都是 off；use-lang 是 zh-tw；notebook-name 是 `devlog.md`；ag-dir 是 `.agentflow`；saving-mode 已新增且為 off。退役的鍵都沒有殘留。
- cross-check 已併入 acceptance，threeways 維持 better。
- model-family 已經從 profile tiers 移到共用位置，openai 和 claude 各自對應正確；兩個 worker 的 priority 都是 3，對應後不變。`next_update_date` 是 2026-10-31，等於查詢日加兩天。
- AGENTS 的增補只有兩段：技能啟用，以及共用規則指向。`.agentflow/rules.md` 寫的是「若存在」，沒有替它建立 scaffold，也符合 AGENTS 第 9 節「最小新增條款」的要求。
- evidence 沒有收錄原始設定或 hooks；hooks 的身分驗證、preflight、release check 和規劃修正都有記錄。
- 只用一個 tracker、三個 task，以單一設定遷移來說夠用。沒有做產品設計，也沒有動 canonical registry。

## 審查限制

我只讀了四個快照檔。`migration-state.json`、STATUS 和 devlog 的實際內容、hooks 外部 metadata，以及 archive 的位元相等，我都沒有直接檢查，這幾項依賴 host 的驗證結果。另外，tracker 裡連到 `migration-state.json` 的相對連結，我沒辦法確認在 Git 裡是否有效。
