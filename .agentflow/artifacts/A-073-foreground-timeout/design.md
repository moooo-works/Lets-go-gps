# 前景服務啟停修復

原始授權：有辦法修復嗎？可以的話就開始進行修復，可以跟claude code討論

已確認服務使用無條件stopSelf，且背景注入失敗會和主執行緒的新開始競爭。Android在等待前景提升時銷毀服務會拋同類例外；尚未證明POCO原工作階段的精確觸發。

範圍：只修改MockLocationService的停止順序、啟動編號與背景停止序列化，以及必要的SDK28 Robolectric回歸測試。保留通知在合法開始命令內提升；不改權限、設定頁、付費、恢復政策或其他runBlocking。

做法：每次onStartCommand記住startId。停止使用stopSelf(startId)，以免丟棄系統已收到但尚未交付的新開始；停止請求與route completion切回主執行緒，對比預期startId，舊工作不清理新session。先請求系統停止才發布IDLE。注入保留背景執行，但擷取其開始時的startId供失敗回報使用。onDestroy只做清理，不重複發出停止指令。

正常流程：使用者按開始，服務先顯示前景通知再啟動定位；按停止後通知與模擬結束。若立即再開始，新開始必須保留；舊背景工作的失敗或完成回呼不可清掉新工作。

## Invariants

- INV-1：停止命令處理時，系統若已有更晚的開始，停止僅使用已交付的編號，不能無條件銷毀；使用無編號stopSelf即失敗。
- INV-2：背景錯誤屬於舊startId時，新命令已處理後不得移除新通知、改新狀態或停止服務；主執行緒處理並檢查編號。
- INV-3：合法開始仍先提升前景才setup定位，停止／暫停本身不提升；以生命週期回歸測試驗證。

## Acceptance criteria

- STOP startId=2，shadow的停止編號必須是2，不是無條件-1（INV-1）。
- 舊背景stop排隊後接收新命令，排程執行後不能呼叫stopSelf或將新狀態清空（INV-2）。
- 正常背景stop先排主執行緒，完成後保留原停止結果（INV-2）。
- 開始提升成功早於mockEngine.setup；不將通知改到onCreate（INV-3）。
- ./gradlew test、lintDebug、assembleDebug成功；列出既有skip/lint限制。Claude獨立審查最終commit。無POCO可用時不能宣稱原裝置重現通過。

## Minimality check

只改服務停止的必要順序與所有權，復用系統startId及Main Handler。已考慮onCreate全面提升與全面lazy注入：前者會把停止／暫停也提升且可能違反背景限制，後者無足夠延遲證據，均不採用。測試使用SDK28避免現有SDK33 legacy資源被跳過，不全域更動測試環境。

Gate：away-gates on可在實際證據通過後供應Design Go / Result Go；不自動合併或上架。
