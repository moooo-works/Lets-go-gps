package com.moooo_works.letsgogps.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 計次閘門的分支覆蓋。純 JVM，不依賴 Robolectric。
 *
 * 這裡最關鍵的是「ad-unlock 使用者仍須付費」那幾則——`isProActive` 包含
 * ad-unlock，用錯旗標就會讓免費使用者白嫖。
 */
class StepSyncGateTest {

    private fun stateOf(
        stepSyncEnabled: Boolean = true,
        isSubscriptionActive: Boolean = false,
        isProActive: Boolean = false,
        featureCredits: Int = 0,
    ) = MutableStateFlow(
        MapUiState(
            stepSyncEnabled = stepSyncEnabled,
            isSubscriptionActive = isSubscriptionActive,
            isProActive = isProActive,
            featureCredits = featureCredits,
        )
    )

    // ── 設定關閉 ──────────────────────────────────────────────────────────

    @Test
    fun `步數同步關閉時直接放行且不啟用同步`() {
        val state = stateOf(stepSyncEnabled = false, featureCredits = 0)

        val result = StepSyncGate.resolve(state, PendingStart.ROUTE)

        assertEquals(false, result)
        assertFalse("不該彈出對話框", state.value.showStepSyncCreditDialog)
    }

    @Test
    fun `步數同步關閉時即使沒次數也不彈對話框`() {
        val state = stateOf(stepSyncEnabled = false, isSubscriptionActive = false, featureCredits = 0)

        StepSyncGate.resolve(state, PendingStart.SINGLE)

        assertFalse(state.value.showStepSyncCreditDialog)
        assertNull(state.value.pendingStepSyncStart)
    }

    // ── 訂閱者 ────────────────────────────────────────────────────────────

    @Test
    fun `訂閱者沒有次數也直接啟用`() {
        val state = stateOf(isSubscriptionActive = true, featureCredits = 0)

        val result = StepSyncGate.resolve(state, PendingStart.ROUTE)

        assertEquals(true, result)
        assertFalse(state.value.showStepSyncCreditDialog)
    }

    @Test
    fun `訂閱者不扣次數`() {
        val snapshot = MapUiState(isSubscriptionActive = true, stepSyncEnabled = true)

        assertFalse(StepSyncGate.shouldConsumeCredit(snapshot, stepSyncActive = true))
    }

    // ── ad-unlock 使用者仍須付費（本功能的關鍵分界）────────────────────────

    @Test
    fun `ad-unlock 期間的非訂閱者沒次數時仍被擋下`() {
        // isProActive 為 true（6 小時解鎖生效中）但沒有訂閱
        val state = stateOf(isProActive = true, isSubscriptionActive = false, featureCredits = 0)

        val result = StepSyncGate.resolve(state, PendingStart.ROUTE)

        assertNull("ad-unlock 不得滿足計次閘門", result)
        assertTrue(state.value.showStepSyncCreditDialog)
    }

    @Test
    fun `ad-unlock 期間的非訂閱者有次數時要扣款`() {
        val snapshot = MapUiState(isProActive = true, isSubscriptionActive = false)

        assertTrue(StepSyncGate.shouldConsumeCredit(snapshot, stepSyncActive = true))
    }

    // ── 一般免費使用者 ────────────────────────────────────────────────────

    @Test
    fun `有次數時放行並啟用同步`() {
        val state = stateOf(featureCredits = 1)

        val result = StepSyncGate.resolve(state, PendingStart.ROUTE)

        assertEquals(true, result)
        assertFalse(state.value.showStepSyncCreditDialog)
    }

    @Test
    fun `次數為零時彈出對話框並記住待恢復的啟動`() {
        val state = stateOf(featureCredits = 0)

        val result = StepSyncGate.resolve(state, PendingStart.TELEPORT_EXPLORATION)

        assertNull("呼叫端必須中止啟動", result)
        assertTrue(state.value.showStepSyncCreditDialog)
        assertEquals(PendingStart.TELEPORT_EXPLORATION, state.value.pendingStepSyncStart)
    }

    @Test
    fun `彈出對話框時清掉上一次的廣告失敗提示`() {
        val state = stateOf(featureCredits = 0)
        state.value = state.value.copy(stepSyncAdUnavailable = true)

        StepSyncGate.resolve(state, PendingStart.ROUTE)

        assertFalse(state.value.stepSyncAdUnavailable)
    }

    @Test
    fun `每一種啟動類型都會被正確記住`() {
        PendingStart.entries.forEach { pending ->
            val state = stateOf(featureCredits = 0)
            StepSyncGate.resolve(state, pending)
            assertEquals(pending, state.value.pendingStepSyncStart)
        }
    }

    // ── 扣款條件 ──────────────────────────────────────────────────────────

    @Test
    fun `沒啟用同步就不扣次數`() {
        val snapshot = MapUiState(isSubscriptionActive = false)

        assertFalse(StepSyncGate.shouldConsumeCredit(snapshot, stepSyncActive = false))
    }

    @Test
    fun `非訂閱者啟用同步時要扣次數`() {
        val snapshot = MapUiState(isSubscriptionActive = false)

        assertTrue(StepSyncGate.shouldConsumeCredit(snapshot, stepSyncActive = true))
    }
}
