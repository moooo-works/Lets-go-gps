* _2026-10-08 12:30:41 +0800 (inherited/inherited)_
Reviewed commit: ec525396d12fb62bde86a69f0eea752651bd7479
Original reviewed commit: 430975dbdeca6a5b43413d7037d700333523fe5a

**截圖與說明已一致，原證據缺口解除。**

- 兩張新圖分別顯示正確 Banner／Rewarded 群組、ID、Android 格式與已暫停；文件已區分圖片能直接證明的內容與主機 UI／AX 觀察。

- 三項設定與 App source 未變的前次檢查可沿用，不需重跑 Gradle。

- 群組維持暫停，R-7 實機由使用者保留待驗證；本次 PASS 不代表 Unity 實際供應或完整 pipeline 完成。

Outcome: PASS
Minimality: PASS
Conformance: PASS

## Finding closure / evidence

- EVID-1 已解除。banner-paused.jpg 是編輯已存在群組畫面，顯示 Let's Go - Unity Banner Android、8708929545、橫幅廣告、Android、所有國家／地區、已暫停。

- rewarded-paused.jpg 是不同群組畫面，顯示 Let's Go - Unity Rewarded Android、8676901314、獎勵廣告、Android、所有國家／地區、已暫停。兩張均不包含帳戶email或其他群組收益。

- 文件不再聲稱 map-ad-2／no-ad-6h 與來源列都在照片；這些細節明列為主機保存頁可見 UI／AX 核對。初次保存成功亦是主機觀察，非新圖片中的toast。Game ID／Placement ID、合作關係及來源配置不由這兩張上方圖片獨立證明，未擴大證據範圍。

- 重用未改 groups-paused.jpg 的兩行列表及暫停圖示；重用前次來源區可見兩來源、有效合作狀態、空waterfall檢查，但不把它誤認為群組ID／單元證據。原錯誤圖片与BLOCKING報告保留Git history，初查報告未覆寫。

## Scope / minimality

- ec525396 僅一份後台文件及兩張圖片，沒有新後台設定或產品修改。前次 auto-reply:on、away-gates:on、streams:off 唯一三值差異與 app／gradle無差異結論仍有效；沿用269 tests、security、manifest及既有lint證據，不跑Gradle。

- 合作關係及mapping在接手前存在、本輪未接受條款與未操作既有Mintegral，仍是主機CUA觀察；文件沒有改成聲稱我們接受。PR58 open/draft沿用主機gh確認，本次不操作PR。

- 實際考慮將兩張上方畫面合併一張：会弱化不同ID／格式與暫停狀態的逐群組核對，因此保留兩張。列表證據重用，不另加重複圖片；文字只修正原錯誤描述，沒有擴張後台工作。

- 文件仍區分paused/prepared與供應，保留CMP地區訊號、Unity單一來源banner/reward、map/mock smoke與實際供應未驗證。使用者「先保留待驗證」不算R7通過，也不允許生產供應。

- native共享主機權限與context；read-only為assigned限制，非OS隔離。本次唯一repo寫入為新report，未替換舊報告、委派、操作GUI或修改其他檔案。

Self-check: 精確ec525396SHA與三檔diff、兩張圖片的name/ID/type/platform/pause已逐一核對；EVID-1解除，舊報告保留，三值設定及source未變證據沿用；PASS限於已發生配置的記錄與證據修正，R7及完整供應未完成。
