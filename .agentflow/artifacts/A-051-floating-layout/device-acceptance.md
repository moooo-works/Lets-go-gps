# 懸浮工具與搖桿接受紀錄

產品固定：85ca56486538913ff64cf17e86d75eace4173579；獨立worktree `.worktrees/floating-dock`，base為PR61產品2d8e3ebdbb6aded2ac4ebdf5e0d85d604a4cf5dd。root main只保存設計與證據，不含產品程式。

## 正常使用流程與證據

1. 地圖有懸浮工具與虛擬搖桿兩個不同圖示。Pixel7Pro公開UI樹實際確認兩個名稱，bounds分別[944,1380][1007,1443]與[944,1527][1007,1590]。
2. 免費開任一入口顯示既有Pro對話框；可訂閱或看廣告解鎖6小時。VM測試確認免費拒絕、repository已撤銷但UI尚未更新仍拒絕。Pro清單四語新增懸浮工具；未改Billing／Ad SDK／reward／價格或步數錢包。
3. 依使用者最新回覆，開另一種前先收起舊模式，最多一個window。VM測試確認工具→搖桿→工具狀態互斥、先hide再show，撤銷時全flags關閉、未呼叫定位停止服務。共用原Manager，沒有新增第二窗或service。
4. 實機公開UiAutomation顯示真正Compose面板；驗證搜尋輸入欄打開真實鍵盤且在keyboard上方，收合後實際WindowManager寬高均≤64dp。搜尋結果直接複製不選點；五入口、路線一次→循環→折返→一次文字切換；更多完全關閉；搖桿獨立面板收合／展開／關閉。最新結果OK1，7.005秒。
5. 展開背景82%不透明度、收合90%，文字與操作保持清楚。黑／白底層合成色回歸確認主要／次要／accent／error文字皆≥4.5。真实地圖與資料不是Pen示意內容；依後續要求調整透明度及控制入口。
6. 初期Calculator實測收合後同側按3、減號可用（3→3−）；左右靠邊已觀察。最終公開UI又驗證實際window縮小，防止只有內容變小、透明窗口仍攔截。

## 驗證

- `./gradlew test lintDebug assembleDebug assembleDebugAndroidTest`成功（最終產品51秒）。284 tests／224實際執行／0 failure／0 error／60既有skip。
- lint任務通過，仍34既有Error／97Warning／3Hint；新增的非可觀察語系讀取錯誤已改用LocalConfiguration。未稱lint零問題。
- APK及AndroidTest APK皆install Success。最後僅補強WindowManager幾何測試，assembleDebugAndroidTest成功，實機OK1；產品沒有再改，不重跑同一套已通過檢查。
- 初次模式測試失敗來自無障礙node快取，refresh後通過；首次鎖定找不到入口、與半透明對比4.47失敗均未算PASS。既有警告與未測項不藏掉。

## 資料與限制

- `mockgps.db`所有表rows及内容hash與開工備份一致：收藏375、路線6、路線點591。原始資料只留private/tmp；[雜湊證明](data-preservation.json)不含地點內容。
- Google傳輸事件庫會變動，沒有宣稱全部資料庫一致。語言仍zh-TW；主題初始DARK、最後LIGHT，來源無法單靠備份判定。保留目前LIGHT，不覆寫現行設定，不能宣稱所有設定完全一致。
- 手機持續有人切換前景，最新跨App逐鍵重測未完成；未稱所有Pikmin按鍵皆測過。已用先前Calculator實測及最終真實window尺寸／IME驗證支持此修復。
- 未觸發真實付款、正式獎勵廣告或Play發布；既有訂閱／廣告來源未改，Pro過期與即時拒絕由VM測試覆蓋。未重跑其他Android版本。
