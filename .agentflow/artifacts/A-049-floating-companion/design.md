# 懸浮搜尋、收藏與控制整合

原始要求：A049「好，那將這功能開發到app上」，承接A047功能組合與A048示意及座標說明。實作側邊收合泡泡，包含搜尋、收藏/資料夾、已存路線/控制、探索與小地圖選點，顯示及複製目前模擬座標。使用者已授權實作，auto-reply/away-gates皆on；不需要再次批准同一需求。

## Minimality check

- 最小完整結果：重用目前Pro懸浮搖桿視窗及引擎，以同一面板操作既有能力；選點與執行分開，不建立遊戲資料接口。
- 更簡單替代：只有通知控制無法解決搜尋/收藏/小地圖找點；另做完整懸浮App會重複已有頁面與導航。
- 保留新項目：一個純資料狀態/控制器、純參數Compose面板、視窗焦點/生命週期整合及必要回歸測試。沒有新DB schema、依賴、倒數、下一站、每站停留、蘑菇自動地圖或Unity/AdMob改動。

## Requirements

- R1：既有搖桿入口啟用Pro懸浮伴侶，泡泡可展開/收合、拖曳吸邊及關閉；原搖桿控制仍可用。
- R2：搜尋支援目前地點服務、座標/PlusCode解析及明確貼上；本地收藏分組結果與資料夾/收藏篩選沿用資料。最新查詢結果不被舊回呼覆蓋。
- R3：選中地點顯示名稱/說明/經緯度，可定位、存收藏或加入待走路線；選取本身不執行定位。已有活動路線的編輯/載入/定位替換須確認或明確禁止，取消時完全保留。
- R4：已存路線可載入與既有啟動/暫停/繼續/停止、速度及循環/往返；探索與搖桿沿用。不得繞過Pro、AppOps三態、健康檢查及步數次數。需要完整解鎖/設定時以明確「回App」入口呈現原流程。
- R5：目前模擬座標與選中座標分別標示；無活動模擬不把地圖中心假裝目前位置。顯示今日寫入/上限/次數；可複製已知座標。小地圖採現有Google Map及點擊選點，不新增遊戲POI或讀取遊戲畫面。
- R6：只在輸入時接收鍵盤焦點，收合/關閉後恢復外部App觸控；面板不超出可用螢幕，權限撤銷/Activity銷毁/重複啟閉不崩潰或洩漏Composition/owner。冷啟動不自動開浮窗或跳設定。

## Invariants

- INV-1：起點=選點前有活動模擬；保證=搜尋、貼上、切資料夾、小地圖移動與選取均不改currentMockLocation/route；失敗=沒有明確執行仍改位置。
- INV-2：起點=執行中route；保證=替換須使用者明確確認，取消保留位置/路線/狀態；失敗=load/add/setRoute暗中重設route。
- INV-3：起點=Pro/health/mock/step授權不足；保證=沿用既有gates及錯誤分流，無自動授權/設定/扣款；失敗=overlay呼叫service繞過檢查或展示假許可。
- INV-4：起點=浮窗收合/離開輸入/關閉；保證=沒有鍵盤焦點阻擋遊戲、沒有搖桿殘餘移動、hide清理owner與composition；失敗=遊戲無法操作或殘留tick/window。
- INV-5：起點=目前mock座標與選點不同；保證=顯示及複製各自來源、Locale固定格式含負號；失敗=中心座標冒充目前位置或複製錯來源。
- INV-6：起點=另有PR58、Unity worktree與裝置原資料；保證=單一功能獨立worktree，自最新main建立，不改外部session/廣告/DB schema，不上傳key/裝置備份；失敗=跨題變動或資料遺失。

## Approach / specification draft

