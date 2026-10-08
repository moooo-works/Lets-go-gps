* _2026-10-08 07:55:50 +0800 (inherited/inherited)_
Reviewed commit: 430975dbdeca6a5b43413d7037d700333523fe5a
Baseline: 39164dae3844df7a368431f62ac65bf7cc0945ac

**設定符合授權，截圖說明需修正。**

- auto-reply:on、away-gates:on、streams:off 是唯一三個設定差異；App source 未變，不需要重跑 Gradle。

- 群組列表支持兩個 Unity 群組已暫停；另兩張截圖完全相同，沒有文件所稱的群組 ID、廣告單元、暫停或保存成功資訊。需補正確截圖或明確改寫證據來源。

- 後台配置不能證明 Unity 廣告實際供應；使用者選擇保留待驗證，R-7 實機仍未完成。

Outcome: BLOCKING
Minimality: PASS
Conformance: BLOCKING

## Targeted findings

- EVID-1／BLOCKING：backend-verification.md 的兩個截圖連結稱包含群組ID、map-ad-2／no-ad-6h、已暫停；實際圖片只顯示「新增中介服務群組」、AdMob／Unity有效來源兩列、空waterfall及灰色儲存按鈕。沒有上述ID、單元、暫停或保存成功資訊，不能作為兩次已保存畫面的證據。

- banner-paused.jpg 與 rewarded-paused.jpg SHA-256 均為 51c39ac9854309f2ae9b4e93fd2f7e856c8771be5095148efa0f23f45a4c186d，逐圖查看亦相同。最小修正是補兩個正確保存結果截圖；若維持現圖，需改稱来源配置畫面，明示ID／單元／保存成功來自主機CUA觀察，非截圖證明。已回報主機，未操作GUI。

- groups-paused.jpg 有兩行 Let's Go - Unity R…／B…及暫停圖示，收益與曝光均為「—」，沒有email或其他群組收益。它支持最後列表已暫停，但不顯示精確群組ID、Game ID／Placement ID。

- 來源圖片支持僅AdMob Network／Unity Ads兩列、合作夥伴有效、waterfall空；不能單憑它證明兩個已保存群組均有這些配置。具體8708929545／8676901314、單元及mapping是主機CUA提供的觀察，非本審查圖片可直接核對的事實。

## Scope / conformance

- git diff 僅當前stream三值與一份後台記錄、三張JPEG；app／gradle比較無差異。保留既有其他設定，沒有擴大到root ag.json或其他stream。

- 後台文件區分既有有效合作關係與本輪核對／複製對應，未聲稱我們接受條款；明確未操作既有Mintegral來源。這些歷史事實是主機提供的CUA觀察，本次未獨立操作後台。

- 文件明確說兩群組paused、非生產、mapping／prepared不代表供應；保留CMP地區、訊號送達、Unity單一來源banner/reward、map/mock實機未驗證。PR58 open/draft由主機gh確認，未由本審查操作。

- 269 tests與先前security／manifest／lint證據未因本輪文件及設定改動失效，直接沿用，不跑Gradle。未把R7延期寫成完成，沒有背景測試。

## Minimality

- 實際考慮刪除完全相同的第二張圖片、重用一張來源配置圖：可以減少重複檔案，但只有在文字改成共同配置畫面、明確其證據限制後才可如此；目前核心問題是錯誤來源描述，非檔案數量。補正確兩張保存圖也合理，保留每個群組的不同ID／單元比較。

- 設定三值直接改既有stream設定，沒有新增機制或source改動；不需要產品重構，也不修相鄰後台配置。

- native共享主機權限與context；read-only是assigned限制，非OS隔離。本次唯一repo寫入為此報告，未委派或操作GUI。

Self-check: 精確SHA、三設定onlydiff、source不變、三張實際影像與重复hash已核對；EVID-1已回報，BLOCKING限於證據文字不符，不否定主機CUA觀察；保留R7待驗證與非生產供應限制。
