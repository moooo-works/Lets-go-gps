# 結論：目前不可結案，屬「有條件通過」

安裝替換與 hook 處置的方向正確，處置也夠小：沒有加 no-op 腳本，沒有全面停用 hooks，也沒有還原 v8。交付前仍須補齊以下事項：

1. **官方 npm test 結果**：目前還沒有結果。
2. **Hook 文件來源**：來源身分有疑慮。
3. **Shared daemon 與重開範圍**：可能影響「重開即可刷新」的推論，需要確認。

## 阻擋項（交付前必須處理）

1. **npm test 尚未完成。** T-3 還是 DOING，證據裡沒有 exit code，也沒有輸出摘要。測試通過前，最終報告不得寫「套件檢查通過」。若測試失敗，需原樣回報輸出，不可只以雜湊相等代替。
2. **Hook 文件來源身分未驗證。** `https://learn.chatgpt.com/docs/hooks` 不是我認得的 OpenAI Codex 官方文件網域。「config 各層、inline 與 plugin hooks 會累加」這個論點目前建立在它上面。需改引用可驗證的官方 Codex 文件或原始碼。做不到的話，就降級為「未經官方來源確認的說明」。

## 缺漏檢查（應補，或在報告中明示為限制）

3. **Shared daemon 可能讓重開無效。** 證據寫到 shared-daemon proxy 無法 initialize。若既有 TUI 是掛在常駐 daemon 上，只退出並 resume TUI，daemon 可能仍保留舊的 hook 註冊。需確認 TUI 是否經由 daemon 執行。確認不了的話，交接要補一句：重開後仍出錯時，可能需要一併結束 daemon。
4. **Fresh app-server 與既有 TUI 的等價性未記錄。** 需確認兩者使用相同的 Codex binary 版本、相同的 `CODEX_HOME` 與環境變數、相同的 cwd 與專案信任狀態。否則 `hooks/list` 為空，只能證明「該隔離環境」沒有 hook。
5. **設定層覆蓋不完整。** 目前只查了 user 層和 project 層。還沒查 managed/system 層的設定，也沒查上層目錄的 `.codex`。plugin 也只查了「已知」的 manifests。fresh `hooks/list` 可以部分補足，但前提是第 4 項成立。
6. **v9.1.0 是否預期註冊 hook？** 新版安裝說明若要求註冊某個 hook（例如 stop／devlog guard），那「hooks 全空」反而代表少做了一步安裝，而不是修好了。需確認 README 沒有這類要求，並寫進證據。
7. **可重現性紀錄不足。** 證據沒有記下 tag 對應的 commit SHA 和 tarball 的 SHA256。`npx skills` 也沒有固定版本，實際執行的 CLI 版本應該記錄下來。

## 交付與交接

8. **Git 交付的範圍。** commit 只能包含 `.agentflow` 下本輪的紀錄。不得帶入私有備份路徑內容、原始 hooks/config，或 state.json 中的敏感入口。push 前要確認 diff 範圍。
9. **使用者交接要寫完整，至少包含以下內容：**
   - 在本專案目錄退出目前的 Codex。若第 3 項成立，也要結束 daemon。
   - 執行 `codex resume 01a186e9-8f4d-7ea3-8608-debc0406351d`。session id 需與 state.json 核對一致。
   - 重開後完成一個回合，觀察是否還出現 Hook failed。
   - 若仍出錯，回報 hook 名稱和完整輸出，以便歸因。
10. **措辭要誠實。** 報告須明示三點：
    - 原錯誤的根因未知。
    - 「舊工作階段快取」只是推論。
    - 既有 TUI 中錯誤是否消失尚未驗證。

    同時註明產品驗證是沿用 A-076 的 test/lintDebug/assembleDebug，本輪沒有重跑。目前 tracker 在這幾點上大致符合，最終報告要保持同樣的措辭。

## 已確認無問題

- 授權明確，兩份安裝都已私有備份，舊目錄也保留。
- 兩份安裝逐檔比對 SHA256 都與 tag 相符，tarball 無路徑逸出，也沒有 symlink。
- 只排除了 Finder metadata，這屬於合理排除。
- schema 13 不需要轉換，既有選項和 `next_update_date` 都保留了。
- 產品碼沒有變更，也沒有覆寫 APK。
