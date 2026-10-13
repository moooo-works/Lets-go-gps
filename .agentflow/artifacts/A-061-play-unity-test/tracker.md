# Tracker

## Identity

- **Work key:** A-061-play-unity-test.
- **Active Ask:** A-061.
- **Goal:** 發布內部測試AAB並實測Unity廣告。
- **Last update:** 2026-10-13 21:12:34 +0800.
- **Evidence commit:** uncommitted.

## Overall state

- **State:** active.
- **Reason:** 10401已安裝並還原資料；10402隔離測試建置完成，Unity實測待完成。
- **Total:** 3.
- **Completed:** 2.
- **Remaining:** 1.

## Accepted task checklist

- [x] **T-1:** 將A060簽章1.4.1／10401 AAB發到Play內部測試；只修改internal track release3，不改正式版草稿與既有PR。以Play顯示版本及提供內部測試人員狀態為證；若拒絕先記錄原因，不重傳不同包或擅升版。Source: A-061. Proof: Play internal track 4701480342138425252 release 3 visibly lists 1.4.1 Unity Ads internal test as available to internal testers, published 2026-10-13 12:43; actual uploaded version 10401.

- [x] **T-2:** 保存Pixel既有資料並安裝實際測試版本；先確認可還原路徑／簽章，私有備份只留本機，絕不提交。以版本及安裝來源、資料保留結果證明；不能安全替換則保留現有App並記錄阻塞。Source: A-061. Proof: data-restore.json; Play10401 installer com.android.vending,375位置/6路線/591路點，既有importer重建route createdAt，原private備份保留。
- [ ] **T-3:** 確認Unity實際AdMob載入／顯示／reward與取消行為，檢查同意及崩潰；排除Mintegral，不擅全域啟用正式群組。以單來源診斷／實際流程證明，無廣告需分清供應或程式錯誤；未驗項不得標成功。Source: A-061.

## Accepted scope changes

- Change: 隔離10402／1.4.1-unity-test建置並僅上內部測試；兩SMOKE版位替換，產品source不變，原10401保留，下一正式code須>10402。 Source: A-061上傳測試版及繼續授權。 Effect: 僅內部測試版，T-3增加真App隔離版位驗證。

## Current recovery

- **Current item:** T-3.
- **Last proven result:** Play-installed 10401 verified; AdMob Test Ad banner and rewarded +6h observed; JSON import of 375 locations and 6 routes performed.
- **Active blocker or running process:** Pixel10402 Play-installed; UnitySDKinit/token success; waiting physicalshake forInspector source proof.
- **Next safe action:** Inspect Unity mediation source and test banner/reward with testdevice; retain all private data.
- **Expected changed files:** .agentflow/devlog.md; .agentflow/artifacts/A-061-play-unity-test/tracker.md; 必要測試證據。

## Completion proof

- **All accepted tasks checked:** no.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** T-3.
- **Evidence status:** current.
- **Judgment:** active.

## Update meaning

- 保存tracker不代表任務完成，繼續未完成項目。
