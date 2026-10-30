# 審查路由與處置

設定要求external優先；Claude因5小時額度限制失敗、exit1，沒有審查結論（external-review-unavailable.txt）。依設定回退fresh-context native，使用acceptance映射gpt-6.1-sol/high。主機未冒充獨立審查者。

原始來源只複製到無遠端、無私密設定的tmp快照；reviewer僅獲唯讀任務，沒有adb或裝置操作，也未修改原始碼。此指派不是OS sandbox。原始／最終快照分別在/private/tmp/gps-a086-review與/private/tmp/gps-a086-review-final；來源身分見source-identity-final.json。

第一次審查提出兩個P2（review-native-first.md），主機採納：非null／播放狀態判斷、同一provider解析器正向對照及有效輸出檢查。加入定位helper來源後重跑整批12項全部通過。

最終獨立審查無剩餘阻擋，見review-native-final.md；主機另核對12個結果碼皆0、無assumption skip，及來源hash吻合。不把靜態審查冒充實機執行。
