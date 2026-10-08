* _2026-10-08 16:07:12 +0800 (gpt-6.1-sol/high)_

# Codewalk and bounded exploration

- **可重用現有資料及引擎，但不能直接把主頁 callbacks 接到浮窗。** 部分入口會停止活動路線，部分恢復入口會重新讀取可變目標。

- **視窗需要補生命週期與焦點。** 現有搖桿視窗不接受鍵盤，hide 沒有明示銷毀 owner；小地圖在這種視窗的行為尚未實測。

- **下一步。** 以新增浮窗動作的安全邊界完成 spec，再用測試及裝置證據接受；不重構所有主頁啟動路徑。

Shared coverage: discovery/codewalk

## Verified paths / public boundaries

以下均實際讀取獨立 worktree 的 source；路徑以 `app/src/main/java/com/moooo_works/letsgogps/` 為根。

| 路徑 / 邊界 | Source fact | 對新功能的影響 |
| --- | --- | --- |
| `ui/map/JoystickOverlayManager.kt`：show/hide/updatePosition/snapToEdge | ApplicationContext 建 ComposeView；裝 Lifecycle/SavedState/ViewModelStore owner；固定 NOT_FOCUSABLE；owner 只存在 show 的區域變數；hide 只 removeView | 已有 owner 起點，不等於清理完整。新輸入需要動態 flags；owner 必須可回收 |
| `ui/map/JoystickController.kt`：toggle/applyMovement/onCleared | toggle 查 Pro 與 overlay permission；每 100ms ticker；SINGLE 且 isMocking 才寫 currentMockLocation；onCleared 停 ticker/hide | 搖桿不需要另建引擎；新收合/換頁必須清方向，不能只隱藏 UI |
| `ui/map/MapViewModel.kt`：startMocking/ensurePermission | startMocking 拒絕 isMocking；健康檢查非同步後再查權限/步數；讀當下 center。ensurePermission 區分 Allowed、NotAllowed、CheckFailed，另有 DeveloperModeDisabled | 新明確定位不能用改 center + startMocking 達成活動route替換；需新受檢入口 |
| `ui/map/MapViewModel.kt`：resumePendingStart/startSingleMock | PendingStart enum 恢復直接呼叫 service helper；single 讀當下 center，未重新查 gate | 不能拿此既有恢復路徑無條件恢復新浮窗選點；新待辦要捕捉並重新檢查 |
| `ui/map/RouteController.kt`：loadRoute/addWaypointAt/clearRoute | loadRoute 讀取後 clearRoute；add/remove 直接 setRoute；clearRoute 停模擬 | 活動路線資料不能作為待走列表；非同步 load 完成也需檢查確認仍有效 |
| `domain/RouteSimulator.kt`：setRoute | setRoute 先 stop() 再設定 points | 呼叫即會停止活動路線，並非純編輯資料 |
| `ui/map/RouteController.kt`：playRoute/startExplorationAtCenter | 有 Pro、permission、StepSyncGate；route 另有一次 battery 提示；public start*Service 是恢復用 dispatch helper | 「重用入口」與「所有 helper 都守 gate」不同；overlay UI 不得呼叫 helper |
| `ui/map/StepSyncGate.kt` | resolve 可回 true/false/null；null 必須中止；訂閱者免次數，ad-unlock 不是訂閱；shouldConsumeCredit 僅判斷扣次數条件 | 保持三種結果與資格語義，不建立新計費系統 |
| `ui/map/MapState.kt` | 分開 center/currentMockLocation/currentLocation；ROUTE_COMPLETED 在 VM 仍 isMocking，simulationState 卻為 IDLE | 判斷活動路線不能只檢查 PLAYING/PAUSED；目前座標不能 fallback 到 center |
| `domain/repository/LocationRepository.kt`、`data/repository/LocationRepositoryImpl.kt` | 已有收藏查詢、資料夾 Flow、observeRoutes、getRouteWithPoints、saveLocation；implementation 委派 DAO | 不需要 schema 或新持久層；DB 查詢和選點皆可與引擎分開 |
| `data/local/LocationDao.kt`：observeSavedLocations SQL 條件 | filterMode 為 ALL/FAVORITES/FOLDER；FOLDER 比對 folderId | 若 UI 要同時按資料夾與 favorite 篩選，不能假設一個 filterMode 自動提供兩者交集 |
| `domain/repository/SearchRepository.kt`、`ui/map/SearchViewModel.kt`、`utils/LocationQueryParser.kt` | repository 回 Result；既有 SearchViewModel 每次 launch 沒序號保護；parser 支援座標及 full/short PlusCode，short 需要 reference | 新 controller 使用 repository/parser並隔離晚回結果；不在 overlay 建 Hilt Search VM |
| `ui/map/MapGoogleMapContent.kt` | isMyLocationEnabled=false；saved snippet=description；route map click 可 add waypoint；MapsInitializer 與 remembered marker/icon 有既有防崩潰措施 | 小地圖不可直接接主頁 route click callback；沿用初始化與穩定 marker慣例 |
| `domain/repository/SettingsRepository.kt` | observeStepDailyQuota/observeStepQuotaUsedToday 已存在 | 今日寫入/上限讀既有 Flow；featureCredits 讀 VM/pro state |
| `service/MockLocationService.kt`：start single/route、resume | SINGLE handler 先停止 simulator，再改目前位置；所有 start 接受 STEP_SYNC_ALLOWED extra，服務視 UI 已完成計費；已有 ACTION_RESUME_ROUTE | 新動作先查 gate/確認再 dispatch；「繼續」接 resume，不用 start route重新播放 |

