# Unity Ads 同意流程設計

目前先完成 SDK 相容版本與編譯準備；完整供應還需要同意流程及 AdMob 對應。

## 授權與範圍

- Source：本輪「godev，接續 Unity Ads SDK 開發，讀取主工作紀錄 A-026／A-027，保留 PR #58，排除 Mintegral。」及主紀錄 A-026 的完整接入授權。
- 保留 PR #58；只在 unity-ads 分支處理 Unity 接入，不納入原 PR 的廣告優化。
- 正確 Game ID：800390974；Banner：BP_Banner_Android；Rewarded：BP_Rewarded_Android。這些識別碼在 AdMob 後台對應，不寫入 App 的初始化程式。
- Mintegral、插頁廣告、付款資料、版本發布均排除。

## 正常使用流程

1. Activity 啟動後，向 Google UMP 更新當前同意資訊；依伺服器需求顯示表單。
2. 僅在 UMP 的 canRequestAds 為 true 時初始化 Google Ads，並允許橫幅／獎勵請求；更新失敗時也依 UMP 判斷，不能自行假定使用者同意。
3. 設定頁只在 UMP 要求時顯示「廣告隱私選項」，讓使用者重新開啟表單。更改選擇後停止新的不允許請求並釋放已載入的廣告。
4. Unity adapter 讀取 CMP 的訊號；不得把 privacy.consent 寫死為 true。美國州隱私訊號需核對官方 GPP／手動傳遞規範，再固定實作，未知訊號不得猜成同意。

## 受影響檔案與方式

- gradle/libs.versions.toml、app/build.gradle.kts：既有 SDK 相容配對；只在官方 Next-Gen 未包含 UMP 時另加必要 UMP 相依。
- MainApplication.kt：移除 Application 無條件初始化入口。
- MainActivity.kt：啟動時更新 UMP；不在 NavHost 外建立 SavedState ViewModel。
- data/billing/AdMobInitializer.kt、RewardedAdManager.kt：在既有初始化機制加入同意限制，所有請求入口都檢查；保持一次初始化及主執行緒回呼。
- ui/components/BannerAdView.kt：依同意狀態建立／釋放廣告，使用 Activity context。
- ui/settings/SettingsScreen.kt、必要 strings：顯示 UMP 要求的隱私入口。
- 新增最小同意協調器及相關測試；避免自製 CMP、重複儲存 consent 字串或大型廣告重構。

## Invariants

- INV-1：啟動時尚未取得可請求狀態，不能初始化／請求廣告；canRequestAds=false 時任何入口發出請求都算失敗。
- INV-2：更新或表單失敗後，只採 UMP 現有有效判斷；未知訊號不寫入 true，否則失敗。
- INV-3：重複回呼、Activity 重建不能造成重複初始化或獎勵；同意變更後不能重用舊廣告，否則失敗。
- INV-4：既有 mock location、Navigation VM owner、地圖四項 UX 與 PR #58 原修改不被本分支覆寫。

## Acceptance criteria

- 首次 EEA 同意未完成時無廣告請求；完成後允許一次初始化，拒絕／錯誤時依 canRequestAds 決定。以假的 UMP 結果與廣告載入器單元測試驗證 INV-1／2／3。
- 隱私選項需求 REQUIRED 時入口可用，其他狀態隱藏；修改同意後釋放舊廣告，實機測 EEA、非 EEA 與美國州流程，驗證 INV-2／3。
- 依賴圖包含指定 Unity SDK／adapter／Next-Gen，無 legacy ads／ads-lite 或 Mintegral；test、lintDebug、assembleDebug 通過。
- 真實測試裝置使用 AdMob 測試模式及 Unity 測試模式，Ad Inspector 單一 Unity bidding 來源的橫幅與獎勵都成功；Google 範例廣告不能作為 Unity 供應證據。
- 實機補驗冷啟動不跳設定、mock app 可選、地圖 pin／準星／泡泡／列表置中，驗證 INV-4。

## Minimality check

- 最小結果：以 UMP 當前狀態控制既有廣告入口，並提供必要隱私選項。
- 僅加 adapter 不足：目前 Application 直接初始化，沒有同意資訊，無法證明 Unity 收到正確訊號。
- 保留既有初始化器、廣告管理器及設定頁，不另建廣告服務或自製同意儲存；每個改動只服務同意取得、請求限制或撤回。

## 待確認與執行門檻

- 隱私流程屬 consequential trust boundary；依 agentflow 的 Design Go 規則，設計提交後需批准精確 commit，才改這部分程式。SDK 相依準備可獨立完成。
- 下一階段先固定美國州訊號的官方契約，再以 full_pipeline 完成必要的 requirements／spec／security／acceptance；未通過前不啟用生產中介或發布。
- AdMob 出價條款仍待原使用者批准，不由本次 SDK 開發指示推定批准。

## 官方依據

- [Unity Next-Gen 整合](https://developers.google.com/admob/android/next-gen/mediation/unity)：版本配對、legacy 排除、同意訊號與測試流程。
- [UMP 設定](https://developers.google.com/admob/android/next-gen/privacy)：每次啟動更新、canRequestAds、必要隱私入口與失敗處理。
