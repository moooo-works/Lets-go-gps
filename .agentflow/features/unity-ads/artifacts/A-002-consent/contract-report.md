* _2026-10-07 23:13:23 +0800 (inherited/inherited)_
Reviewed source commit: 5adca013828aeddc21f462ea0edbff30ab967848

**同意流程可以在既有廣告入口實作，無須新增 UMP 相依。**

- Next-Gen 1.5.0 已直接帶入 UMP 4.0.0；讓 UMP 決定可否請求廣告，保留一次初始化。

- 美國州 GPP 自動傳遞尚無足夠官方或本機證據，不能當成已支援，也不能把 `privacy.consent` 寫成 true。採明確保守限制或保持 Unity 生產供應未啟用，直到實機確認；這不阻止本次 UMP 程式實作。

- 隱私選項開始及結束都使舊廣告失效；需用請求世代編號拒絕稍後到達的舊回呼，僅清空 loadedAd 不足。

## Requirements → invariants → specification

|需求|已批准內容|Invariant|可驗證規格|
|---|---|---|---|
|R-1|Activity 啟動更新 UMP，必要時展示表單|INV-1、2|S-1：Activity 啟動呼叫 requestConsentInfoUpdate；成功後 loadAndShowConsentFormIfRequired；失敗也讀 canRequestAds；初值 false，不把 consentStatus 或 callback success 當成允許。|
|R-2|canRequestAds true 才初始化及請求|INV-1、2|S-2：Application 不初始化。initializer 的 initialize/whenReady、reward preload/show、banner load 都查同意限制；等待初始化完成後再次查，不能沿用較早結果。AtomicBoolean 保持一次初始化。|
|R-3|僅 REQUIRED 顯示設定入口|INV-2、3|S-3：StateFlow 暴露 privacyOptionsRequired，設定頁僅為 REQUIRED 顯示入口；僅使用者點選呼叫 showPrivacyOptionsForm。開表單立即停止新請求，結束重新讀 UMP；錯誤不猜同意。|
|R-4|變更同意釋放舊廣告、避免重複回呼|INV-3|S-4：單例協調器維護世代編號；隱私表單開始、完成均換世代。in-flight load 捕捉世代，回呼不同則 destroy；loaded rewarded destroy/null；banner 以世代及允許狀態作 composition key，onRelease destroy。show 前再檢查；同一廣告獎勵仍使用既有 compareAndSet 防重。|
|R-5|Unity 讀 CMP；未知訊號不得 true|INV-2|S-5：保留 TCF/AdditionalConsent adapter 路径；不把 canRequestAds 對映為個人化同意。美國 GPP 缺證據時不得依賴自動轉送，也不得寫 true；防守性 false 只可表示主動限制，不可宣稱它是使用者的回答。|
|R-6|保留 PR #58、排除 Mintegral 與其他功能|INV-4|S-6：僅批准設計列出的入口與必要測試/字串；不修改 mock location、VM owner、地圖流程、不寫 Unity dashboard Game/Placement ID 初始化。|
|R-7|三項 Gradle、依賴圖與實機流程|INV-1至4|S-7：test/lintDebug/assembleDebug；確認版本配對且無 legacy ads/ads-lite/Mintegral；實機 EEA/非EEA/美國、Ad Inspector 單一 Unity bidding banner/reward；既有 map/mock smoke。範例 Google ads 不能證明 Unity 供應。|

## Discovery / codewalk shared coverage

- 共同閱讀上述批准 design commit；本機 AdMobInitializer、RewardedAdManager、BannerAdView、MainApplication、MainActivity 的 onCreate 與設定 route、SettingsScreen 的 Activity 與廣告入口、AndroidManifest、兩份 Gradle 相依設定。

- **現況事實**：Application 無條件 initialize；whenReady 會自動 initialize；reward 無同意 gate、沒有失效世代或 LoadedAd.destroy 接口；banner 只監聽 initializer Ready。banner 已有 onRelease destroy，可保留並擴充同意 key。

- **現況事實**：Manifest 無 AdMob APPLICATION_ID metadata；新 UMP 流程要依官方 UMP App ID 設定，填既有 BuildConfig 所對應的 AdMob app ID／placeholder，不能猜新 ID。Activity 注入協調器，不在 NavHost 外取得 SavedState VM。設定頁已有顯式傳入 VM。

- **最小接口（規格，非既有事實）**：AdConsentGate 暴露 state: StateFlow<AdConsentState>、canRequestAds(): Boolean；state 包含 allowed、privacyOptionsRequired、generation。具體協調器提供 gatherConsent(Activity)、showPrivacyOptions(Activity)；薄 UMP wrapper 供測試替換。只在 UMP 呼叫時持有 Activity，不長期保存。需要 listener 或 state observer 通知 rewarded 主動 destroy，banner collect state；不新增 consent 字串儲存。

- **Activity 回呼規格**：相同 Activity 重複 gather 不開第二個表單；換 Activity 時舊 owner 回呼不得展示表單。用 operation token 加 WeakReference/Activity identity，完成前確認 !isFinishing && !isDestroyed；新 Activity 在舊 owner 完成/取消後可重新 gather，不能永遠被 in-progress 擋住。底層 UMP current status 可以更新，但舊回呼不能恢復舊世代或再次初始化。

## 官方與本機 SDK 契約

- **事實**：本機 ads-mobile-sdk-1.5.0.pom 的 compile dependency 為 `com.google.android.ump:user-messaging-platform:4.0.0`，相同 UMP AAR 已在 Gradle cache。不另加相依。

