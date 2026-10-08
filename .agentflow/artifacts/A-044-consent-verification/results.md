# 完整App廣告隱私流程測試結果

完整App的同意、拒絕、重啟與設定頁修改選擇已在Pixel7Pro驗證，未發現需新增產品修改的問題。手機已恢復一般測試版與原資料；這份結果不代表Unity已能供應廣告，也不代表正式App已發布。

## 測試來源與範圍

- 日期：2026-10-08；裝置：Google Pixel7Pro，Android17。
- 獨立worktree：`.worktrees/consent-verification`；分支`fix/ad-consent-verification`；來源main `c021bb8f159ed73fa5368854ff1d8cb91beacede`。
- 既有Unity提交`5adca01`、`fb44cd7`、`7fa932f`依序帶入後，測試來源為`88bc790`；app及gradle內容與unity-ads完全一致。沒有編輯另一session的工作區。
- 完整App暫存測試版使用正式AdMob AppID、Google範例橫幅／獎勵單元、僅登記測試裝置的EEA模擬及狀態日誌。Maps設定由已驗證測試版的本機設定沿用；金鑰未記錄或提交。DEV_FORCE_PRO=false。
- 所有地區模擬、測試裝置註冊與日誌只在私有tmp；產品來源未新增這些設定，沒有新產品程式提交。

## 實機觀察

| 情境 | 結果與證據 |
|---|---|
| 首次需要選擇 | 真正Let's Go GPS顯示正式同意／不同意／管理表單。選擇前日誌只有gather allowed=false，沒有GMA初始化；表單後方地圖正常。 |
| 首次拒絕 | 14:30:25回呼無錯誤；UMP status=3、canRequestAds=true、privacyOptions=REQUIRED。個人化用途旗標全0，Unity vendor consent=0、Mintegral=0。Google測試橫幅可載入，地圖功能未鎖住。status3表示已完成選擇，不代表接受個人化。 |
| 拒絕後重啟 | 14:30:54仍status3／REQUIRED，不重問；檢查完成後初始化GMA，測試橫幅正常。 |
| 拒絕後獎勵廣告 | 從設定頁觀看Google測試廣告，畫面顯示已發放獎勵，App取得6小時解鎖。這是範例廣告功能測試，不證明正式供應商填充或非個人化廣告分類。 |
| 管理選項 | 設定頁顯示「廣告隱私選項」，可開啟Manage options；Confirm choices保留用途旗標全0。 |
| 拒絕改成同意 | 設定頁再次開啟並Consent，用途旗標全1、Unity consent=1、Mintegral仍0；14:32:15完成，revision從2到4再6。程式對Unity仍一律保守設定privacy.consent=false，不推定已驗證個人化供應。 |
| 同意後重啟 | 14:32:28不重問，UMP仍status3／REQUIRED。 |
| 同意改回拒絕 | 重新開啟設定入口並Do not consent，用途旗標恢復全0，Unity=0、Mintegral=0；返回地圖可重新出現測試橫幅。 |
| 首次直接同意 | 重置僅測試的UMP資料，14:34:11開始為allowed=false；14:34:35點Consent才完成與初始化GMA。 |
| 廣告失效／載入阻擋 | 實機確認選擇前不初始化、修改選擇可完成與返回廣告。快取失效、回呼競態、失敗時狀態及獎勵回呼攔截由下述30個既有廣告單元測試覆蓋；未把畫面觀察當成網路封包／供應商完整合規證明。 |
| 恢復一般地區版本 | 裝回測試前APK、還原原資料；地圖與測試橫幅正常，隱私權政策可見而「廣告隱私選項」不顯示。正式AppID與EEA測試版沒有留在手機。 |

## 驗證與恢復

- 獨立worktree執行`./gradlew test lintDebug assembleDebug`，BUILD SUCCESSFUL，1m38s。
- 全部269個測試：0 failures／0 errors，66 skipped，實際203個執行。其中AdConsentManager 11、AdMobInitializer 4、RewardedAdManager 15，共30個廣告測試全通過且未跳過。
- lint有34 errors、92 warnings、3 hints；34個error的ID、訊息及位置與既有Unity分支報告一致。兩筆版本更新warning訊息隨外部版本資訊變動，未把lint描述成零錯誤或發布門檻已全數解除。
- DB與WAL、原同意偏好及獎勵期限狀態恢復成測試前狀態。原本不存在的ad_unlock.preferences_pb也已清除；測試獎勵不保留。DB回讀前後皆375 saved_locations、6 routes、591 route_points。
- Pixel最後顯示正常地圖、Google Test Ad與鎖定的路線模擬；沒有保留歐洲模擬表單或測試獎勵。
- app/src、app/build.gradle.kts、gradle/libs.versions.toml未包含Mintegral／Mobvista／MBridge。PR58仍OPEN、draft，未修改。Unity正式群組沒有啟用，GameID問題仍待既有工單。
- git diff --check通過；origin/main含AGENTS.md；分支從最新main建立，工作區乾淨。沒有新增PR或請求合併。

## 私有本機證據

- 表單：`/private/tmp/letsgps-fullapp-eea-form.png`、`letsgps-fullapp-first-consent-form.png`。
- 拒絕、管理及恢复畫面：`/private/tmp/letsgps-fullapp-after-reject.png`、`letsgps-fullapp-manage.png`、`letsgps-consent-restored-map-final.png`。
- callback及重啟日誌：`/private/tmp/letsgps-fullapp-rejected.log`、`letsgps-fullapp-restart.log`、`letsgps-fullapp-privacy-final.log`、`letsgps-fullapp-consented-restart.log`、`letsgps-fullapp-first-consent-after.log`。
- 建置結果：`/private/tmp/letsgps-consent-source-checks.log`；完整測試報告位於獨立worktree的app/build。
- 手機APK／資料及TCF偏好均留在私有tmp，不上傳、不提交。Pro略過不必要表單仍屬延後項目。
