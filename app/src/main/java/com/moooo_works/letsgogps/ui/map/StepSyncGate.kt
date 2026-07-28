package com.moooo_works.letsgogps.ui.map

import com.moooo_works.letsgogps.domain.FeatureCost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 步數同步的計次閘門。
 *
 * 純狀態邏輯、無副作用（除了更新 UiState），讓 RouteController 與 MapViewModel
 * 的四個啟動路徑共用同一套判斷，也讓這段可以直接用一般 JVM 測試覆蓋。
 *
 * 這裡刻意**不**碰既有的時數解鎖機制（`grantAdUnlockHours` / `isProActive`）
 * ——那是給路線、搖桿、匯入匯出用的，兩套並存互不影響。
 */
object StepSyncGate {

    /**
     * 決定本次模擬要不要啟用步數同步。
     *
     * @return `true`  啟用步數同步，繼續啟動
     *         `false` 不啟用（使用者沒開這個設定），繼續啟動
     *         `null`  次數不足，已彈出對話框，呼叫端**必須中止啟動**
     */
    fun resolve(state: MutableStateFlow<MapUiState>, pending: PendingStart): Boolean? {
        val snapshot = state.value

        // 沒開步數同步 → 跟這個閘門無關，照常啟動。
        if (!snapshot.stepSyncEnabled) return false

        // 訂閱者免費。必須用 isSubscriptionActive，不能用 isProActive
        // ——後者包含 ad-unlock，會放行 6 小時解鎖期內的免費使用者。
        if (snapshot.isSubscriptionActive) return true

        if (snapshot.featureCredits >= FeatureCost.STEP_SYNC_SESSION) return true

        state.update {
            it.copy(
                showStepSyncCreditDialog = true,
                pendingStepSyncStart = pending,
                stepSyncAdUnavailable = false,
            )
        }
        return null
    }

    /**
     * 啟動成功後是否要扣次數。
     *
     * 扣款刻意放在啟動成功之後：啟動失敗就等於從來沒扣過，
     * 不需要任何退款邏輯。
     */
    fun shouldConsumeCredit(snapshot: MapUiState, stepSyncActive: Boolean): Boolean =
        stepSyncActive && !snapshot.isSubscriptionActive
}
