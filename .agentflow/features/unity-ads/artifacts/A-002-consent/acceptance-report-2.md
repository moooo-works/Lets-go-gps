* _2026-10-07 23:32:42 +0800 (inherited/inherited)_
Reviewed commit: 7fa932facb9289ac5bd14a7880412c68277c8042
Original source: fb44cd7e3cb7d1bde779607f0408ee6ba915a20f
Contract baseline: 02774b4

**兩項程式缺口已解除；本次通過限於程式準備。**

- 初始化與各實際廣告入口會重新檢查 UMP 並重設 Unity 個人化限制；失敗立即關閉 gate，使舊廣告失效。

- 初始化的最終檢查與 SDK 呼叫在主執行緒同一工作段，已移除先前背景初始化的撤回窗口。30 個直接相關測試均實際執行通過。

- R-7 實機地區流程、Unity 單一來源供應及訊號送達仍未證明，不允許生產供應；尚非完整 pipeline 或接入完成，也沒有批准後台條款。

Outcome: PASS
Minimality: PASS
Conformance: PASS

## Security scan / finding closure

- SEC-1 已解除：initializer scope 改 Main.immediate，prepareAdRequest、CAS、建立 config、MobileAds.initialize 之間沒有 suspension；同意表單 begin 同樣在主執行緒，不能再在此工作段插入 false。仍保留一次初始化與 whenReady／完成回呼 gate；相關入口主執行緒契約必須維持。

- SEC-2 已解除：集中 prepareAdRequest 先查目前 state，再讀 UMP 及 reassert privacy.consent=false；失敗設 false、revision+1。初始化、reward 實際 load/show、banner factory 實際 load 均使用此方法；reward 與 banner 再查 revision，避免準備失敗後沿用舊廣告。

- 新測試驗證每次 prepare 重設、失敗換 revision；initializer 準備失敗不呼叫 SDK且釋放等待者；reward 準備失敗不 load、不 show 並 destroy cached ad。SEC-1 解除主要由主執行緒無 suspension 的原始碼證据支持，測試不能證明所有 SDK 內部時序。

- 重用前次完整檢查：WeakReference／operation token 拒絕舊 Activity 回呼，destroyed／finishing owner 不展示表單；privacy 表單開始 false/busy，重複點擊拒絕重入，結束與錯誤依 UMP 當前判斷。舊 rewarded 成功 destroy、舊失敗不清新請求，reward compareAndSet 防重；banner Activity context、最新 gate、revision key 與 onRelease destroy 保留。

- 沒有新增 exported component、權限、秘密、consent 字串儲存、GPP 解碼器或網路服務。Unity 全域 false 是保守限制，可能壓過 EEA 個人化；不是使用者回答，也不能證明 GPP 自動轉送或法律合規。metadata.commit 無成功回傳，實際訊號送達仍 not-proven。

## Requirements / invariants cross-check

|需求|狀態|證據及限制|
|---|---|---|
|R-1|covered|Activity 啟動 update→required form；初始 false，更新／表單錯誤依 UMP；token 防重。|
|R-2|covered|Application 不初始化；initializer 最終 prepare+SDK 呼叫同主執行緒工作段；whenReady 及 load/show 入口重查。|
|R-3|covered|僅 REQUIRED 顯示入口；使用者點擊開表單；busy 不重入、開始 false、結束重讀。其他 UMP 狀態由 equality 判斷隱藏。|
|R-4|covered|revision／generation 使 loaded 與 in-flight rewarded 失效；舊 success destroy、舊 failure 隔離；banner key/release。實際 UI 拆卸時序 not-proven。|
|R-5|covered / runtime not-proven|每次初始化／實際 load/show 前重設 false；失敗關 gate，從不由 canRequestAds 推 true。GPP、SDK 訊號傳遞與地區效果仍 not-proven。|
|R-6|covered|沿用原16檔範圍，本輪7檔僅修批准同意邊界與tests；PR58功能、mock、map、VM owner 無修改，排除 Mintegral；遠端 PR 狀態由主機管理。|
|R-7|not-proven|Gradle與依賴圖成功；實機 EEA／非EEA／美國、Ad Inspector Unity bidding banner/reward與map/mock smoke 未提供。Google sample ads不能證明Unity供應。|

- INV-1：程式 covered；初始／busy／false 不初始化或請求，SEC-1 已封閉。實機SDK內部行为未證明。

- INV-2：程式 covered；錯誤使用 UMP、未知不猜 true，每次請求重設限制失敗即停止。全球 false 的地區效果及訊號送達仍有限制。

- INV-3：程式及單元測試 covered；舊回呼／舊廣告／重複獎勵隔離。banner disposal與SDK實際行為須實機補驗。

- INV-4：diff covered；mock／navigation owner／map／PR58功能未被本輪覆寫，實機smoke not-proven。

## Evidence / minimality / boundaries

- 本輪審查 fb44cd7→7fa932f 七檔修正與新增測試，重用前次精確 baseline→source 的完整 security／acceptance codewalk及未失效證據。原 BLOCKING acceptance-report.md 保留，沒有替換；未重跑通過的 suite。

- /private/tmp/letsgo-consent-reviewed-validation.log 三项Gradle BUILD SUCCESSFUL；主機提供269 tests、0 failures/errors、66 skipped（203實際執行）。直接讀 XML：manager11、initializer4、rewarded15，均skipped0、failures0、errors0。

- lint34 Error／92 Warning／3 Hint，主機比對new0 removed0；不能稱零Error。debug/release AppID／release manifest與SDK相依圖沿用前次證據，本次未修改：Next-Gen1.5.0、Unity4.20.1、adapter4.20.1.0，無legacy ads／ads-lite／Mintegral。

- 真實最小化考量：將 privacy 重設合併至既有協調器，避免在三個入口複製 MetaData 或新增 privacy service；保留 gate/preparation/revision 的不同用途。考慮刪除 reward generation 改只用 consent revision，但 generation 也隔離本地失效後同 revision 舊回呼，保留有理由。重用 Next-Gen 內建 UMP，沒有新增相依或自製 CMP。

- 官方依據沿用已查的[Google UMP](https://developers.google.com/admob/android/next-gen/privacy)、[Unity mediation](https://developers.google.com/admob/android/next-gen/mediation/unity)與[Unity privacy](https://docs.unity.com/en-us/grow/ads/privacy/ccpa-compliance)。不把官方 metadata 路徑解讀為完整地區合規證明。

- native 共享主機權限，read-only 是 assigned 限制，非 OS enforced。本輪唯一repo寫入為新report；沒有委派、改產品、接受條款、啟用生產中介或操作後台。

Self-check: 精確新版SHA、SEC-1／2解除、R-1至7、INV-1至4、security與最小化考量已核對；舊BLOCKING報告保留，測試未重跑，PASS僅限程式準備，R7實機與生產供應仍未完成。
