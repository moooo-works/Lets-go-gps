# Tracker

## Identity

- **Work key:** A-061-play-unity-test.
- **Active Ask:** A-061.
- **Goal:** 發布內部測試、實測Unity，完成正式10403發布及更新說明。
- **Last update:** 2026-10-14 08:00:51 +0800.
- **Evidence commit:** 981977cd61204a5bc68e942b9271794b3b599331.

## Overall state

- **State:** complete.
- **Reason:** 內部實測、正式候選及送審/自動推出設定完成，Claude最後接受PASS；Google審查仍待外部核准。
- **Total:** 5.
- **Completed:** 5.
- **Remaining:** 0.

## Accepted task checklist

- [x] **T-1:** 將A060簽章1.4.1／10401 AAB發到Play內部測試；只修改internal track release3，不改正式版草稿與既有PR。以Play顯示版本及提供內部測試人員狀態為證；若拒絕先記錄原因，不重傳不同包或擅升版。Source: A-061. Proof: Play internal track 4701480342138425252 release 3 visibly lists 1.4.1 Unity Ads internal test as available to internal testers, published 2026-10-13 12:43; actual uploaded version 10401.

- [x] **T-2:** 保存Pixel既有資料並安裝實際測試版本；先確認可還原路徑／簽章，私有備份只留本機，絕不提交。以版本及安裝來源、資料保留結果證明；不能安全替換則保留現有App並記錄阻塞。Source: A-061. Proof: data-restore.json; Play10401 installer com.android.vending,375位置/6路線/591路點，既有importer重建route createdAt，原private備份保留。
- [x] **T-3:** 確認Unity實際AdMob載入／顯示／reward與取消行為，檢查同意及崩潰；排除Mintegral，不擅全域啟用正式群組。以單來源診斷／實際流程證明，無廣告需分清供應或程式錯誤；未驗項不得標成功。Source: A-061. Proof: unity-runtime-results.json/final-acceptance.json/Inspector FillUnityAds圖；正向banner/reward+6h與關閉single-source已完成，提前取消/exactlyonce/Europe未驗明示限制。

- [x] **T-4:** 從最新origin/main建立獨立production worktree，承接A060source只升1.4.3/10403，正式IDs及Profalse；必跑test/lintDebug/assembleDebug/bundleRelease、實包/簽章與Claude審查，更新說明zh/en/ja/ko。Source: A-061「完成後將測試版升級為正式版，更版說明記得寫」。 Proof: production-build.json/production-review.md，source31b528c/ROOT915bd58審查PASS，311tests0fail60existingSkip，lint命令exit0但34existingerrors明示。

- [x] **T-5:** 發布10403 internal更新Pixel保留資料，再正式release與Unity正式groups啟用；核對實際Google審查/發布狀態，不能直接promote10402、不mergePR58。Source: A-061正式發布授權。 Proof: production-rollout.json/production-phone-smoke.json/Console送審圖及groupsReady；100%已送審，managedoff核准後自動推出；Google未核准，不稱已live。

## Accepted scope changes

- Change: 隔離10402／1.4.1-unity-test建置並僅上內部測試；兩SMOKE版位替換，產品source不變，原10401保留，下一正式code須>10402。 Source: A-061上傳測試版及繼續授權。 Effect: 僅內部測試版，T-3增加真App隔離版位驗證。

- Change: Owner追加正式release及更版說明。 Source: A-061「完成後將測試版升級為正式版，更版說明記得寫」。 Effect: 新production-design、T-4/T-5、10403正式候選及formalUnitygroup啟用；不直接升10402。

## Current recovery

- **Current item:** none.
- **Last proven result:** production-phone-smoke.json; formal審查中/managedoff;formalUnitybanner及rewardready/googlefallback;10402未promote.
- **Active blocker or running process:** none.
- **Next safe action:** none.
- **Expected changed files:** .agentflow/devlog.md; .agentflow/artifacts/A-061-play-unity-test/tracker.md; 必要測試證據。

## Completion proof

- **All accepted tasks checked:** yes.
- **Blocking accepted decision:** none.
- **Operation running:** no.
- **Next action remaining:** none.
- **Evidence status:** complete.
- **Judgment:** complete.

- **External status:** Google審查待核准，managedpublishingoff核准後自動推出；正式送審操作完成不等於已公開上線。

## Update meaning

- 保存tracker不代表任務完成，繼續未完成項目。
