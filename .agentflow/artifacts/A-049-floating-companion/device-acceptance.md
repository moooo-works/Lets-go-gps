# Pixel 7 Pro 實機接受紀錄

- Device: 2A151FDH300HUZ，Android17/API37，CP3A.260905.009，1080x2340/density420；其他Pixel不操作。
- Source: Android17修復1ecc8a4；整合63030d7；IME修正c0e520f。最終接受結論由獨立floating-runtime-final.md判定；以下分開實機與單元證據。
- 備份：private tmp原APK/data、pre-floating及pre-android17-fix檔案，不提交。

## 已證明

- Android17 App UID global0而shell1；修前instrument1fail、修後OK1，健康UI全部通過。SDK37 AppOps允許/拒絕/失敗純JUnit全部真正執行。
- 最新完整test/lintDebug/assembleDebug成功；279tests零fail、60既有skip；lint仍34既有Error，非clean。
- IME可見性red：焦點框[21,1310,819,1485]與keyboard[0,1328,1080,2340]重疊，檢查script exit1。修正imePadding後[21,1132,819,1307]不重疊，script PASS。
- 鍵盤Search提交負座標-12.345678,-34.567890，IME關閉，選中顯示6位格式；選點時不啟動service、不改主圖中心。ADB hardware keys在注音輸入法會組注音，因此暫切English輸入測試數字，完成後需還原注音。
- 搜尋/收合後鍵盤false、overlay NOT_FOCUSABLE；背景Calculator可完成2+2=4，浮泡保持可見。
- 背景Calculator上明確定位成功，MockLocationService存在，系統location包含test負座標；浮窗目前座標亦同。
- 選點複製→明確貼上，query由無空白改為格式化comma-space，IME不彈出。改搜尋25.0330,121.5654後選中25.033000,121.565400，目前模擬仍負座標；來源確實分開。

- 小地圖臺北101道路圖資可見，crosshair在map box中心；拖曳改camera為25.032991,121.563300、使用中心後選中名「地圖選點」，目前負座標仍不變，未新增app marker。
- 後續操作發現系統鎖屏，已請使用者解鎖；不繞過鎖屏。

- 鎖屏等待期間已force-stop測試App，MockLocationService不再存在；不留下持續測試定位。備份與checkpoint DB各table rows hash完全相同：375 saved_locations、6 routes、591 route_points、0 folders，無新增測試資料。輸入法仍English，待解鎖還原注音。

## 限制

- 不聲稱遊戲POI識別、遊戲花數、Pikmin實際遊戲測試；使用非遊戲Calculator驗證背景輸入/觸控。
- 真實撤銷系統權限未執行；已有Manager add/update-failure及dismiss取消owner/VM的執行測試，不代改安全設定。

## 解鎖後續測（2026-10-08）

- Source ddeb51c：橫向MAP原本固定區塊使使用中心與控制永久clip；修後僅landscape MAP移進body scroll，portrait原手勢結構保留。Gradle三命令成功，279/0fail/60skip；第一次patch路徑錯誤未改source，該次build不用作修後證據，final-build log才是最新。
- 已存119點路線load只prepare不mock；明確play開始、pause顯示暫停。第二73點路線要求取代；cancel後仍Tokyo原路線paused。第一輪cancel後批次座標隨layout shift誤觸stop，沒有當cancel保持證據；第二輪單獨cancel截圖才證明保持。
- Slider19→48km/h、loop一次→循環與主App同步，resume重新運行；close浮窗後service仍foreground，reopen目前位置繼續更新；最後明確Stop後目前未啟用。
- 名稱Taipei101 remote結果可見，選台北101僅selected；完整Plus849VCWC8+R9解碼37.422062,-122.084063。一次ASCII注入ctrl+A在focus前造成附加文字，host讀回確認後重試，真正EditText正確時才submit並記PASS。
- 台北101與Plus兩點queued→apply只prepare，不play；獨立queue2點保持。Explore selected後目前Googleplex附近位置且route模擬運行；joystick gesture執行、切tab釋放，尚不單憑gesture冒稱ticker movement完全驗證。
- Save同選點兩次，DB只新增1個test row id427，座標37.422062499999996/-122.0840625；原375→376，待精確清理。未改原資料。
- 橫向Calculator panel在2340x1080內，header可close/collapse；修後從hint向上滑顯示使用中心；Map水平drag改camera為37.422053,-122.087326，使用中心可操作。rotation已恢復accelerometer1/user0。safe screenshots private tmp，不上傳原saved POI畫面。

## 最終清理與補測

- Saved query37.422只列新test point；只看最愛後仍可見（save預設favorite），選點更新selected。原資料夾0，沒有為測試新增folder；folder/favorite/name交叉條件及短PlusCode以實際執行controller/parser單元測試為證據，不宣稱真機點過不存在的資料夾。
- 注音輸入法已透過globe還原，截圖有注音字母與空白鍵「注音」；Done釋放focus、close。rotation accelerometer1/user0，無AppOps變更。
- main列表點選test point→主地圖動畫到37.4221,-122.0841並更新座標。清理prepare route後，移動/點圖改center無額外blue pin；crosshair在map可視容器中心。點saved marker bubble標題是儲存名稱37.422062,-122.084063，subtitle是描述849VCWC8+R9，沒有另加latlng snippet。用bubble trash刪除唯一id427。
- 最終DB各table rows SHA256與pre-android17-fix完全一致，375/6/591與folder0；data-preservation.json保存非私密counts/hash。原資料未被刪改，test row不存在。
- Bubble拖曳到右下，frame[816,2057][1080,2208]，展開後[240,702][1080,2277]，在螢幕/導航安全範圍內，header可close。多次展開/收合/關閉/重開均成功；冷啟動只main App、不開浮窗或跳settings。
- Single模式另實測搖桿：第二次水平hold2秒使目前longitude -122.088585→-122.088494（lat37.423763不變），放手後下一張相同，沒有持續漂移。首張狀態初始化25.033顯示尚未穩定，不作位移起點。close浮窗後按main停止，服務檢查無MockLocationService。
- 小地圖blank area click→selected「地圖選點」37.425425,-122.091109；camera仍37.423763,-122.088494、maincenter仍37.4238,-122.0885，目前未啟用；選點無引擎或route副作用。最後浮窗close，Mock service不存在。
- 最終test/lintDebug/assembleDebug279/0fail/60skip、lint34/95/3；必要測試/manager owner失敗及cancel測試重用，沒有把真實撤權或older Android未測當PASS。安裝為debug sample ads/Pro override；不是release發布或Pikmin端到端遊戲驗收。
