# 懸浮外觀與語系實測

- Pixel7Pro 2A151FDH300HUZ；備份APK/data在private tmp。原設定English/DARK。
- 修前：主App Japanese/LIGHT，浮泡仍「懸浮工具」且深色、低對比，before.png真實重現兩項錯誤。
- 修後：主AppLIGHT、系統深色時浮泡暖白黑字橘框；Japanese標題與小地圖/選中提示同步。切DARK浮泡即時變深色白字、淺橘外框，未重開。
- 仍開著浮泡時Language Japanese→Korean→繁體中文→English，即時更新標題，main與浮窗一致。Android AppUID instrument四語title以及Configuration不變/系統localefallback OK1；/private/tmp/appearance-instrument.log。
- 最新APK含Joystick主題色：深色Control圈/齒輪外框可見，按鈕及current文字高對比。Light/Ja map及Dark/En control圖片均無原saved私密資料，不提交Search頁私密POI截图。
- 搜尋聚焦可輸入、退出後能close。焦點框[261,1132,1059,1307]，IME[0,1328,1080,2340]，原診斷script PASS；沒有修改Google輸入法設定。
- 原English/DARK已恢復，浮窗最後close；MockLocationService不存在，未啟動任何mock。DB375/6/591全表行數/rows SHA256與備份一致；沒有新增test rows或代改AppOps。
- test/lintDebug/assembleDebug最新成功；280 tests，220 executed/0fail，60 existing skips。新色彩對比純JUnit實際執行；onSurface/onSurfaceVariant/primary/selected/onPrimary>=4.5，outline>=3。Lint仍34既有Error/95Warning/3Hint，不是clean。
- 最新source5c9f34f39549b959471a80ecf1af797348d4febc，7files152add16del，base ba2541f（原PR59）；root main沒有新產品程式。未實測遊戲本身、olderAndroid或release付費；背景Calculator/AppSettings已驗證。
