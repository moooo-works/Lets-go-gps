# A-077 證據

Ask A-077，實體 notebook `/Users/mori/side-project/Lets-go-gps/.agentflow/devlog.md`。主 agent claim session／私有備份入口見 state.json。

Preflight：`main`；`## main...origin/main`；fetch exit0無stdout；origin/main `62b9898bf98a78c8f3d1439df5a28c48cf784e8c`；最近五筆 `62b9898`、`bed4426`、`202580c`、`17d6d6a`、`0135214`。工作目錄最初乾淨。專案維護範圍只有本輪紀錄，無產品 coding。

使用者已明確要求升級至 v9.1.0，具安裝替換授權，不需重問。GitHub 官方 tag `agfnow/agentflow/v9.1.0` tarball staged於 Git 外私有備份；SKILL metadata／package.json皆9.1.0，schema仍13。來源無 tar 路徑逸出或 symlink。兩份原安裝／原工具 lock／global及project hooks 均已私有備份；未公開原始設定。安裝原工具 lock 指向 agfnow/agentflow；以 `npx skills add https://github.com/agfnow/agentflow/tree/v9.1.0 --skill agentflow --global --agent codex --copy --yes` 更新 managed `.agents`。工具未更新既存 `.codex` 副本；確認它未變更、不是 symlink/source checkout，將 staged 完整樹原子換入，舊目錄另保存。兩個最終安裝排除 Finder metadata 後，所有相對路徑及檔案 SHA256 皆與官方 tag完全相同。備份與 release 均保留。

新 v9.1.0 規範已讀，這是目標與依賴明確的低風險安裝維護，host直接規劃；沒有新增產品設計或 system invariant。共用baseline沿用已讀版本；本專案 canonical registry/local rules仍不存在。不新增 scaffold。

Hook調查：目前 `~/.codex/hooks.json`、project `.codex/hooks.json` 均只有空 hooks；未變更它們。無 project config.toml；user config未含inline hooks。官方 OpenAI Docs https://learn.chatgpt.com/docs/hooks 說明各config層、inline及plugin hooks會累加；針對已知 plugin manifests 未找到 stop-hook/devlog-guard命令。SQLite診斷與本 thread transcript未找到可歸因的Hook事件／stack，不將截圖exit1直接當成已證明的module-not-found。

使用安裝Codex產生實際protocol schema，啟動新 `codex app-server --stdio`，initialize成功；對實體專案呼叫hooks/list回覆 hooks=[]、warnings=[]、errors=[]，程序已終止。這驗證新載入的設定不再含可執行hook。既有宿主TUI尚未重啟，快取舊註冊屬合理推論；不聲稱已觀察原錯誤完全消失。未還原v8 hook、未加no-op腳本、未廣泛關閉hooks。查詢shared-daemon proxy未能initialize，未執行變更，改用隔離的fresh app-server讀取驗證。

重新開啟對話為官方Agentflow安裝說明所要求：https://github.com/agfnow/agentflow/blob/v9.1.0/README.md 。使用者可退出此Codex後在本專案執行 `codex resume 01a186e9-8f4d-7ea3-8608-debc0406351d`；不能在自己尚在執行中強制關閉宿主。新版本下次新載入工作階段可用。

專案config schema13已完整且無需轉換；保留既有選項及future next_update_date，不做重複release scheduling。產品來源未變；沿用A076已通過test/lintDebug/assembleDebug，這輪驗證改為官方skill套件測試，不重建或覆寫APK。

審查：獨立 Claude CLI `claude-opus-5-5/high`，safe-mode／tools-disabled／strict MCP／無session persistence，在Git外無remote的disposable checkout完成，exit0。僅提供非秘密證據／完整任務邊界，不傳原始hooks/config。實際供應模型未驗證，非OS sandbox。回覆見review.md。

處理審查發現：套件測試尚在進行，保留未通過狀態。reviewer對learn.chatgpt.com網域的疑慮不成立：本機OpenAI Docs技能明確列它為官方來源，已實際開啟官方Hooks文件；保留引文來源而不將reviewer記憶當權威。所有適用config layer的加載結果以fresh hooks/list實測補足，沒有修改managed設定。上層`/Users/mori/side-project/.codex`及root未存在hook/config候選，home已查；未將局部文件搜索說成全機完整掃描。