- **事實**：[Google UMP](https://developers.google.com/admob/android/next-gen/privacy) 要求每次啟動更新，錯誤也依 canRequestAds，重複允許結果需防重；UMP 的初始 canRequestAds 在 requestConsentInfoUpdate 前為 false。本規格仍採首次目前流程完成後開 gate，避免應用程式自行用快取猜允許。隱私入口依 REQUIRED。

- **事實**：[Google Unity mediation](https://developers.google.com/admob/android/next-gen/mediation/unity) 保證 adapter 4.19.0.1+ 的 TCF/Additional Consent 轉送；美國示例 `privacy.consent=true` 是手動 opt-in 範例，不能代表 UMP 每位使用者回答。該頁同時要求 Activity context 與單一來源測試。不要從 canRequestAds 推出 true。

- **事實**：[Unity consumer privacy](https://docs.unity.com/en-us/grow/ads/privacy/ccpa-compliance) 記載 privacy.consent false 表示限制個人化；privacy API 可覆蓋 gdpr API。故全球一律 false 會壓過 EEA 個人化選擇，不能宣稱精準保留 CMP 原選擇。

- **事實**：本機 Unity 4.20.1 AAR 的 classes.jar 及其他封装資產搜尋 IABGPP/Gpp/gpp 均無命中；javap AndroidTcfDataSource 明確讀 IABTCF_TCString。UnityAds 存在 nullable setUserConsent、setUserOptOut、setNonBehavioral。這些證明 TCF 讀取與手動 API，不能證明 GPP 自動處理；遠端 WebView/server 路径未查，不能反向斷言 SDK 絕不支援。

- **決策建議**：批准設計允許「未知不能猜同意」；若目前不能可靠識別適用州及使用者 opt-out，就不建立自行解析 GPP 的新框架。可先對 Unity 作保守 privacy.consent=false（在初始化及每次允許請求前 commit，承認可能限制 EEA 個人化），或保持 Unity 生產供應禁用直到核對信號。選擇此 fallback 是實作保守限制，不是替使用者回答。不能用單次空 GPP string 推出非美國或 opt-in；是否開啟生產供應仍屬原設計的未完成實機／後台驗證。

## Confirmed fallback and SDK lifecycle

- Host selected `privacy.consent=false` as the temporary conservative restriction; do not decode GPP or introduce another CMP. This restricts Unity personalization, including potentially EEA users, and does not certify automatic GPP support. Retain TCF propagation but never infer personalized opt-in from UMP canRequestAds.

- Actual manager state contract: `canRequestAds`, `privacyOptionsRequired`, `revision`, `busy`, `error`. Updates/forms suspend requests and invalidate loaded/in-flight ads. Completion or update error republishes UMP's current decision.

- Local `javap MetaData` confirms constructor retains applicationContext, set stores value/timestamp, and commit initializes PUBLIC storage, merges values, writes storage, and emits SET. This is persistent SDK metadata, not a single-request value; reassert false before initialization and permitted requests. commit returns void and logs storage failure, so its return cannot prove successful transmission. Check set's boolean, catch exceptions, and verify on device; signal failure must not permit affected requests.

- [Unity consumer privacy](https://docs.unity.com/en-us/grow/ads/privacy/ccpa-compliance) explicitly documents false and privacy metadata precedence over gdpr. [New developer API](https://docs.unity.com/en-us/ads-android/4.20.0/privacy/developer-consent/solution/custom-consent) distinguishes framework-specific userConsent/userOptOut and recommends those APIs after initialization. Follow Google's mediation metadata pre-request path here rather than mixing those newer API semantics into a second consent store. This supports a non-TCF conservative restriction, not a claim of comprehensive legal compliance.

## 必要測試與交付記錄

- 同意初始 false、更新成功 true/false、更新失敗但 UMP true/false；兩次成功回呼只初始化一次；Activity 重建舊回呼不能開表單或覆蓋狀態。

- REQUIRED／NOT_REQUIRED／UNKNOWN 入口；隱私表單進行中禁止新 load/show，完成 true 也不能重用舊廣告；false 停止請求；錯誤依 UMP 重讀。

- 延遲 rewarded 成功及失敗回呼跨世代：成功 destroy、不存入；舊 failure 不清掉新 load 的旗標；先前 loaded destroy；同一 show reward 防重。banner 拆卸及隱私世代變更均 destroy，重新可用建立新 Activity-backed view。

- 本 slice 僅 report，未跑程式測試、未改 source、未 commit。Owner 負責整合 commit、notebook/STATUS/route、Git preflight、必要驗證結果；這些工作不委派越界寫入。本報告不代表實機或生產供應完成。

## 未查區域與範圍

- 未做全 repo codewalk、Google/Unity 後台、付款/合作條款、其他 ad networks、mock engine、地圖、遠端 Unity server/WebView GPP 實作。現有 PR #58 尚需 owner 以 Git 證據確認保持；本報告未操作 PR。

- 需要主線整合時先遵守 AGENTS Git preflight；不擴充設計、不自製 CMP、不新增依賴、不修相鄰功能。

Self-check: requirement R-1 至 R-7 均映射批准 invariants/spec；discovery/codewalk 共用覆蓋與未查範圍明列；官方事實與本機證據、推論/建議分開；唯一 repo 寫入為本報告；未宣稱 GPP 自動契約已確認或實機已通過。
