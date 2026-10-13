# Unity 內部測試候選

- 原10401已發布並真Play安裝，AdMob測試banner與+6h獎勵觀察成功；Unity單一來源尚未出現。
- 本次使用同一已審查產品程式，以可追溯Gradle init script只替換隔離版位及測試版本資訊。SDK、同意、解鎖與Release Pro false不變。
- 10402／1.4.1-unity-test只能內部測試，禁止升級正式版；之後正式候選需使用大於10402的code。原10401生產AAB及Pro Debug APK保持原檔。

## Invariants

- INV-1: 從已驗證10401 source開始，App與gradle產品檔完全相同；腳本僅override兩個test ad units與code/name。任何其他產品diff算失敗。
- INV-2: 正式mediation groups維持paused，隔離群組只有unity-smoke版位；不得把production adunit加進去。
- INV-3: 私有資料只留手機或private/tmp；375地點、6路線、591路點及模擬設定可匯回。既有importer重建route createdAt已記錄，原完整tar保留。
- INV-4: 真Release不提供永久Pro、不得建立購買；測試成功來源需明確Unity，不把AdMob Fill當Unity。

## Acceptance criteria

- 建置新包後核對metadata10402／test標記、兩個隔離IDs、Profalse及簽章；確認原10401hash不變（INV-1,4）。
- 內部測試單獨標明test-only並Play更新到10402，與production10401分別記錄（INV-2）。
- 在實際App流程或Inspector中確認Unity init／banner／reward來源及顯示；記錄失敗code與未驗證項。至少正向reward，提前取消需在reward前操作才能算驗證（INV-4）。
- 核對原資料內容，關閉single-source診斷、恢復原外觀／mock設定；正式群組不啟用（INV-2,3）。

## Minimality check

- 最小結果是隔離驗证新App Unity功能，同程式碼測試版只改ad units與版本metadata。
- 已嘗試不建新包：Inspector專用版位320×50可請求但仍只有AdMob；正式App請求仍走paused正式Unity群組。不能為測試把Unity開給正式流量。
- 不改SDK／初始化策略、不改同意或獎勵產品行為，避免將未證實的原因當修正。
