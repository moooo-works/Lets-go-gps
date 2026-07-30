package com.moooo_works.letsgogps.domain.health

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.utils.GeoDistanceMeters
import java.time.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** 一批要寫入 Health Connect 的步數。[start] 必定早於 [end]。 */
data class StepBatch(
    val steps: Long,
    val start: Instant,
    val end: Instant
)

/**
 * 把模擬移動的距離累積起來，換算成可寫入的步數批次。
 *
 * 刻意做成不依賴 Android framework 的純 Kotlin 類，所有換算邏輯都能用一般
 * JVM 單元測試覆蓋，不需要 Robolectric 或實機。
 *
 * 非執行緒安全——設計上只由 MockLocationService 的單一 coroutine 存取。
 */
class StepAccumulator(
    /**
     * 單段位移的合理上限（公尺）。超過此值視為瞬移而非行走，該段距離會被丟棄。
     *
     * 100 公尺是安全的門檻：路線模擬每 250ms 推進一次，即使時速 500 公里
     * 單段也只有約 35 公尺；而跳點與瞬移探索的相鄰點通常相距公里級。
     */
    private val maxPlausibleLegMeters: Double = DEFAULT_MAX_PLAUSIBLE_LEG_METERS
) {

    private var pendingMeters: Double = 0.0
    private var lastPoint: LatLng? = null

    /** 目前尚未換算成步數的累積距離（公尺）。測試與除錯用。 */
    val pendingDistanceMeters: Double get() = pendingMeters

    /** [addDistance] 對單一段位移的判定結果。 */
    enum class LegOutcome {
        /** 已計入累積距離。 */
        Counted,

        /** 首個點，沒有前一點可比。 */
        NoPrevious,

        /** 單段位移過大，視為瞬移，距離丟棄。 */
        Teleport,
    }

    /**
     * 記錄一個新的注入位置，累加與上一點的距離。
     *
     * 呼叫端必須傳入**加抖動前**的原始座標，否則座標抖動會讓靜止狀態
     * 也持續累積假距離。
     *
     * 超過 [maxPlausibleLegMeters] 的位移視為瞬移，距離丟棄但錨點仍更新
     * ——跳點模式、瞬移探索、從列表跳到儲存位置都靠這條排除，呼叫端
     * 不必分辨自己處在哪種模式。
     *
     * ### 速度判斷不在這裡
     *
     * 曾經在這裡用「單段距離 ÷ 實際耗時」判斷速度，實測會誤判：注入是在
     * `setLocation()` 這個 IPC 之後才記時間，而 IPC 延遲會浮動數十毫秒。
     * 19 km/h 距 20 km/h 的門檻只有 5% 餘裕（250ms 的 tick 只要被量成
     * 237ms 就會誤判），雜訊直接吃掉整個餘裕。
     *
     * 速度改由呼叫端用**設定值**判斷——那是精確的，也與 UI 警示同源。
     */
    fun addDistance(point: LatLng): LegOutcome {
        val previous = lastPoint
        lastPoint = point
        if (previous == null) return LegOutcome.NoPrevious

        val leg = GeoDistanceMeters.haversineMeters(
            previous.latitude, previous.longitude,
            point.latitude, point.longitude
        )
        if (leg > maxPlausibleLegMeters) return LegOutcome.Teleport

        pendingMeters += leg
        return LegOutcome.Counted
    }

    /**
     * 明確斷開距離累積的連續性，但保留已累積的距離。
     *
     * [addDistance] 的距離門檻已能自動處理絕大多數瞬移；此方法留給
     * 呼叫端明確知道發生了不連續的情況（例如模擬模式切換）。
     */
    fun breakContinuity() {
        lastPoint = null
    }

    /** 全部歸零。模擬停止時呼叫。 */
    fun reset() {
        pendingMeters = 0.0
        lastPoint = null
    }

    /**
     * 把累積距離切成可寫入的批次，但**不**扣除——寫入成功後由呼叫端呼叫 [commit]。
     *
     * 批次由 [now] 往回排列：最後一批的結束時間就是 [now]，往前每批之間
     * 留 [BATCH_GAP_SECONDS] 秒空隙，避免產生時間重疊的紀錄。
     *
     * ### 為什麼要傳入 [spanSeconds]
     *
     * 步頻**不能寫死**。時間窗長度 = 步數 ÷ 步頻，而步數 = 距離 ÷ 步長，
     * 所以固定步頻時：
     *
     * ```
     * 時間窗 ≤ 實際經過時間  ⟺  速度 ≤ 步長 × 步頻
     * ```
     *
     * 以預設值 0.75 m × 2 步/秒 計算，臨界速度只有 5.4 km/h。超過就會讓批次
     * 往回追溯到比實際經過時間更早的位置，跟上一輪已寫的紀錄重疊。
     *
     * 改為由 `步數 ÷ 經過秒數` 推導步頻後，時間窗恆等於實際經過時間
     * ——在數學上不可能溢出，與速度無關。
     *
     * @param stepLength     步長（公尺）。非正數時退回 [DEFAULT_STEP_LENGTH_METERS]
     * @param quotaRemaining 當日剩餘配額；<= 0 時回傳空結果
     * @param now            批次的時間基準點（最後一批的結束時間）
     * @param spanSeconds    距上次成功寫入實際經過的秒數。所有批次的時間區間
     *                       （含間隔）都會被壓在這個範圍內
     */
    fun drainBatches(
        stepLength: Double,
        quotaRemaining: Int,
        now: Instant,
        spanSeconds: Long,
    ): List<StepBatch> {
        if (quotaRemaining <= 0) return emptyList()

        val effectiveStepLength = if (stepLength > 0.0) stepLength else DEFAULT_STEP_LENGTH_METERS
        val span = max(1L, spanSeconds)

        val available = floor(pendingMeters / effectiveStepLength).toLong()
        val total = min(available, quotaRemaining.toLong())
        if (total <= 0L) return emptyList()

        val batchCount = ((total + MAX_BATCH_STEPS - 1) / MAX_BATCH_STEPS).toInt()
        // 批次間隔也要算進 span，否則多批時總區間會超出實際經過時間。
        val gaps = (batchCount - 1).toLong() * BATCH_GAP_SECONDS
        val usableSpan = max(batchCount.toLong(), span - gaps)

        val batches = ArrayList<StepBatch>(batchCount)
        var cursorEnd = now
        var remaining = total
        while (remaining > 0L) {
            val steps = min(remaining, MAX_BATCH_STEPS)
            // 依步數比例分配時間，至少 1 秒避免零長度區間。
            val durationSeconds = max(1L, (usableSpan * steps / total))
            val start = cursorEnd.minusSeconds(durationSeconds)
            batches += StepBatch(steps, start, cursorEnd)

            cursorEnd = start.minusSeconds(BATCH_GAP_SECONDS)
            remaining -= steps
        }
        return batches
    }

    /**
     * 扣除已成功寫入的步數對應的距離。
     *
     * 只扣成功的部分——某批寫入失敗時，那批的距離要留著下次重試。
     */
    fun commit(writtenSteps: Long, stepLength: Double) {
        if (writtenSteps <= 0L) return
        val effectiveStepLength = if (stepLength > 0.0) stepLength else DEFAULT_STEP_LENGTH_METERS
        pendingMeters = max(0.0, pendingMeters - writtenSteps * effectiveStepLength)
    }

    companion object {
        /** 單筆 StepsRecord 的步數上限，避免產生「一秒鐘一萬步」這種一眼可辨的假紀錄。 */
        const val MAX_BATCH_STEPS = 500L

        /** 相鄰批次之間的時間間隔（秒），確保區間不重疊。 */
        const val BATCH_GAP_SECONDS = 1L

        /** 預設步長（公尺）。 */
        const val DEFAULT_STEP_LENGTH_METERS = 0.75

        /** 步長可設定的合理範圍。超出即夾回範圍內。 */
        const val MIN_STEP_LENGTH_METERS = 0.3
        const val MAX_STEP_LENGTH_METERS = 1.5

        /**
         * 計為步數的速度上限（km/h）。
         *
         * 超過這個速度就不是走路或跑步（世界紀錄馬拉松配速約 20.5 km/h），
         * 硬要換算會產生生理上不可能的步頻。模擬速度最高可設到 100 km/h，
         * 那種情境刻意不計步。
         *
         * 判斷用**設定的速度**而非量測值——見 [addDistance] 的說明。
         */
        const val MAX_STEP_SPEED_KMH = 20.0

        /** 單段位移合理上限的預設值（公尺）。超過即視為瞬移。 */
        const val DEFAULT_MAX_PLAUSIBLE_LEG_METERS = 100.0
    }
}
