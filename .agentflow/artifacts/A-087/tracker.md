# A-087 追蹤

目標：新版上架、Git 合併並釋放可安全回收空間。狀態：DONE，4/4。

## Tasks

- [DONE] T-1 — 整合兩個定位服務修正與版號

  來源：A-087。範圍：PR66/67、app/build.gradle.kts、更新說明。依據：[設計](design.md) INV-1/2、AC-1/2。先核對 Play，再整合衝突，失敗不發布。證據待寫 validation.json。

- [DONE] T-2 — 驗證、獨立審查與 Git 合併

  來源：A-087 git合併。範圍：相關測試／CI／PR。INV-1/2、AC-2/3。必跑 test/lintDebug/assembleDebug/bundleRelease；behind=0，保留既有紀錄。失敗回到 T-1，不合併。

- [DONE] T-3 — 正式包送審

  來源：A-087 上架。範圍：簽署 AAB、四語說明、Play 正式軌道。INV-2、AC-1/3。先確認合併產品樹相同及產物身分，再提交；未知結果先查現況，不重複發布。

- [DONE] T-4 — 清理可重建產物並交付

  來源：A-087 釋放空間。範圍：專案 build 與 GPS 本任務暫存，具體清單盤點後寫入。INV-3、AC-4。先保留正式產物／mapping，再刪可重建檔；沒有把握則保留並報告。

## Recovery

2026-10-30T15:17:20.523831+08:00：4/4完成，證據見[完整報告](report.md)。PR66/67均MERGED，main產品樹與AAB來源一致；Play正式版10404審查中，控管型發布關閉、通過後自動推出，尚未上線。清理8.18GB配置空間，當下可用12.62GB。無執行中子agent或mutating程序；待完成本輪紀錄commit/push及claim釋放後交付。僅Google審查是外部等待，不建立背景監看。先前A077恢復入口保留，非本輪產品工作。

INV-1：新單元測試與Pixel11/11通過。INV-2：AAB/production設定/有效簽章及Play接受key、原App指紋不變。INV-3：刪前保留AAB/mapping/APK並查無使用舊cache，逐項刪除及空間計量完成。AC-1..4均有報告鏈結與原始回執；Google尚未核准之界限明示。

驗證例外：PR66歷史A073/review-brief.md八個既有空白行尾保留原始bytes，不為本次記錄改寫歷史；本輪產品與A087路徑diff --check通過。舊notebook所有非空歷史行均仍在live或archive，archive原前綴逐byte不變；前次紀錄與本輪結果分開commit。
