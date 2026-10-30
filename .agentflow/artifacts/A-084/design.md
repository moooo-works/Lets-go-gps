# 停止模擬後恢復定位

來源：A-084，使用者授權修正 Pixel 亦重現的定位恢復問題。主機直接規劃：問題與路徑已由 A-082 定位，不委派規劃。保留既有本機紀錄；不合併PR66、不發布、不清資料。

流程：服務停止注入→背景等待engine清理→成功才結束正常停止；失敗保留狀態並顯示engine錯誤，允許再按停止。已確認權限撤銷時，保留清理錯誤並結束本機注入服務，避免永久重試；CheckFailed不視為撤銷。已知FLP啟用失敗的provider-only裝置，不以同樣不可用的FLP退出阻擋停止。engine逐項保留失敗provider，等待FLP退出Task並設timeout。定位按鈕請求新位置，拒絕mock，顯示等待／失敗。

## Invariants

- INV-1：停止過程不能再注入或接受新開始；onDestroy不能取消尚在執行的清理。
- INV-2：清理失敗不能標記成功或清掉失敗provider；錯誤不導向mock app設定。
- INV-3：回到目前位置不能使用舊mock座標；查無新位置／權限錯誤可辨識。
- INV-4：NavHost／VM owner、地圖準星／marker／儲存點、資料與權限政策不變。

既有sys-invariants.md不存在；本輪不新增永久registry，以上作為修正契約。

## Acceptance criteria

- AC-1：A082兩項故障注入在修正後通過；FLP Task等待成功、失敗、timeout有測試。
- AC-2：停止清理不阻塞主執行緒；重複停止不平行清理，失敗可再停止。已完成的舊服務destroy不清掉新session；已撤銷權限保留錯誤並退出。
- AC-3：定位請求maxAge=0、有期限；null／mock／失敗不更新鏡頭且顯示失敗。
- AC-4：三項Gradle檢查及獨立審查；Pixel開始→停止→按定位，不依賴Google Maps刷新。Samsung無裝置，不聲稱該機實測。

## Validation

有效SDK28隔離Robolectric測engine，純協程測狀態／新定位政策；既有SDK33跳過問題僅揭露，不大改全套測試。執行test、lintDebug、assembleDebug。佔位key不安裝；實機建置前從既有已授權本機來源取得真正key且不輸出。

## Minimality check

不引入新定位常駐服務或新權限；沿用現有FLP／service／error dialog。介面只將teardown改為suspend，使服務背景等待；定位按鈕使用單次有期限請求。保留清理失敗後服務讓使用者重試，避免虛假停止成功。
