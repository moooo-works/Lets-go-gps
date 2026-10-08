* _2026-10-08 16:07:12 +0800 (gpt-6.1-sol/high)_

# Floating companion contract

- **Specification: PASS，依下列必要契約實作。** 功能範圍與既有架構可銜接，不需要新 DB 或依賴。

- **Acceptance: BLOCKING，尚未有執行證據。** 新動作若直接接 load/add/service helpers，或未補焦點/owner 清理，不符合需求；IME 與 map 必須實機驗證。

- **下一步。** Host 在新增浮窗入口完成 S1–S5 與 S6證據，保持主頁/廣告流程改動僅限必要接線。

## S1 — State and view

- 新 controller 管理展開、分頁、查詢、folder/favorite 篩選、選點、待走列表及確認狀態；只讀既有資料 Flow。純參數 Compose view 接資料與 callbacks，不建立 Hilt/ViewModel/NavHost。

- 沿用 Pro 搖桿入口、拖曳吸邊與原控制；分頁切換/收合先清搖桿方向。泡泡關閉僅關工具，停止模擬仍由明確停止按鈕執行。

- 待走列表與活動 waypoints 分開；首次加入先從既有 route 建副本（或清楚顯示獨立列表），直到明確套用才修改引擎。選取不切 mapMode、不呼叫 onCameraMove 驅動模擬、不寫 repository currentMockLocation。

## S2 — Query, selection and display

- 明確搜尋/貼上先用 LocationQueryParser；地名走現有 SearchRepository。短 PlusCode 使用查詢提交時明確的 reference，不能依晚回時的中心變化重新解讀。保存查詢序號並忽略舊 success/failure；取消 job 不能單獨替代序號檢查。

- 收藏/資料夾來自 LocationRepository；需要交叉篩選時使用現有資料做交集，不改 DAO/schema。存收藏保留名字/描述/座標；錯誤呈現不觸發定位。

- 目前座標取 `isMocking && currentMockLocation != null`，其餘顯示未啟用/未知；選中座標獨立顯示。複製目前/選點使用各自來源，固定 Locale 小數點及負號；無已知來源則停用按鈕。

- 今日寫入/上限取既有 SettingsRepository Flow，次數取既有 state。小地圖 camera、點擊與 marker只更新新選點；不接 RouteController.addWaypointAt。保留 GoogleMap 初始化及 remember marker/icons，準星置中於地圖容器，沒有因移動中心新增藍 pin。

## S3 — New command boundary and replacement

- 浮窗 UI 只能呼叫 VM 新的受檢入口；VM 重用既有 permission/error、health、StepSyncGate及 RouteController 能力。不可從 UI 發 service intent、呼叫 start*Service helper，或改 currentMockLocation 來完成定位。

- 明確執行捕捉不可變 payload：動作類型、選定座標/路線點副本、確認對應的活動route身分/版本。非同步健康查詢、route讀取及回App期間仍用原 payload，不讀改過的 center/selection。payload與請求token可放新controller/VM私有資料，不需DB。

- 路線判斷含 PLAYING、PAUSED及仍 isMocking 的已完成route。定位、探索替換、load route/套用待走列表需明示取代確認；取消不 stop、clear、setRoute、dispatch或扣次數。讀取route可先做，但套用前再確認活動route/請求仍相同；改變時重新要求確認或明確拒絕。

- 確認後先通過全部新入口 gate，才 dispatch single/替換；利用 existing SINGLE service handler 停 route，不為了通過 startMocking 的 isMocking guard 先停止或切 mapMode。新動作失敗/被擋不能預先破壞活動route。

- pause/resume/stop沿用既有命令；resume接 ACTION_RESUME_ROUTE。速度及循環沿用現有 controller，不用 setRoute達成。明確 stop 才清待辦/方向，禁止晚回結果自行重新啟動。

## S4 — Gates, return to App and new pending resume

- 新浮窗功能執行時重查 Pro（需要 Pro 的功能）、fine/coarse/notification及 AppOps；NotAllowed、CheckFailed與engine failure保持原分流。缺資格只顯示原因/回App入口，禁止自動跳設定或在浮窗新增廣告/購買流程。

- 新啟動與新待辦恢復執行前，套用現有健康檢查語義並重新判斷必要資格。StepSyncGate 的 null 必須中止；步數關閉可不啟用同步；訂閱免次數，ad-unlock仍不能免計次。不對已在播放的 pause/resume重收一次session次數。

- 次數不足保存本次新浮窗 payload；明確「回App」開原dialogs。原不用同步/廣告成功callbacks僅在新待辦仍有效時恢復該payload，並走同一受檢入口；主頁既有pending路徑保持既有行为。回App本身不停止活動route。

