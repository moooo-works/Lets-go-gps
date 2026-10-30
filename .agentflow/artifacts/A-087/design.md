# 1.4.4 發布、合併與空間清理

原始授權：「改版號後上架，git合併，然後清理多餘資料，釋放出空間」。沿用既有正式發布流程；Play 實際版號核對後選下一可用 patch。將 PR66 與 PR67 相關 GPS 服務修正整合，在目前最新 main 衍生的分支驗證後提交合併；不覆寫既有 App 資料。簽章及 Maps key 僅由既有私密來源注入。

## Invariants

- INV-1：合併後保留兩個修正的保證：停止時完成 provider 清理、取得新鮮非 mock 定位，以及快速啟停不以過期 startId 停掉新服務；清理期間 START 必須有明確終止／重播語意。失敗時不得發布。
- INV-2：正式包保持原 applicationId、正式簽章及 production 廣告／非強制 Pro；不更改權限、安全鎖、Cloud 白名單或使用者資料。簽章或版本不符則停止上傳。
- INV-3：清理只刪除已盤點的可重建專案 build／本任務重複暫存，以及 ~/.gradle/caches/8.8、8.11.1 兩個未使用的舊版快取（刪前再查程序與檔案使用；其他版本／modules-2／wrapper 保留）；保留 keystore、設定、完整 Git／工作紀錄、最新 AAB 與 mapping、必要恢復副本。量測實際刪除位元組及磁碟可用量；未證明可丟者保留。

共用規範已讀；.agentflow/rules.md、sys-invariants.md 不存在，不新增重複 registry。

## Acceptance criteria

- AC-1：Play 正式版及已用版號可見核對，Gradle 與正式 AAB 顯示同一新版本；四語更新說明包含本次修正。
- AC-2：整合結果 test／lintDebug／assembleDebug／bundleRelease 成功，測試 skip 與既有 lint 缺陷明列；競態與清理測試有效執行，受影響實機流程重驗或誠實保留缺口。
- AC-3：獨立審查無未處置阻擋，behind main=0，GitHub 合併可驗，AAB 追溯合併產品樹；正式軌道提交審查可驗，不能把送審當已上線。
- AC-4：清理清單、保留清單及回收空間數字可核對，原 App 與使用者資料保留。

## Validation

Git preflight 已保存於工作回覆工具輸出；合併前重查 main。檢查兩 PR 重疊差異；必跑四項 Gradle。正式 AAB ZIP／簽章／manifest／release BuildConfig 檢查，Play UI 上傳與送審回執。清理前後 du／df 與檔案存在檢查。獨立審查在發布及清理邊界前執行。

## Minimality check

沿用目前 PR、測試與發布程序，僅修補整合造成的衝突／競態；不重構其他功能，不新增發佈平台。不清理其他專案、Gradle 目前使用的版本或共用依賴／wrapper；只移除明列兩個舊版可重建快取，避免資料及活躍建置受影響。

規劃採既有已授權發布模式；Claude CLI 本次前一輪已確認配額耗盡，使用設定 gpt-6.1-sol/high 原生 fresh-context 挑戰（release_plan），主 agent 最終整合。審查權限為只讀指示，非 OS 沙箱。

規劃挑戰處置：release_plan 指出舊 startId 與非同步 teardown 合併會留下空轉服務，已採 Main 序列化、清理期間保存最後 START、清理完成後重播；後續 STOP 取消待重播。新增阻塞 teardown 測試驗證。沿用目前 PR67 合併 PR66 全祖先（其相對 main 僅產品修正／A073 證據，不含舊 notebook 差異），保留 Git 歷史而非新開重複 PR；所有合併衝突均重新驗證。Play 所有最新套件最高10403，採1.4.4/10404。
