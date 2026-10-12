# Tracker

## Identity

- **Work key:** A-051-floating-layout.

- **Active Ask:** A-051.

- **Goal:** 依Pen設計實作浮窗UI，修正收合靠邊與透明觸控攔截.

- **Last update:** 2026-10-12 19:49:04 +0800.

- **Evidence commit:** 85ca56486538913ff64cf17e86d75eace4173579.

## Overall state

- **State:** active.

- **Reason:** Work remains.

- **Total:** 5.

- **Completed:** 0.

- **Remaining:** 5.

## Accepted task checklist

- [x] **T-1:** Pen協作重新規劃UI，並依使用者「實現未完成項目」實作：48dp圖示、toolbar更多、五個固定入口、內容優先、次要額度、控制鍵可見。保留原actions/gates/多語。Proof: pen .pen/PNG、最終Compose儀器及手機各頁流程. Source: A-051.

- [x] **T-2:** 修正展開後收合停留原地；scope Manager dock anchor與VM收合接線，先紅後綠、required Gradle及手機左右側實測，保留完全close。Source: A-051.

- [ ] **T-3:** 獨立最終審查、single-topic draft PR與資料設定保留、紀錄push，未完成pen則維持open不假稱完成。Source: A-051/AGENTS/Agentflow.

- [x] **T-4:** 收合/換頁後黏著側底層App按鈕可點，先Calculator實機red、最小focus/padding修正、IME與底層綠驗證。Scope: FloatingCompanionView及必要接受紀錄，不改系統安全。Source: A-051 新增觸控回報.

- [x] **T-5:** 分開懸浮工具／搖桿按鍵，依使用者回覆一次顯示一種，沿用訂閱或6h廣告Pro。Scope: MapState/MapScreen/MapOverlays、VM/Controller/View、ProDialog/four locales及必要VM/UI tests。Proof: live repository拒絕免費或已撤销、切換flags互斥／window先移除、Pro撤销清理與公開UI收合／close；既有paid/ad來源不改。Source: A-051.

## Accepted scope changes

- 使用者要求背景增加透明度：expanded82%、entry90%，保留文字／輸入／操作清晰度；Effect: 覆蓋原Pen實色背景。Source: A-051.
- 使用者要求修正路線模式辨識：可見一次／循環／折返文字及對應圖示，沿用四語與原cycleLoopMode。Effect: CompanionView/UI instrument新增模式文字辨識。Source: A-051.

- 分開懸浮工具／搖桿入口，依使用者較新回覆採互斥切換與各自state，兩者沿用訂閱或6h廣告Pro。Effect: 增加MapState／MapScreen／MapOverlays、ProDialog及四語與權限／撤销測試；不改付費／廣告來源。Source: A-051.

## Current recovery

- **Current item:** T-3.

- **Last proven result:** required Gradle通過284/0fail/60skip；最終IME與實際window bounds／模式／兩面板close實機OK1；資料mockgps.db rows/hash一致。詳見device-acceptance.md與data-preservation.json。

- **Active blocker or running process:** 無；即將固定root證據並發出最終只讀審查。最新跨App逐鍵測試與原主題差異已明列限制，沒有假稱全部手機設定一致。

- **Next safe action:** full native review、host核對、draft PR與closeout；只重用有效證據，不再操作正在使用的手機。

- **Expected changed files:** Manager/VM/ManagerTest、MapState/MapScreen/MapOverlays、CompanionState/View、OverlayTheme、JoystickController/View、ProDialog、four locale labels/platform及VM/UI tests；A051設計／接受記錄。拆分平台／UI／語系／驗證子項，不混ads/engine/schema.

## Completion proof

- **All accepted tasks checked:** no.

- **Blocking accepted decision:** none.

- **Operation running:** no.

- **Next action remaining:** T-3.

- **Evidence status:** current.

- **Judgment:** active.

## Update meaning

- Saving this tracker is a recovery checkpoint, not a stop signal.

- For completed work, Evidence commit names the Git evidence commit, or is not applicable in a plain folder. Local file and test proof is still required.

- Work continues with the next unfinished item unless an independent stop condition applies.
