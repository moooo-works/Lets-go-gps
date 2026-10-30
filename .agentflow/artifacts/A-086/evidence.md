# A-086 證據與重現

## 範圍／身分

使用者授權「進行實機回歸」，並答覆「可以，提醒我解鎖」「已解鎖」。產品基線19bd469，產品來源與A084已審／已測source-final.json全數hash相同；本輪只新增MockServiceDeviceRegressionTest及A086紀錄。

Git preflight：fix/location-recovery；追蹤origin/fix/location-recovery，只有devlog/archive的既有修改與A077/A082/A085未追蹤。fetch成功；origin/main62b9898bf98a78c8f3d1439df5a28c48cf784e8c，最近5筆62b9898／bed4426／202580c／17d6d6a／0135214。主機在開始及交付前都確認main無落後。保留舊混合紀錄，不吸收進本輪提交。

共用project-rules已載入，本機rules／sys-invariants不存在，沒有建立registry或新永久保證。以tracker的AC1–4／資料與安全保護驗收。

## 最終裝置結果

accepted-regression.txt確有12個測試完成碼0，沒有-2失敗或-4assumption；12通過、0失敗／跳過，95.529秒。result-summary.json由原始狀態碼解析，不只看OK行（Android runner有skip時也可能印OK）。含10個一般啟停循環及5組快速停止／開始壓力；後者可拒絕停止中的提前START，但必須能結束並再次正常開始。

- AC1通過：實際Hilt singleton、Service intents、GPS mock值／同一dumpsys解析器正向確認；正常停止後IDLE、服務不存在、provider消失及fresh非mock。撤銷權限時只要求本機服務退出且錯誤保留，恢復權限後再驗乾淨停止。
- AC2通過：路線暫停續播／完成、JUMP、探索與傳送探索實際有非null位置變化，播放狀態適當；不是只測畫面按鈕。
- AC3通過：真實WindowManager overlay、UiAutomation螢幕視窗bounds、TOUCHSCREEN原生拖曳，方向正向、mock座標改變、overlay停止按鈕結束服務。Pro只屬controller fixture，未修改Billing或帳號。
- AC4通過：Home及短時熄屏觀察更新、實際通知PendingIntent停止。安全鎖沒有繞過：人工配合參數開啟後，等待Keyguard回報解鎖才呼叫fresh定位。最後批次亦完成；先前manual-screen-off.txt保存單獨人工配合通過。

背景與熄屏觀察各3秒。定位新鮮度依正式helper的maxAge0／逾時契約及非mock判定，未額外估算GNSS測量年齡；回傳可來自fused／network。沒有開Google Maps。

## 首輪失敗與修正

first-run.txt／service-regression-batch.txt保存最初10pass、搖桿fixture手勢未命中、1安全鎖skip。後續以真實畫面確認搖桿可動，改fixture等待render、讀系統實際overlay視窗bounds，並明確TOUCHSCREEN來源；joystick-regression.txt記錄單獨pass。不改產品來源來讓測試通過。

獨立審查提出兩個P2：nullable位置可被誤算移動、provider解析只有負向檢查。已要求非null且播放中，暫停值必須存在；dumpsys輸出標頭有效且同一解析器先確認mock存在再確認移除。修正後整批12項重跑，包含人員配合安全解鎖，才得到最終12pass。

## Crash與清理

crash-summary.json中8筆近期本包程序退出紀錄，crash／native crash／ANR原因0筆；正常finished instrumentation為USER REQUESTED。Android只保留有限近期紀錄，不將其當全時間保證。整批runner未因產品crash中斷，所有case均完成。

cleanup.json確認兩個臨時套件已卸載、原App APK前後hash相同、無mock override殘留。原App未卸載或清資料；未寫Health Connect、未改Cloud、未更動安全鎖。截圖只留tmp，最終fixture截圖僅作渲染同步後即釋放，不儲存私人位置。

## 建置／審查

最終./gradlew test、lintDebug、assembleDebug皆成功，見gradle-checks.txt；單元來源未變，沿用326項中266通過、60既有skip的有效快取，不冒稱這60項通過。沒有新增產品程式。lint仍有既有發現，abortOnError=false未改。

Claude路由因額度限制exit1，沒有審查結論；依設定回退native gpt-6.1-sol/high，fork_turns=none，實際handle /root/device_regression_review。使用無遠端、無私密設定的獨立快照，指派唯讀且不准adb／工作流／派工，非OS sandbox。首次提出兩P2並補強，最終重新檢查最新source與整批輸出後無阻擋，見review.md。來源hash見source-identity-final.json；reviewer未自行執行手機測試。無獨立provider多樣性聲明。

## 重現限制

測試只允許.recoverytest尾碼的隔離applicationId，正式包名會assume skip。此次以Git外init替換applicationId及對應GoogleServices配置，Maps key只在私有tmp／環境使用，未輸出／進Git；隔離套件底圖未授權，未改Cloud白名單。先安裝隔離APK與testAPK，給定位／通知／overlay及mock app權限，保持前景，執行AndroidJUnitRunner指定MockServiceDeviceRegressionTest；manualUnlock=true只在操作人員可配合時開啟。不要停用安全鎖來換取測試pass。

不外推Samsung、正式簽章安裝、長時間Doze、付款或Health實寫。先前混合筆記本／A077/A082/A085仍保留本機，不在本輪產品PR提交中。
