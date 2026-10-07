* _2026-10-07 23:05:27 +0800 (inherited/inherited)_
Reviewed commit: 5adca013828aeddc21f462ea0edbff30ab967848
Baseline: 3ce386b54d26922109a8b0e1075def01fb6b76fd

**審查結果**

- SDK 準備與同意流程提案通過本次限定審查；版本符合官方配對，依賴圖未帶入 Mintegral 或舊 Google Ads 主套件。

- 同意流程仍是提案，未實作也未完成 Unity 真實供應驗證。不得據此啟用生產中介或宣稱完整接入完成。

- 三項 Gradle 任務成功由主機提供；lint 有 34 個既有錯誤，主機比對確認沒有新增或移除。仍不能描述為零錯誤。

Outcome: PASS

- 精確 diff 僅修改兩個 SDK 設定檔並新增 design.md；沒有覆寫 PR #58 功能。ag.json 未出現在目標 commit。

- Next-Gen 1.5.0、Unity SDK 4.20.1、adapter 4.20.1.0 與[官方整合範例](https://developers.google.com/admob/android/next-gen/mediation/unity#android_studio_integration_recommended)一致；官方 adapter 版本紀錄亦列明與 Unity 4.20.1 相容。App minSdk=26 滿足官方 API 24 下限。

- app/build.gradle.kts 使用官方 configurations.configureEach 排除 play-services-ads 和 play-services-ads-lite。主機依賴圖在 /private/tmp/letsgo-unity-validation.log 包含指定三版本；精確套件搜尋沒有上述兩套件，也沒有 Mintegral／com.mbridge。play-services-ads-identifier 仍存在，並非要排除的 legacy Ads 主套件。

- design.md 明確保留 PR #58，排除 Mintegral、插頁、付款與發布。UMP 同意限制、隱私入口、美國州訊號和真實供應測試均標為後續工作；本次 PASS 僅表示準備成果符合範圍。

Minimality: PASS

- 實際考慮刪除直接 Unity SDK 相依、只依賴 adapter 傳遞帶入 SDK：可少一個 alias，但會讓 SDK 配對改由 adapter 隱性決定，降低固定版本的可讀性，且不再對齊官方三套件範例，因此保留目前明確配對。

- 實際考慮把兩項 legacy 排除合併為 group 排除：會連同 ads-identifier 等其他 Google 套件移除，超出必要範圍，因此保留兩個 module 排除。

- 設計重用既有初始化器、獎勵管理器與設定頁，不另建廣告服務或同意字串儲存；這是可行的簡化方向，尚非已完成實作。

Conformance: PASS

- 只審查指定 commit 與 design.md，未改動產品程式、ag.json 或其他紀錄；唯一寫入是本報告。native 審查與主機共享權限，read-only 是工作限制，並非 OS enforced read-only。

- 未重跑套件測試。主機提供 test／lintDebug／assembleDebug BUILD SUCCESSFUL；248 tests、0 failures/errors、66 skipped。這些結果不能替代實機同意或單一 Unity 來源供應證據。

- 主機報告 lint 為 34 Error、92 Warning、3 Hint；app/build.gradle.kts:91 已有 abortOnError=false，因此 lint 任務成功不等於零錯誤。主機比較主 checkout 10/7 14:56 的先前 lint XML：Error 的 id、message、location、line 多重集合完全相同，34→34，新增 0、移除 0；九個 Error 來源檔與 baseline 3ce386b 的 bytes 相同。此證據由主機提供，未另跑 lint，也不授權修補不相關 lint。

- design.md 的精確設計批准與後續完整流程門檻保留；此次 SDK 審查不替代批准，不將提案中任何隱私行為計為已完成。

Self-check: 已核對精確 SHA、官方相容配對、legacy／Mintegral 排除與最小化考量；三項 PASS 限於 SDK 準備及設計提案，保留 lint、實機及未實作同意流程的限制。