## Conventions and focused proof

- **已讀測試、未執行。** `ui/map/{JoystickControllerTest,RouteControllerTest,MapViewModelTest,StepSyncGateTest}.kt` 包含 Pro拒絕、單點搖桿寫入條件、route load/clear、permission拒絕及 ad-unlock不能免計次。`data/repository/LocationRepositoryImplTest.kt` 驗證 query/filter 參數委派。

- **缺少新增契約證明。** 以上測試不證明活動 route 取消零副作用、不證明新浮窗待辦重查gate/凍結目標，也不證明真实 IME 或 GoogleMap overlay。不能把 mock manager 的測試稱為視窗實測。

- **計次時間是 source 事實。** VM/RouteController 在 startForegroundService 返回後呼叫扣次數，沒有等待引擎 setup 成功的確認。A049 保留既有制度，但不得在報告聲稱「已證明引擎成功才扣次數」；本次新增被gate擋住/取消的動作必須零扣次數。

## Explore / inference and unexamined

- **推論：IME 可由 manager 切焦點解決。** 輸入時移除 NOT_FOCUSABLE，非輸入時恢復，並清焦點/收鍵盤；必須確認旗標、soft input resize 與 overlay window 在指定裝置實際可用。沒有裝置驗證不能判為 PASS。

- **推論：Map owner 可由同一視窗提供。** show 前安裝可追蹤的 Lifecycle/SavedState owner，hide 反向釋放，禁止 overlay 內 Hilt/SavedState VM。這不證明 SDK 在 ApplicationContext overlay 成功渲染；需要背景 App、旋轉及重複 show/hide證據。

- **有界選擇。** 不新增 Activity、網路來源或依賴來解決問題；若實機證明 Maps Compose 在現有容器失敗，host 先記錄原因再調整本功能容器，不能直接把「回App看地图」宣稱完成 R5。

- **Unexamined。** 未查 SDK 內部實作/本機依賴快取、未讀所有 DAO/DI/Navigation/UI、未完整审查健康檢查/計费实现、未跑Gradle、未查远端分支、未操作手機、未驗證主頁4項UX；这些不是本報告的已證據範圍。

Self-check: 以讀到的 public boundary 支撐判斷；事實、推論與未驗證項目分開；Shared coverage 明示，source 未修改。
