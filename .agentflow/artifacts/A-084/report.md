# 停止模擬後定位恢復：修正結果

已修正，Pixel7Pro／Android17已通過實機回測：開始模擬→停止→按定位，座標顯示更新至非模擬位置，不需要先開Google Maps。

停止流程現在會等待Google確認退出模擬；失敗保留重試狀態並顯示停止錯誤。定位按鈕會要求新位置、拒絕舊mock快取，並處理逾時與權限不足。

[修正版Debug APK](../../../app/build/outputs/apk/debug/letsgo-1.4.3-location-recovery-debug.apk)已建立；版本維持1.4.3，未發布。Pixel原App因簽章不同未被覆蓋，回測使用隔離套件，已移除。

驗證：266項單元測試通過、60項既有跳過；新增15項均實際執行。Pixel另1項裝置測試通過；實際按鈕回測亦確認停止及座標更新。三項Gradle指令成功，獨立審查無剩餘阻擋。lint仍有33項其他Error，原設定不阻擋建置；詳見證據。

SamsungAndroid16仍需朋友回測，不能宣稱該機型已驗證。隔離套件底圖授權受限，因此未驗收完整圖磚渲染；這不影響已取得非mock位置與座標更新的觀察。

[驗證與限制](evidence.md)保存重現、檢查及審查處置；[最終UI回測](ui-smoke.json)不含私人座標。已推送並建立[草稿PR67](https://github.com/moooo-works/Lets-go-gps/pull/67)，未合併或發布；GitHub CI執行中。詳見[追蹤](tracker.md)。先前A077/A082與混合筆記本變更仍保留本機，未混入本次產品提交。
