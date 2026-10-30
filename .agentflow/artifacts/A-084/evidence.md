# A-084 驗證與審查證據

## 身分與範圍

主機Codex規劃／實作；來源A084「pixel 7 pro也測出同樣的問題，表示真的有bug，開始進行修正」。自origin/main 62b9898建立fix/location-recovery，fetch成功、起點behind0。保留工作前devlog/archive修改、A077/A082未追蹤紀錄；未合併PR66、未發布、未改Cloud或新權限。

Git preflight原輸出：branch main；status main...origin/main，M .agentflow/devlog.archive.md、M .agentflow/devlog.md、?? A077/、?? A082/；fetch成功；origin/main62b9898bf98a78c8f3d1439df5a28c48cf784e8c；最近5筆62b9898／bed4426／202580c／17d6d6a／0135214。本輪最後fetch與origin/main比較0 behind。

共用project-rules已載入，rules.md／sys-invariants.md不存在；無新增registry或保證。設計INV與AGENTS既有權限分流、資料／地圖／VM owner約束適用。設計由主機直接完成：既有A082定位的有界修正，無委派規劃。

## 實作與故障回歸

teardown改suspend，mutex封住跨實例清理期間的開始，IO yield保證離開Main，再以Task完成callback等待3秒，不阻塞Main。失敗provider保留，成功項移除；服務重複停止／destroy保護，錯誤透過Teardown分流。權限已知撤銷時保留錯誤並停止本機服務，CheckFailed保持可重試；已知FLP啟用失敗不阻擋provider-only停止。

初回實機發現UNDISPATCHED+相同IO context令Tasks.await仍在Main執行，錯誤Must not be called on the main application thread；已移除阻塞等待。首輪獨立審查B1亦指出同問題，舊報告保留，不作最終通過證據。

定位使用FLP21.3.0的CurrentLocationRequest：maxAge0、15秒、外層16秒，拒絕mock，取消token，權限錯誤單獨提示；動畫2秒後fallback至position setter，不讓未attach地圖的動畫阻擋座標更新。21.3.0需明確依賴，原傳遞依賴19.0.0無此API；cache中的兩版AAR manifest權限集合相同，原app manifest無修改。沒有新增長駐定位服務。

## 檢查

- ./gradlew test、./gradlew lintDebug、./gradlew assembleDebug皆成功，見gradle-checks.txt。使用原裝置APK內既有Maps key，僅在私有tmp／子程序環境傳遞，未輸出或進Git。最終APK為正常applicationId，非佔位key或isolatedpackage。
- 326項、60既有skip、0failure/error，即266通過。新增engine5／service4／fresh5，加VM1共15項全部實際執行。原SDK33skip沒有掩蓋新測試。test-summary.json存總數。
- lint指令成功不代表0發現：仍33Error／103Warning／3Hint，原abortOnError=false。新FreshDeviceLocation的MissingPermission已以明確SecurityException處理消除；其餘為原有API／權限、Compose、翻譯等問題，不在本輪重構。
- git diff --check通過。

## Pixel實機

Pixel7Pro／Android17。原App是不同簽章，不覆蓋／不卸載、不清原資料。以同產品來源、不同applicationId recoverytest測試；Google Maps先force-stop。未擴張Maps key白名單，隔離package圖磚空白，不能聲稱完整圖磚渲染驗收。

1. 手動開始→停止：確認gps/network mock provider啟用後被移除，UI回到開始按鈕。
2. 裝置instrumentation實際1項通過、0skip，見device-test.txt：啟用FLP／注入→從Main以IO scope UNDISPATCHED進入正式engine清理→正式freshDeviceLocation取得非mock位置。測試直接engine，不把它冒充完整Service intent測試。
3. 最終UI操作回測：由真實按鈕開始，dumpsys正向確認mock；按停止後UI開始按鈕且mock override清除；按定位後座標顯示改變，fused last fix為非mock。全程沒有開Google Maps，見ui-smoke.json（不含私人座標）。此項補足前項的Service intent與FAB座標更新；圖磚渲染仍不在證明範圍。
4. 測試套件／instrumentation套件已卸載，臨時UI dump清理；原App APK前後SHA256相等。私有截圖／原始位置只留tmp，未入Git。

SamsungAndroid16無裝置，不宣稱已實測。正式簽章安裝未實測；隔離package的結果是共通邏輯證據。

## 獨立審查與處置

配置external counterpart CLI：claude-opus-5-5/high，safe-mode、tools空、strict-mcp、no-session-persistence，輸入非敏感source文字／設計／project-rules；沒有檔案工具，故不需可變clone。非OS sandbox，實際模型執行身分以CLI可觀察範圍為限。各次CLI exit0並有實質報告；主機核對來源hash，不假稱reviewer跑過測試。原始handle29635、39202、97686、52520皆已結束。

- review.md：舊snapshot提出B1阻擋；實機同樣重現，已改callback+yield。M1記錄FLP enable Task、M2撤銷權限終局錯誤、M3權限toast及取消提示、destroy重複清理皆已修正並回歸。
- review-final.md：無可確認阻擋，R1要求盤點呼叫點。host盤點所有ACTION_STOP／stopMocking／stopService路徑：UseRoute等待IDLE後只載入路線，沒有自動START；其他STOP路徑無連續START。R1依條件關閉。
- review-device.md：新增實機測試無阻擋；揭露它不經Service intent／UI鏡頭。最後UI-smoke另補實際intent與座標顯示，仍不擴張為圖磚或Samsung通過。
- review-ui.md：最後動畫timeout/fallback差異無阻擋。其後FreshDeviceLocation僅把原已處理的SecurityException由泛型catch拆成顯式catch，以便lint辨識，結果語意不變；主機複核並重跑三項檢查通過。

INV1停止／destroy、INV2失敗／權限分流、INV3新鮮非mock／失敗提示、INV4既有資料／VM／marker政策均通過：以新回歸、Pixel、靜態差異與審查證據支持。AC1–3通過；AC4在Pixel隔離package共通流程及座標顯示範圍通過，完整圖磚／正式簽章／Samsung不在已證明範圍。沒有新保證提升至registry。

## 交付界線

產品與A084可安全單獨交付。devlog／archive混有A077未通過套件診斷及前輪本機紀錄，不把它們混進本次產品commit；A077/A082及混合筆記本保留本機，後續另行處理。A084設計已保留本輪精確授權與結果，避免靠未提交筆記本重建工作。不得把這個界線描述為整個workspace clean。

產品與本輪證據已分離提交／推送，草稿PR67已建立，GitHub compare behind_by=0。Android CI run40994907361執行中；未要求合併、未發版。這次收尾更新保存實際PR交付與審閱狀態，不重跑未變動的產品檢查。