- 工作區`.worktrees/floating-companion`，分支`feat/floating-companion`，origin/main基底2c03c72；root session記錄於主devlog，不寫外部stream筆記。
- `FloatingCompanionState/Controller`只管理面板、查詢、篩選及選點；使用LocationRepository/SearchRepository/LocationQueryParser。模擬動作由MapViewModel/RouteController已有入口執行，不在視窗建立Hilt/SavedState ViewModel。
- `FloatingCompanionView`使用純資料與callbacks，控制分頁包裹現有JoystickOverlayView。小地圖是現有Maps Compose组件，讀manager供給的owner；不得建立NavHost外SavedState VM。
- JoystickController保留Pro/overlay權限與移動ticker，加一個wrapper把已有搖桿放進新面板；收合與離開控制分頁必須釋放搖桿方向。視窗Manager處理焦點切換、尺寸範圍與owner清理。
- MapViewModel加搜尋repository依賴、建立controller及wrapper；UI動作通過同一VMgates。定位替換重用ACTION_START_SINGLE的原子停止route逻輯，但只有確認與health/AppOps/StepSyncGate通過才dispatch。若步數credit待辦，捕捉目標以免解鎖後用到改過的中心。
- 活動route的新點先保留在待走清單/要求停止後套用，不直接setRoute打斷。選擇已存route亦先確認取代；route恢復沿用現有service命令。
- 畫面入口名稱改為懸浮工具，保留既有Pro feature gates與四語字串。付費/廣告不在浮窗彈出，透過使用者「回App」按鈕處理原有dialogs。

## Acceptance criteria

- AC1（R1/R6，INV4）：Pixel7Pro非遊戲測試背景App上，泡泡開合/拖曳/關閉可用；鍵盤可打搜尋且收合後背景可點擊；旋轉與重複顯示不crash。源碼owner清理及必要視窗test補強。
- AC2（R2/R3，INV1）：輸入座標與PlusCode、提交一般地名、收藏名/資料夾交叉篩選；舊查詢晚完成不覆蓋新查詢；選取不改定位，複製選點正確。純controller tests及裝置畫面證明。
- AC3（R3/R4，INV2/3）：活動route時定位/載入/套用待走route需確認；取消無service呼叫。明確定位通過全部gates；拒絕權限、失敗AppOps與不足credit不dispatch。不因結果late callback誤用目標。針對command boundaries用真正執行的JUnit tests。
- AC4（R4/R5，INV3/5）：已存route載入，播放/暫停/繼續/停止、速度/循環、探索及現有搖桿可用；目前mock與選點分開，無mock顯示未啟用；今日寫入/額度state沿用。
- AC5（R5/R6，INV1/4）：小地圖圖資可見，點擊只更新選點，無不預期marker；輸入視窗生命周期與主地圖4項UX不回歸。沒有聲稱遊戲POI識別或花數保證。
- AC6（INV6）：test/lintDebug/assembleDebug與獨立外部驗收/防守性review，before/after資料計數，PR单一主题與behind main0；保留PR58與Unity。若現有lint基線仍34errors要如實揭露，不擴充無關修復。

## Boundaries and decisions

- auto-reply safe default：採側邊浮窗，收合泡泡，不同時做第二套底部配置；圖示的兩種配置是候選，並未要求兩套都上線。
- 目前本機Pixel7Pro的mock AppOps deny、overlay default。先完成可檢查的程式與tests，實機測試前若仍未授權需使用者在手機開啟，不暗中改安全設定。Pixel10ProXL未列測試授權，不操作。
- 手機已有備份替換測試版授權，安裝同簽章debug前新增備份；只針對指定Pixel7Pro，保留地圖key於ignored local.properties。
- Required pipeline：full_pipeline；requirements/spec/codewalk使用本次重新查核及獨立設計讀取；探索不開新資料接口，IME/MapView owner需防守性查核及實機驗證；無獨立spike（既有API可复用）；security review合併最終外部驗收；learn不執行（非新事故）。
- Design Go由已on的away-gates在接受requirements/spec及plan commit後供給；Result Go在全部必要證據/外部驗收後同樣處理。沒有待使用者選擇的定價、遊戲接口或新對外channel。
