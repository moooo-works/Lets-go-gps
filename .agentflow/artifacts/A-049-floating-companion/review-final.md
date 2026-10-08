* _2026-10-08 17:05:57 +0800 (gpt-6-astra/medium)_

Reviewed commit: 915546401df32839243034deb6185a1b36794364

Review range: d7078bf20f3081c388fc4daeb6762fa48e19b5f7..915546401df32839243034deb6185a1b36794364

- **程式審查維持 PASS。** 本次只增加兩個 VM 測試，沒有改產品程式；前次 F1/F2 修正結論仍有效。

- **實機接受仍為 BLOCKING。** 手機權限及浮窗、鍵盤、小地圖、背景操作證據仍未完成，只適合標示為待實機驗證的 draft。

- **測試補到實際 VM 停止與關閉通知接線。** 停止案例已明確設定 AppOps Allowed，排除該權限拒絕掩蓋取消缺口；真實視窗與 ticker 動作仍未實測。

## Verdict

- **Outcome: PASS** — 限定 code review；實作與已通過修正審查的 d7078bf 相同，未新增程式 blocker。

- **Minimality: PASS** — 單一 MapViewModelTest.kt 增加 49 行，針對原兩個發現补接線證據，無額外功能、依賴或重構。

- **Conformance: PASS** — 限定 code review；R1–R6、INV-1–6 的 source 判讀重用原 review 與 review-corrections，同一實作不重做全面審查。下述測試限制保留，不擴大通過範圍。

- **Code review verdict: PASS。Acceptance: BLOCKING。** 完整接受尚未解除的 blocker 為原 F3：權限及實機行為未證明；本報告不支持 Result Go 或合併建議。

## New test claims checked

- **VM stop：** 測試啟動會延後返回且不理取消的 controller route 讀取，呼叫真正 `vm.stopMocking()` 後讓結果返回；驗證只有一次明確 STOP、零 foreground START、零新增 setRoute 與零扣次數。只在初始化後清 recorded calls 並保留 answers，正確排除初始化的 setRoute 空列表，不會抹去受測操作。

- **Stop fixture checked：** 第三輪審查曾指出 f881538 未明確設定 AppOps Allowed；最終 9155464 相較 f881538 只加入這一行 stub。既有 fixture 已設定健康檢查通過及停用步數同步，最終 VM suite 再次真正執行通過，原證據疑點已補正。

- **Dismiss bridge：** 測試捕捉真正 JoystickController 註冊的 listener，先建立 VM 的 credit pending，再觸發 callback；驗證 dialog 清除、晚到 startWithoutStepSync 不啟動且不扣次數。這補到 F2 的 VM 取消接線。

- **Dismiss evidence limit：** manager 是 mock；測試未真正開窗或啟動 ticker，isJoystickEnabled 原本就是 false。故不宣稱這個測試證明 true→false、ticker 停止或系統撤權；manager callback/owner 測試及上層同步 cleanup source 判讀仍沿用前報告。

## Evidence and remaining work

- 直接檢查兩段精確 commit diff、fixture、VM permission gate 與最新 XML。最終 9155464 的 focused MapViewModelTest 為 13 tests、零 failures/errors/skips，`/private/tmp/floating-vm-final.log` 為 BUILD SUCCESSFUL in 6s；兩個新 testcase 實際執行。

- 必要 `test/lintDebug/assembleDebug` 全部 exit 0 的證據屬前一個 f881538，見 `/private/tmp/floating-delivery-checks.log`：coordinator 統計總共 274 tests、零 failures、60 skips，相關五個 suite 共 32 個實際執行通過。最終只改一行測試 fixture；沿用未變產品程式的 build/lint 及其餘測試證據，沒有聲稱最終 SHA 又重跑全部 suite。Reviewer 沒有自行重跑測試。

- Lint 仍有既有 34 errors、95 warnings、3 hints，exit 0 不表示乾淨 lint。初始化 fixture 修正及此次綠燈不取代裝置測試。

- R1/R5/R6 的浮窗/小地圖/IME/背景觸控，R3/R4 的完整 route/control 裝置流程，以及主地圖四項 UX 仍按原 spec 完成並記錄。尚無新的手機證據，不能把既有同簽章安裝、資料 375/6/591 與主地圖可見当作這些接受条件通過。

- 三次審查到此收束；前兩份報告未改。後續必要 commits、notebook、STATUS、route records、behind-main 與 draft 交付由 coordinator 維護；不新增無關修正或自動擴大 review。reviewer 只寫本檔，未改 source/log/Git/config，未操作手機、網路、測試或委派；shared filesystem 並非強制唯讀隔離。

Self-check: 固定精確 Reviewed commit；本次僅評估新增測試，重用未變實作的 R/INV 審查；測試支持與限制分開；Code Outcome/Minimality/Conformance PASS 與 Acceptance BLOCKING 明確分開；無 Result Go/合併建議；只寫 review-final.md。
