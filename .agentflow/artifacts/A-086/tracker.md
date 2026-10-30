# A-086 Pixel實機回歸

狀態：測試、審查與清理完成，3/3。12項實機情境全部通過，0失敗、0跳過。

## Tasks

- [DONE] T-1 — 建立實際Hilt singleton／Service intent／通知停止的裝置回歸，包含正向mock注入與provider解析對照。

- [DONE] T-2 — Pixel7Pro Android17實際12項全部通過，95.529秒；10次一般啟停、5組快速停止／開始、路線／跳點／探索／傳送探索、原生搖桿拖曳停止、背景與短時熄屏、mock權限撤銷恢復。人工解鎖已完成。

- [DONE] T-3 — 兩個審查P2已修正並整批重驗，獨立審查無阻擋；隔離套件已移除，原App APK前後hash相同，無Health／Cloud／安全鎖設定變更。結果與證據已整理，附於PR67的回歸測試補充。

## Recovery

分支fix/location-recovery，產品基線19bd469／origin-main62b9898，產品來源沒有修改。本輪只新增androidTest及A086證據。沒有進行中的裝置操作或review worker；手機已亮屏解鎖，無mock override殘留。下一步為[草稿PR67](https://github.com/moooo-works/Lets-go-gps/pull/67)審閱；不自動合併或發布。舊devlog/archive及A077/A082/A085保持本機，不混入本輪提交。不可把短時Pixel回歸外推Samsung或所有OEM長時間背景。

證據：result-summary.json、accepted-regression.txt、crash-summary.json、cleanup.json、review-native-final.md。原授權：使用者「進行實機回歸」「可以，提醒我解鎖」「已解鎖」。
