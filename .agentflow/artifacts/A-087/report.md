# 1.4.4 發布與清理結果

1.4.4（10404）已確認在 Google Play 正式軌道「審查中的變更」；100%全面推出、控管型發布關閉，審查通過後自動發布。目前尚未上線。[Play回執](play-in-review.jpg)與[發布資料](publication.json)可核對版本及狀態。重新開啟發布頁時已顯示審查中，因此未重複提交；回報為已觀察狀態，不臆測送出者或時間。

PR67與PR66均已合併，產品整合來源7c7132b，main合併commit de3565f。PR的CI41015448589成功；合併後app/gradle等產品樹與正式包來源相同。[Git回執](git-delivery.json)與[preflight](git-preflight.txt)保留細節。先前未提交的技能遷移、定位調查及風險／回歸紀錄已另以4284c76保存，未混入產品修正。

清理已完成：刪除約8.18GB配置空間的可重建檔案，當下磁碟可用量由4.37GB增至12.62GB（增加8.25GB，其他系統活動及APFS會影響即時值）。內容為未使用Gradle8.8／8.11.1／8.14快取、專案build／本地建置快取及重複測試暫存；活躍8.13／9.3.1、共用modules-2／wrapper、設定、簽章、Git及所有工作紀錄保留。[逐項清理清單](cleanup.json)列出原始位元組與保留項目。Finder在刪除build期間重建.DS_Store造成一次中斷，檢查只有該中繼資料後完成清理，沒有持續建置或使用者資料被移除。

正式AAB、mapping、原Play10403 base APK副本及來源／SHA資料保留於 `/Users/mori/Downloads/Lets-go-gps-release-artifacts/1.4.4/`。AAB SHA256為 `e4a1178fb5e24681d83f8bd0028c6694b2aab38c0d77dcb07436f0bf4199b627`；[正式包核對](package.json)包含版本、有效簽章與production設定。原App及資料未刪除，測試前後原APK指紋相同；APK副本只含安裝包，不冒稱使用者資料備份。

驗證：335項單元測試中275通過、60既有跳過、0失敗；test／lintDebug／assembleDebug／bundleRelease成功，新版Pixel7Pro Android17實機11/11通過、0失敗／跳過。獨立審查找到的權限變更後空轉FGS缺口已修正並重驗，詳見[審查與處置](review.md)、[驗證資料](validation.json)及[實機原始結果](device-regression.txt)。lint命令成功但仍有既有33 Error／103 Warning／3 Hint；Play另有未上傳native symbols的非阻擋警告，Kotlin ReTrace mapping已包含。Samsung、長時間Doze、付款、Health實寫、完整Map四項UX與本輪人工解鎖熄屏未完整重驗；不保證所有裝置零crash。

本輪執行與交付已完成；Google審查與實際上線仍由Play處理，沒有排程背景監看或承諾代替Google核准。
