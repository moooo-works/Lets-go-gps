# Tracker

## Identity

- **Work key:** A-003-result.

- **Active Ask:** A-003.

- **Goal:** 記錄Design／Result Go、套用三項設定、接續Unity後台準備與實機驗證；PR58保留、Mintegral排除.

- **Last update:** 2026-10-07 23:54:08 +0800.

- **Evidence commit:** ec525396d12fb62bde86a69f0eea752651bd7479.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 4.

- **Completed:** 3.

- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 記錄5adca01 Design Go與7fa932f Result Go；核對source已遠端且無未批准source修改. Proof: A-003 owner原文，7fa932f為已批准source，git遠端39164da包含它，source無diff. Source: A-003.

- [x] **T-2:** auto-reply:on、away-gates:on、streams:off僅套用目前stream設定；設定工具validate及exact diff驗證。Proof: ag-settings validate有效；Git diff僅三個requested值. Source: A-003 settings input.

- [x] **T-3:** 準備保存兩種格式的Unity bidding群組，維持暫停；正確App／Game／Placement／單元，僅AdMob+Unity，GUI保存及截圖驗證。Proof: backend-verification.md與banner-paused.jpg／rewarded-paused.jpg／groups-paused.jpg；ID8708929545／8676901314保存成功且Paused. Source: A-003 Result Go接續整合；主A-026完整接入授權.

- [ ] **T-4:** 接續R-7實機地區同意／訊號／單一Unity來源及map/mock驗證；目前無adb裝置，測試成功前不啟用生產群組。Source: A-003 (沿用批准design).

## Accepted scope changes

- T-4實機驗證延期。Source: A-003「- 先保留待驗證」. Effect: R-7仍未證明，不標為完成；兩個群組維持暫停，沒有背景實機測試。

## Current recovery

- **Current item:** T-4.

- **Last proven result:** source Result Go已批准；三項設定有效；兩個暫停群組保存成功，見backend-verification.md與三張截圖。

- **Active blocker or running process:** 使用者A-003明確先保留待驗證；R-7未證明，兩群組維持暫停；設定／後台證據review已PASS；沒有背景程序。

- **Next safe action:** 已完成本輪紀錄交付；依使用者指示保留R-7待驗證，沒有自動實機測試或生產啟用。

- **Expected changed files:** .agentflow/features/unity-ads/ag.json、stream notebook、A-001與A-002 trackers、A-003-result的tracker／後台證據／截圖／審查紀錄.

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** 無新owner決策；使用者已選擇先保留R-7驗證。

- **Operation running:** no.

- **Next action remaining:** T-4 (使用者延期，待重新接續).

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
