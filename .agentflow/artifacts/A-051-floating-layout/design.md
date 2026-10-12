# 懸浮配置與收合修正

A051兩項原要求：1.重規劃UI並請pen.dev協作；2.展開後關閉文字停原地，需回到邊緣。

## 範圍與方法

初期先交付設計稿與配置說明；Pen完成並取得「實現未完成項目」授權後，已依後續採用章節實作Compose。既有五項搜尋／收藏／路線／控制／小地圖與主題／多語保留。主要操作與座標移到頁內，避免常駐資訊把主內容推下；五個入口全部可见；收合入口小型圖示，拖曳可移動，完全關閉獨立在更多選單，不跟收合混淆。
收合bug獨立修復：Manager維持吸附邊緣狀態，後續真正layout/寬度變化仍計算新邊界；拖曳開始解除吸附。VM收合呼叫snap。既有完全關閉仍hide，不停止背景定位。
pen.dev本機設定0.6.34路徑失效，已找到0.6.49 MCP，tools/list可讀但get_editor_state連antigravity三次失敗；CUA無該app。以上是初期連線診斷；之後改用使用者指定的Pen桌面內建助理完成設計，原始.pen／PNG已保存。

## Minimality check

只有在收合按下立即snap一次不夠：此時仍是展開面板的寬度，縮小後會停內側。延遲固定毫秒也無法保證Compose實際重測時間。沿用現有layout listener和snap機制保存anchor，避免timer/新window/新動畫引擎。

## Invariants

INV-1: 收合不停止定位、不清除搜尋／選點／路線；完全關閉移除window，保持既有安全gates。
INV-2: 主題及四語、輸入焦點釋放、owner disposal保持。
INV-3: 拖曳仍可離開邊緣，結束重新靠邊；不修改原手機資料與設定。

## Acceptance criteria

AC1: 大面板snap後縮成小入口，右側x=screen-newWidth，左側x=0；再次尺寸變化仍靠同側，y在畫面範圍。
AC2: 兩側展開→收合在其他App上不留下中間文字；完全close無window殘留。
AC3: pen.dev設計稿包含收合、搜尋、選點操作／地圖與控制配置；明確區分已規劃與已實作。

## 補充使用者觸控缺陷

A051新增「懸浮視窗黏著的那一邊，底層app的按鈕都會失效，無法點擊，反對側的可以」。Calculator Red：Map Surface實際較短，但WM frame=[240,702][1080,2277]，空白右下方點3不改原顯示4。先查鍵盤padding；加入focus狀態讓只有輸入時使用imePadding，關閉/切頁釋放focus時去掉透明padding；要真機綠證明，不把推測算修復。此子任務只改FloatingCompanionView，既有Manager/VM收合修正獨立已紅綠。

AC4: 未被可見浮窗遮住的左右底層按鈕可點；IME開啟時搜尋欄不被蓋，收起IME／換頁後無透明攔截區。正常安全input/owner不放寬。

pen桌面已signed in，readstate成功但execute/read references逾時；參考桌面已安裝bundle內同版pen-dev schema/execute/mobile指南；使用者明確要求Pen協作，已經CUA送非私密設計brief至Pen內建Claude Opus5.5，已完成可編輯設計及四狀態PNG，實際交付檔已保存。無App code／裝置／帳號操作授權给Pen worker。

## UI實作授權與採用

使用者A051新增「實現未完成項目」明確授權把完成的Pen配置實作到App，不再停設計階段。Pen內建ClaudeOpus5.5已完成4狀態／收藏／路線／更多／長字及橫向提案，原Frame保留，.pen和PNG已保存。host已看四狀態PNG。採用48dp收合圖示、單列toolbar/更多、五個底部入口、內容與選点優先、額度次要呈現。保留既有actions/定位gates／完全close不stop語義；不採用Pen提議的新探索開關／新速度預設／關閉多一層確認／首次說明，因屬新增未必要功能。搖桿原停止仍停止模擬，不改成只停止移動。

## Pen實機對齊修正

Owner新增「你是照著pen.dev的設計稿做成app ui嗎？怎麼看起來不一樣」；host承認前輪只套大結構、未完整還原。現在依.pen數值對齊358dp panel、22dp圆角、6dp padding/gap、48dp toolbar/search、56dp五入口、結果卡/直接複製、選點三次要操作+滿寬52dp主按鈕、220dp map、控制左右排列/三個既有交通模式+滿寬停止。沿用actual GoogleMaps與真實資料，拒絕假距離/ETA/進度與不存在的新增資料夾/改名/編輯浮窗接口。Controller仍完整Stop，不虛稱停止移動保留GPS；遊戲以公開mock-location機制，不接遊戲資料。新onCopyLocation僅既有clipboard方法，複製結果不切換選點，會以instrument callback驗證。選結果自動開MAP預覽，但仍需按Locate才執行。