- 明確關閉工具、取消dialog或VM onCleared清新待辦/token；非同步搜尋/route回傳及廣告成功不能恢復已失效動作。收合不等於取消；明確回App需保留有效待辦，不能用一般hide副作用將其丟掉。

- 被擋/取消新動作零扣次數；成功dispatch沿用既有consume方法，不能重複扣款。現有方法不等於engine成功確認，不在本次擴充所有啟動/計費制度。

## S5 — Window, IME and map owners

- Manager持有每次show的 owner及ComposeView；在attach/content前安裝 Lifecycle/SavedState owner，遵守restore/create/start/resume次序。不透過overlay owner创建任何SavedState/Hilt VM。

- 輸入request才使window可focus；非輸入、換頁、收合與hide清Compose焦點/收IME並恢復NOT_FOCUSABLE。布局仍只攔截面板觸控，不佔整個螢幕；focusable階段也要可明確離開輸入。具體flags/softInputMode由裝置驗證決定。

- hide必須可重複呼叫：清方向、disposeComposition、移除window、owner pause/stop/destroy與ViewModelStore.clear，並清references。add/update/remove失敗或overlay permission撤銷走同等清理，state不能假裝仍開啟；window操作在主線程。

- 可用區域考慮系統列、旋轉及IME；大小限制在可用區域，位置上界至少0，避免view大於screen時coerceIn下限大於上限。儲存位置讀回亦夾限，沒有冷啟動自動show。

- Maps Compose從同一window獲得有效owner；只有地圖分頁建立map，關閉釋放。這是待驗證設計，不宣稱只安裝owner就已解決無Activity MapView/IME問題。

## S6 — Coverage ledger and proof

| Requirement | Spec sections | Invariants | Required proof |
| --- | --- | --- | --- |
| R1 | S1、S4、S5 | INV-3、4 | Pro/overlay denial；泡泡開合拖曳/原搖桿 |
| R2 | S2 | INV-1、5 | 查詢競速/舊failure；parser/交叉篩選/貼上 |
| R3 | S1、S2、S3、S4 | INV-1、2、3、5 | 選點零副作用；cancel保持route；不可變target與route晚回 |
| R4 | S3、S4 | INV-2、3、4 | gate拒絕零dispatch/consume；新pending失效/授權改變；pause/resume不重啟 |
| R5 | S2、S5 | INV-1、5 | 無mock/來源分開；負號/Locale；真實小地圖選點 |
| R6 | S1、S4、S5 | INV-3、4、6 | 清理/permission撤銷；背景IME/旋轉/重複啟閉；冷啟動 |
| 全部範圍 | S1–S6 | INV-6 | 獨立diff、資料計數、Git及Gradle/裝置/外部驗收記錄 |

- **必要自動測試。** 新controller查詢競速、選點零引擎副作用；VM新commands確認取消/目標snapshot/重新gate/late callback；真實執行JUnit及零service/consume驗證，不只assert UI旗標。manager測試覆蓋show失敗、雙hide、owner/composition清理及尺寸上界。

- **IME驗證條件（BLOCKING至實測）。** 指定 Pixel7Pro 非遊戲背景App：輸入文字可送出；收合、離開輸入、關閉後鍵盤與焦點消失、背景可點；portrait/landscape及反覆開關不崩潰、不遮住整螢幕。記錄裝置/API與結果；Robolectric不能替代。

- **Map驗證條件（BLOCKING至實測）。** App離開前景後工具map圖資可見、移動/點擊只改選點；回主頁/旋轉/重複開關無owner/MapView崩潰；主地圖pin、準星、description泡泡、列表animate置中與座標更新四項仍正常。地圖key只留ignored local設定。

- **交付門檻。** Host依AGENTS記錄最新main/behind=0、test/lintDebug/assembleDebug結果、before/after資料計數及外部接受。已知lint基線需明示實際結果，不能把Gradle exit0當零lint errors；不得順手修其他主題。装置權限不足如實記錄，未驗證的AC保持未完成。

## Verdict / necessary changes

- **Requirements/spec design: PASS。** 已捕捉範圍可實作；本版限定安全新增入口，不要求全面重構主頁/廣告路徑。

- **Required before acceptance: BLOCKING。** (1) 新pending snapshot與有效性token；(2) 活動route確認且取消零副作用；(3) dispatch前重查新入口gate；(4) window焦點/owner/尺寸清理；(5) 真實IME/map及必要命令測試證據。這些都是已授權結果所必要，不是新增產品行為。

Self-check: R1–R6→section→INV ledger完整；新增pending修正範圍受限；IME/map未實測明示BLOCKING，未將推論寫成已完成。
