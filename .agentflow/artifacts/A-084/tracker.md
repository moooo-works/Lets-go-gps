# A-084 修正追蹤

目標：修正停止模擬後舊定位及清理失敗。產品與驗證3/3完成；Git交付執行中。

## Tasks

- [DONE] T-1 — 非同步停止清理、失敗重試、destroy與權限撤銷回歸。AC1／2通過，見[evidence.md](evidence.md)。

- [DONE] T-2 — 要求新位置、拒絕mock、權限／逾時提示、鏡頭動畫期限。AC3通過，見[UI回測](ui-smoke.json)。

- [DONE] T-3 — 三項Gradle、獨立審查、Pixel裝置與真實按鈕回測。AC4在隔離package及座標更新範圍通過；完整圖磚／正式簽章／Samsung不列通過。

## Recovery

分支fix/location-recovery，自origin/main62b9898建立，最後fetch behind0。沒有執行中的測試或review worker；Pixel臨時套件及dump已清理，原App保留。產品與A084將單獨commit/push；混合devlog/archive及A077/A082保持本機，避免吸收舊未交付紀錄。下一步完成Git交付並回讀遠端；如失敗保留本追蹤與實際操作身分，不重跑已通過功能測試。

[修正報告](report.md)、[完整證據](evidence.md)、[最終UI審查](review-ui.md)。
