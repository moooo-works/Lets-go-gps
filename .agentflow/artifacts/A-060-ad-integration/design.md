# 廣告分支整合與雙產物

Owner A-060：「把廣告分支整合起來後再build一版aab，另外需產生一份有pro權限的debug版apk」。採1.4.1／10401區別上一包。最新main建立獨立fix/release-ad-integration，承接既有Release的浮窗／FAQ，再cherry-pick廣告功能；不合併GitHub PR、不改後台、不含Mintegral。

Unity與consent-verification產品tree相同，選2eecf57／ab2c75f／88bc790（SDK、UMP、請求前重設與初始化修正），再帶PR58 bb58f9a橫幅adaptive／有界reward重試。四語衝突保留原浮窗與新增隱私字串；Main／Settings自動接線保留Help導覽。

## Minimality check

只打包原1.4.0會缺廣告；只取SDK會缺已完成的同意流程；只保留任一reward分支會掉同意保護或重試。最小完整改動為沿用原廣告功能及Pro build flag，在重疊的reward／banner上合併兩套必要條件。重試共用既有scope、保留generation／revision及3次5/15/30s上限；不新增Pro商品、flavor、永久付費授權、依賴抽象或後台操作。兩項新unit測試專門驗「重試重設隱私」及「撤回取消retry」交界，保留原分支測試；不改無關UI或engine。

## Invariants

- INV-1: Release DEV_FORCE_PRO=false且production AdMob IDs，Debug=true且Google sample IDs；不得把Debug override帶入Release或建立新key。
- INV-2: consent未允許／privacy重設失敗不得載入、顯示或給獎勵；revision變更使舊廣告和retry失效，重試仍3次有界。
- INV-3: 保留Help／浮窗／joystick／Android17已接受功能與四語；不動VM owner／DB schema／儲存資料。
- INV-4: Mintegral SDK不在依賴；PR58–63與後台群組保持原狀，產檔不代表正式Unity中介供應已驗證或Play已發布。

## Acceptance criteria

- 合併前先以舊同意版跑PR58 retry與新privacy交界測試：20tests／3fail；接線後focused20tests PASS（INV-2）。
- required test／lintDebug／assembleDebug、AndroidTest與bundleRelease成功；全廣告unit與Help／浮窗publicUI回歸（INV-2/3）。
- 真AAB protobuf1.4.1／10401、JARsignature／zipCRC；APK manifest1.4.1／10401／debuggable與apksigner；generated BuildConfig Pro flags直接核對（INV-1）。
- AAB mapping／APK dex確認UnityAds與Google Unity adapter、AdConsentManager；依賴無Mintegral、legacy Google Ads排除。署名產物與SHA分別保存（INV-4）。
- 獨立Claude full審查整合diff／新測試及產物證據，原report保全；只交付產物與分支，不上傳Play／啟用生產來源（INV-4）。

紀錄修正：最新Unity stream A006已記獨立SDK初始化與測試banner/reward成功；根紀錄曾沿用舊GameID阻塞，本次不把舊描述當最新後台狀態。正式App經AdMob Unity來源／reward仍是獨立未驗證範圍。
