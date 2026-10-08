# Android 17 開發者選項誤判修復

授權：A049「你先檢查android 17的版本，我已經開啟開發者選項，但是app這邊監測結果是「未開啟」，這是bug需修復，修復完成後再來測試懸浮工具」。先修此bug，再續T5浮窗；不同主題以獨立fix worktree及PR，不混PR59/58。

## Outcome and Minimality check

- Pixel7Pro Android17/API37，CP3A.260905.009；shell development_settings_enabled=1、Mock AppOps/overlay均allow。官方Global API已註明一般App總讀0，不能據此宣稱未開啟。
- 最小修正：Android17不以developer全域值當失敗；健康檢查使用既有NotApplicable（不顯示無法適用的項目），mock以實際AppOps為主要依據。AppOps拒絕及讀值失敗仍保留NotAllowed/CheckFailed。
- 更簡單替代「把0一律當開啟」會假報狀態；「只改文字」仍會阻擋啟動。保留兩個consumer修正及真實執行regression/instrumentation，沒有權限提昇或ADB grant。

## Invariants

- INV-A17-1：API37一般App讀developer=0，保證不回DeveloperModeDisabled/健康blocking，實際mock仍由AppOps決定；失敗=錯誤developer擋使用。
- INV-A17-2：AppOps非Allowed或例外，保證NotAllowed/CheckFailed不放行，健康mock項仍失敗；失敗=版本相容被当授權。
- INV-A17-3：舊版本開發者狀態及其他health/engine處理維持必要契約；不改settings/schema/廣告或自動設定。

## Acceptance criteria

- API37+global0+AppOpsAllowed，健康Developer NotApplicable、Mock Passed且無blocking；真實Pixel7Pro appUID instrumentation和UI確認不再未開啟。
- API37+global0+AppOpsdeny/error，仍拒絕／CheckFailed，單元測試zero假授權。
- 舊版本既有health/developer cases通過；必跑test/lintDebug/assembleDebug，獨立code review、fix PR behind0。手機原資料備份保留。
- 修復裝置验证完成後，將fix功能commit帶到獨立浮窗分支再執行T5；維持兩PR單一主題。

## Execution

T7a：實機版本/官方API/source診斷、紅測試。T7b：engine/health最小修正。T7c：單元/真機檢查與獨立review。T5只有修復已證實才續測。

Design Go：使用者已授權修復，既有away-gates:on在保存此計畫commit後供給；無owner決策或新增安全授權。