## 使用者要求半透明背景

A051新增背景透出底層的要求，覆蓋原Pen實色面板規格。展開面板採82%不透明度、收合入口90%；文字、搜尋框及主要操作維持原不透明色。未選導航背景透明，避免疊加實色擋住底層。只改Compose背景顏色，不降低WindowManager視窗alpha、不改觸控flags或權限。此處透明是視覺效果，浮窗覆蓋區仍由浮窗操作；底層點擊驗收針對可見面板之外的区域。

## 路線模式辨識修正

使用者詢問雙向箭頭作用並要求修正。模式按鈕改為文字＋圖示：一次→單向箭頭、循環→Repeat、折返→SyncAlt。顯示既有route_loop_none/loop/bounce四語字串及accessibility stateDescription；仍接原cycleLoopMode，順序與路線引擎不變。實機public UI儀器依序驗證三種可見文字、click callback及回到一次；不啟動定位或播放。

## 分開入口與Pro解鎖

使用者明確要求懸浮視窗與虛擬搖桿的按鍵分開，兩者皆屬Pro，可付費或看廣告解鎖。沿用ProRepository.isProActive（訂閱OR既有6小時廣告解鎖）；不改步數計次、價格、廣告reward或購買實作。已詢問是否同時顯示；先採同時開啟的暫定方案後，使用者回覆「一次顯示一種，開另一種時切換」，最終依較新選擇採互斥切換。

方法：地圖增加獨立懸浮工具FAB，搖桿恢復獨立FAB／控制面板。沿用單一Manager與既有位置prefs，使用獨立isCompanionEnabled／isJoystickEnabled狀態；開新模式前關舊模式，停止旧ticker。控制頁只提供開啟獨立搖桿的入口，仍保留探索操作。每次新開入口檢查UI及repository當下Pro狀態與overlay permission；Pro狀態撤销時關閉目前浮窗並清除搖桿移動，不停止既有定位服務。Pro說明四語增加懸浮工具。關閉及切換清理舊模式pending/focus/state，ViewModel清理時移除唯一window。

Minimality check：只拆開入口與模式state，復用原Manager；不新建service、權限、付費商品或廣告錢包，不把搖桿移動權限改成免費。沿用既有owner、snap、theme／locale以及public window API。

INV-4: 新開任一窗時Pro必須有效；免費或repo已撤销但UI未更新時，顯示既有訂閱／看廣告對話框，無window/ticker啟動。獎勵依原SDK onReward授權，不由UI假造。
INV-5: 同時最多一窗且一種enabled state；切換清理舊模式pending／focus／movement。Pro撤销、VM disposal均移除window並清理ticker，不殘留。
AC5: 免費點兩個入口都進既有Pro對話框；有效Pro兩個不同圖示可切換開啟；開另一種時舊窗先移除。單元覆蓋即時權限、互斥切換與撤销，實機UI覆蓋工具／搖桿收合及close行為。
AC6: 四語Pro清單明列懸浮工具和搖桿；既有paid／ad entitlement持續採同一來源。實際購買／正式廣告不在本次測試中觸發。

Source paths: MapState、MapOverlays、MapScreen、MapViewModel、JoystickController/View、JoystickOverlayManager、ProUpgradeDialog、four locale strings及必要platform／VM／UI測試。root只A051設計／tracker／證據；此新增scope由使用者直接授權，away-gates:on提供符合證據後的Design Go／Result Go。

## 半透明文字對比與接受範圍

82%背景在底層黑／白極值下原次要文字與部分accent／error不足4.5；沿用既有局部palette，次要文字dark B4C0D1／light4F545B、lightaccent9D2808、error darkFFA49C／lightA51C16。新增compositeOver黑／白回歸，四語字串保持完整。

實機公開UI已驗證真實IME、輸入欄不被蓋、收合後實際WindowManager bounds小於64dp、模式文字循環與兩種面板收合／關閉。手機持續有人切換前景，最新跨App逐鍵重測略過，保留早先Calculator同側3→3−與左右邊緣實測及最終window geometry證據；不稱所有Pikmin按鍵均已測。mockgps.db全部rows／hash一致；Google傳輸事件庫屬動態SDK記錄。主題備份DARK→最終LIGHT來源無法確定，保留目前LIGHT與語言zh-TW，不能宣稱所有設定完全一致。
