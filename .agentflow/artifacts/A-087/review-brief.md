# 發布前審查資料

原始授權：改版號後上架，git合併，然後清理多餘資料，釋放出空間。請讀設計所有 INV-1..3、AC-1..4；現階段審查發布／清理前的具體結果與操作邊界，不把尚未執行的送審／刪檔稱完成。

來源：HEAD2a59fac + 正在合併 origin/fix/mock-service-foreground-timeout e609bb8；source-identity.json 固定本輪產品修改。原PR67已有定位清理／freshGPS修正及12/12 Pixel證據；本輪加入PR66 startId/Main保護、Main非同步清理、清理期間保存最後START並重播，後續STOP取消重播。測試334/0fail/60既有skip，新增加阻塞teardown重播及取消；完整建置進行中，稍後提供結果。請看實際程式，不僅相信本文。

版號由Play最高10403升10404/1.4.4。local.properties已具正式簽章設定，Mapskey私密注入；勿讀或輸出secrets。四語說明fastlane...10404.txt。上傳前須核實正式包manifest、signature、releaseBuildConfig（DEBUG/DEV_FORCE_PROfalse、正式廣告）、Gitbehind0+CI+合併產品樹；正式送審而非聲稱上線。

清理清單擬：app/build、build、.gradle專案快取（先保留最新AAB與mapping到專案外持久release目錄）；/private/tmp/gps-a082/84/86/87本任務重複APK／reviewcopy／logs已保存證據後刪除。保留Mapskey及隔離init/config至後續驗證結束，保留一份原PlayAPK恢復副本；任何未確認唯一資料不刪。共用Gradle caches8.8、8.11.1、8.14為可重建歷史版本；刪前證明沒有對應活躍daemon/編譯及不刪活躍8.13／目前9.3.1、modules-2、wrapper。保留.git、工作紀錄、簽章、local.properties、所有使用者資料。量測實際檔案與磁碟空間，APFS延遲如實記錄。

請獨立檢查整合競態、scope/minimality、每個INV/AC可否成立，並列出阻擋與可沿用證據／未證明部分。不能新增需求或自行發布刪檔。
