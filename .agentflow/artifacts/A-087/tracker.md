# A-087 追蹤

目標：新版上架、Git 合併並釋放可安全回收空間。狀態：DOING，1/4。

## Tasks

- [DONE] T-1 — 整合兩個定位服務修正與版號

  來源：A-087。範圍：PR66/67、app/build.gradle.kts、更新說明。依據：[設計](design.md) INV-1/2、AC-1/2。先核對 Play，再整合衝突，失敗不發布。證據待寫 validation.json。

- [DOING] T-2 — 驗證、獨立審查與 Git 合併

  來源：A-087 git合併。範圍：相關測試／CI／PR。INV-1/2、AC-2/3。必跑 test/lintDebug/assembleDebug/bundleRelease；behind=0，保留既有紀錄。失敗回到 T-1，不合併。

- [TODO] T-3 — 正式包送審

  來源：A-087 上架。範圍：簽署 AAB、四語說明、Play 正式軌道。INV-2、AC-1/3。先確認合併產品樹相同及產物身分，再提交；未知結果先查現況，不重複發布。

- [TODO] T-4 — 清理可重建產物並交付

  來源：A-087 釋放空間。範圍：專案 build 與 GPS 本任務暫存，具體清單盤點後寫入。INV-3、AC-4。先保留正式產物／mapping，再刪可重建檔；沒有把握則保留並報告。

## Recovery

2026-10-30 14:53 +0800：T-1完成，T-2四項Gradle／獨立審查及Pixel新版11/11通過。AAB/mapping已保留外部release目錄。待最後測試檔註解校正後lint與正常debug重建，然後commit/push、CI及合併。活躍程序post-device-checks（由工具session追蹤）；未刪任何build/cache，Play草稿release43已建立但未上傳。原生reviewers已完成。

驗證例外：PR66歷史A073/review-brief.md八個既有空白行尾保留原始bytes，不為本次記錄改寫歷史；本輪產品與A087路徑diff --check通過。
