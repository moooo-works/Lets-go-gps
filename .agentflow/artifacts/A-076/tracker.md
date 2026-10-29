# A-076 — 專案遷移至 Agentflow v9

目標：將此專案的 v8 設定與掛鉤遷移至已安裝的 Agentflow 9.0.0／schema 13，保留模型設定及開發歷史。進度：3/3。

## Tasks

- [DONE] T-1 — 退役舊掛鉤並搬移設定

  - 來源：使用者「遷移至v9」；邊界為根目錄設定、兩個專案 hook 表面及 AGENTS.md 最小增補。

  - 驗收：單一 `.agentflow/ag.json`、schema 13；模型／effort 完全保留；只移除已證實屬於 Agentflow 的掛鉤。

- [DONE] T-2 — 更新狀態並驗證歷史

  - 筆記本與封存檔維持原路徑；既有歷史逐位元保留；Current 保留 PR66／POCO 待測線索。

- [DONE] T-3 — 完成檢查、審查與交付

  - 檢查 JSON、保留選項、hook 外部內容、歷史相等及 Git 差異；依專案要求執行三項 Gradle 驗證，再做獨立審查。

## Recovery

更新：2026-10-29 17:16:38 +0800。三項任務已驗證完成，獨立審查的兩項紀錄問題已修正；沒有待決核准或執行中的程序。原始 Debug APK 已恢復。此輪接著執行設定紀錄的 commit／push；若交付失敗，回復入口仍為此 tracker 及 migration-state.json，應先核對遠端 tip，不重做遷移。

保留既有外部待辦：PR66／POCO 實機驗證（本輪未授權執行或合併）。

證據與回復路徑：[migration-state.json](migration-state.json)、[evidence.md](evidence.md)、[獨立審查原文](review.md)。
