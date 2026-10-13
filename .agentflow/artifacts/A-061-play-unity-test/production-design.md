# 正式版發布計畫

- Source: A-061 owner「完成後將測試版升級為正式版，更版說明記得寫」。原內部Unity正向測試已成功，授權擴充到正式發布及更新說明；不直接升級10402測試版。
- Normal journey: 建置正式IDs的1.4.3/10403 signedAAB→必要Gradle驗證及Claude審查→Play內部更新Pixel確認資料/地圖/正常廣告→正式發布、正式Unity中介group啟用→核對Console狀態與Pixel測試裝置廣告；無購買、PR58保留、Mintegral排除。
- 從最新origin/main建立獨立worktree，合併A060已驗產品source；僅versionPatch1→3。保留10401/10402與backup，測試init script不套用正式build。前一待派runtime-only review取消派工（尚未啟動）；最後review改涵蓋正式候選與runtime證據，重用未改產品測試證據但遵守repo必跑test/lintDebug/assembleDebug。

## Invariants

- INV-P1: 正式AAB ADMOB_APP_ID／banner1824598031／reward6473078035，DEV_FORCE_PRO=false；出現8648995174/8563391382或testonly標記不得發布。
- INV-P2: code10403>10402，名稱1.4.3；原10401與10402不覆寫。PR58保持原PR，不merge。
- INV-P3: 更新而非uninstall；375位置/6路線/591路點保留，私有GPS與AAID不Git/外傳。
- INV-P4: Owner只授權App正式發布與必要廣告配置，不建立購買或接受新法律協議；SDK/UMP/paid功能及浮窗FAQ維持已驗產品。
- INV-P5: Unity測試結果限定Pixel/Android17正向banner與reward；提前取消/exactlyonce/Europe/productionfill未驗限制保留。

## Acceptance criteria

- 獨立worktree產品樹與b9868ee相較僅版本patch變更；./gradlew test/lintDebug/assembleDebug與bundleRelease成功，簽章/CRC/realmetadata/productionIDs/Profalse核對（INV-P1,2,4）。
- Claude最後審查固定source/ROOT及更新說明、測試證據，host核對再publish（INV-P4,5）。
- Pixel真Play安裝10403，地圖圖資與原資料可用；內部10402由正式candidate取代（INV-P3）。
- 正式release有zhTW及適用語系更新說明，核對Play已發布或Google審查中的實際狀態；不能把送審當已上線。正式Unitygroups配置只使用正式IDs/正確Game/placements，保留GoogleAdMob fallback（INV-P1,4）。

## Minimality check

- 10402測試版不能直接promote：隔離IDs會帶測試設定進正式，原10401code又小於已消耗10402；最小安全候選是已審產品只升版到10403並正常build。
- 不改SDK、獎勵邏輯、版本算法、地圖、DB或UI；更新說明忠實涵蓋已整合浮窗/FAQ/Android17/隱私/Unity功能，不為了發布新增其他功能。
