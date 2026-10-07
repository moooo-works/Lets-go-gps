* _2026-10-07 23:28:03 +0800 (inherited/inherited)_
Reviewed commit: fb44cd7e3cb7d1bde779607f0408ee6ba915a20f
Baseline: 02774b4

**目前需補兩個同意限制缺口，再完成程式準備。**

- UMP、隱私入口及廣告世代控制已實作，27 個直接相關測試均實際執行成功。

- 初始化仍在背景執行緒檢查允許後才呼叫 SDK；使用者此時開隱私表單，仍可能在不允許狀態開始初始化。另缺契約要求的初始化與每次請求前 Unity 限制重設。

- 實機地區流程、Unity 單一來源供應及訊號送達仍未證明，不允許生產供應。本報告不代表條款批准或完整接入完成。

Outcome: BLOCKING
Minimality: PASS
Conformance: BLOCKING

## Security scan

- **SEC-1／BLOCKING：背景初始化與撤回交錯。** AdMobInitializer.initialize 在 IO 工作段先讀 canRequestAds，接著建 RequestConfiguration／InitializationConfig 才呼叫 MobileAds.initialize；主執行緒可在其間執行 showPrivacyOptions 的 begin，將 gate 設 false。這會違反 INV-1 的「false 時不能初始化」。最小修正是在主執行緒同一工作段做最終 gate 檢查及 SDK 初始化呼叫，仍保留 AtomicBoolean；補初始化已排程但表單先開始的測試。現有測試只查初始化完成時撤回，不涵蓋此交錯。

- **SEC-2／BLOCKING：缺請求前限制重設。** 批准 contract 的 Confirmed fallback 要求在初始化及每次允許請求前 commit privacy.consent=false；目前唯一 applyMediationPrivacy 呼叫是 manager.finish。initializer、reward loader 和 banner factory 沒有重設或處理該時點失敗。metadata 是持久 SDK 資料，但其 commit 成功與傳遞沒有可驗證回傳，因此無法用先前 finish 替代明確契約。最小修正為重用協調器的請求前方法，失敗關閉 gate；不要新增 consent 字串儲存或 GPP 解碼器。

- 正向防護：初始 false；只有 UMP 當前 canRequestAds 與 privacy set 成功才開 gate，更新／表單錯誤不猜 true。未輸出 consent 字串，沒有新增 export component、權限、網路服務或秘密。Unity metadata 永遠 false，不能宣稱使用者回答、GPP 支援或法律合規。

- Activity 僅 WeakReference 保存在協調器；operation token 拒絕舊 owner 回呼，destroyed／finishing owner 不開表單。重複成功與 late failure 無法重開 gate。設定按鈕 busy 時即使可見仍由 manager 拒絕重入。

- Rewarded 在等待初始化、成功／失敗回呼及 show／reward 時檢查 revision；舊成功 destroy，舊失敗不清新 load，loaded ad 隨世代失效。Banner 使用 Activity，factory 再查最新 gate／revision／Activity 狀態，composition key 配合 onRelease destroy。實際 Compose 拆卸時序尚需實機驗證。

## Acceptance / cross-check

|需求|狀態|證據及限制|
|---|---|---|
|R-1|covered|Activity onCreate gather；update→required form；失敗依 UMP 重讀，初始 false。AdConsentManager 10 tests，0 skipped。|
|R-2|missing|Application 無初始化；whenReady 再查 gate；但 SEC-1 的背景初始化窗口未封閉。Initializer Robolectric API28 3 tests 成功，不代表此交錯已測。|
|R-3|covered|只有 REQUIRED 顯示隱私入口；用戶點擊才開表單；begin false/busy，結束重讀。NOT_REQUIRED／UNKNOWN 皆由相同 equality 判斷隱藏。|
|R-4|covered|rewarded revision／generation 失效、舊成功 destroy、舊失敗隔離、reward compareAndSet；banner key/release。Rewarded 14 tests 成功；banner UI disposal 未實機證明。|
|R-5|missing|不推 opt-in、false fallback 已實作；但 SEC-2 缺每次允許請求前重設。GPP 自動轉送與 commit 訊號送達 not-proven。|
|R-6|covered|16 個 changed files 集中批准入口、manifest、必要字串／tests；mock engine、map、VM owner 沒有 diff。沒有加入 Mintegral 或 Unity Game/Placement ID 初始化，沒有改 PR #58 原功能。遠端 PR 狀態由主機持有，不在本審查操作。|
|R-7|not-proven|Gradle、依賴圖成功；實機 EEA／非 EEA／美國、Unity 單一來源 banner/reward 及 map/mock smoke 尚未提供。Google sample ads 不能證明 Unity 供應。|

- INV-1：BLOCKING（SEC-1）；請求入口 gate 與 callback 重查已存在，但不能據此忽略初始化時序。

- INV-2：部分 covered；錯誤回退及未知不推 true 正確，請求前 fallback 契約缺口為 SEC-2。美國訊號與 EEA 個人化影響仍明列限制。

- INV-3：程式與單元測試 covered；privacy 開始／完成換 revision，重複回呼及舊廣告隔離正確；banner 實機拆卸與 SDK 實際行為 not-proven。

- INV-4：changed-file cross-check covered；既有 map/mock 實機行為 not-proven。

## Evidence and minimality

- 已讀精確 baseline→target diff、批准 A-001/design 與 A-002/contract，以及協調器／初始化器／rewarded 完整程式、banner／setting／manifest／Gradle diff；逐一搜尋所有 SDK initialize/load 呼叫以檢查漏掉入口。額外原始碼搜尋的原因是 SEC-2 缺口，未重跑已成功 suite。

- /private/tmp/letsgo-consent-final-validation.log BUILD SUCCESSFUL；主機提供總計266 tests、0 failures/errors、66 skipped（200執行）。直接讀三份 XML：manager 10、initializer 3、rewarded 14，均 skipped=0、failures=0、errors=0。

- lint34既有 Error，主機比對 new0 removed0；不稱零 Error。debug／release App ID 分別沿用 sample／production，manifest placeholder 重用同一變數；主機提供 releaseMergedManifest 成功。依賴圖包含 Next-Gen1.5.0／Unity4.20.1／adapter4.20.1.0，搜尋無 legacy ads／ads-lite／Mintegral。

- 最小化實際考量：可刪 generation 僅用 consent revision，但 generation 還表示 manager 本地失效，可避免本地失效後同 revision 舊回呼存入；目前保留。可重用現有 AdConsentManager.applyMediationPrivacy 的薄 backend，而非新增 Unity privacy service，適合修 SEC-2。沒有另加 UMP 相依，重用 Next-Gen 內建 UMP。

- [Google UMP](https://developers.google.com/admob/android/next-gen/privacy)支持啟動更新、錯誤依 canRequestAds 與必要入口；[Unity mediation](https://developers.google.com/admob/android/next-gen/mediation/unity)及[Unity privacy](https://docs.unity.com/en-us/grow/ads/privacy/ccpa-compliance)支持限制 metadata 路徑。false 是限制，可能覆蓋 EEA 個人化，不能寫成精確保留所有 CMP 選擇。

- native 共享主機權限；read-only 是 assigned 限制，不是 OS enforced。本次唯一 repo 寫入為本報告，未接受條款、操作後台、修程式或委派。

Self-check: 精確 SHA、R-1至R-7、INV-1至4、security scan、真實最小化考量及驗證限制已逐一列出；SEC-1／2已回報主機，通過的測試未重跑，未將程式準備、實機或生產供應宣稱完成。