Fresh-loader等價性：執行相同`/Users/mori/.local/bin/codex`（codex-cli0.165.0）、相同繼承環境與專案cwd；沒有傳-c config override／hook disable flag，CODEX_HOME環境未設（採同使用者default），未修改trust。既有宿主祖先為pid2245同一路徑Codex、父bash與cmux，未見app-server daemon祖先。`codex app-server daemon version`及proxy均指向缺少的control socket，沒有可重啟的shared daemon可被此CLI查到；不應關閉其他未證實相關的Codex程序。若重開後仍發生，需取得實際hook名稱／完整輸出，不能以無證據的daemon restart取代歸因。

官方v9.1.0為host-driven工作流程，README及技能不要求Stop/devlog-guard hook，hooks空是正確設定，無需還原腳本。套件管理器skills CLI版本1.7.4；官方tag物件75ae9bc578231aad169e3f558a3df87ae7d97f99，tarball SHA256 bccb0ca823c5f3df5b943d7952bea018c7b7ddcbff6874b9ee3c33d41fe5f49c。備份路徑與session UUID是非秘密回復metadata，保留在state.json；不納入任何備份原始內容／token／credential。讀取schema及protocol結果不依賴雲端模型執行。

首輪npm test出現feature測試失敗，已用單一失敗測試重現：預設`/opt/homebrew/bin/git`2.34.1不支援`worktree list --porcelain -z`。系統`/usr/bin/git`為2.50.1；以單次程序PATH優先系統Git重跑官方完整suite，未改全域PATH／Git安裝／官方skill來源。不能宣稱原始預設環境已通過或新feature CLI對Git2.34.1相容；host-driven筆記本與本輪hook處置不依賴該feature命令。這是額外環境限制，須在最終結果揭露。

系統Git重驗單一feature測試仍失敗，原因為macOS `EPERM: operation not permitted, access /Users/mori/.Trash`，堆疊為feature-common preflightClaimDisposal；不繞過OS隱私權限制，不修改官方來源。完整suite仍在進行，不能標示全套成功。v9.1.0 annotated tag對應commit `6c3537debc38e207da24ab6f25dcacbe98051541`。

首輪套件診斷完整結束：exit1，305tests／234pass／71fail／0skip，285.977s。已觀察Git2.34.1的-z不支援，另有native Trash測試失敗；不是產品App測試。系統Git完整重驗仍在進行。安裝完整性與fresh hooks/list已驗證，但不以這些替代套件測試成功。

系統Git完整重驗結束：exit1，305tests／239pass／66fail／0skip，436.758s。多數feature失敗可歸因macOS對`.Trash` accessSync的EPERM，native Trash整合測試亦失敗；不把所有失敗無條件等同同一原因，也不宣稱套件全通過。原始完整輸出私存於state.json所指備份下npm-test-system-git.log。兩輪suite及兩個diagnostic app-server／reviewer均已結束，無task-owned執行中程序。沒有為測試修改OS隱私權、全域Git／PATH或官方套件。

審查結論處理完成：來源網域疑慮已查證回覆；tag／commit／tarball／工具版本及fresh-loader與宿主等價性已補齊；缺少shared daemon socket，不能要求關閉不相關程序；原TUI尚未重開，runtime錯誤是否消失保持未證明。測試確實失敗，未改寫為成功，原始官方套件保持不變。這些限制留在Reply與Current／tracker。未通過套件檢查前不進行本輪紀錄commit/push，保留精確paths供下一輪恢復，不默默放寬檢查。

本輪待交付paths：`.agentflow/devlog.md` 及 `.agentflow/artifacts/A-077/{state.json,tracker.md,evidence.md,review.md}`。沒有暫存index、沒有本輪local commit；origin/main仍為62b9898，本輪Git外安裝更新不屬專案commit。恢復時先確認這些來源／claim／遠端，避免吸收其他工作。使用者重開原thread後才能確認原畫面，若仍失敗需hook名稱及完整輸出。
