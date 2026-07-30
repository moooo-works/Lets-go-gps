package com.moooo_works.letsgogps.domain.health

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.utils.GeoDistanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StepAccumulatorTest {

    private lateinit var accumulator: StepAccumulator

    /** 固定的時間基準，避免測試依賴真實時鐘。 */
    private val now: Instant = Instant.parse("2026-07-28T12:00:00Z")

    /**
     * 台北車站附近，約略東西向的兩點，相距約 50 公尺。
     * 刻意壓在單段門檻（100 公尺）之內，否則會被當成瞬移丟棄。
     */
    private val pointA = LatLng(25.047_675, 121.517_055)
    private val pointB = LatLng(25.047_675, 121.517_555)

    @Before
    fun setUp() {
        accumulator = StepAccumulator()
    }

    // ── 距離累積 ──────────────────────────────────────────────────────────

    @Test
    fun `第一個點不產生距離`() {
        accumulator.addDistance(pointA)
        assertEquals(0.0, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `兩點之間累積的距離等於 haversine 距離`() {
        val expected = GeoDistanceMeters.haversineMeters(
            pointA.latitude, pointA.longitude, pointB.latitude, pointB.longitude
        )

        accumulator.addDistance(pointA)
        accumulator.addDistance(pointB)

        assertEquals(expected, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `距離會跨多個點持續累加`() {
        val legOne = GeoDistanceMeters.haversineMeters(
            pointA.latitude, pointA.longitude, pointB.latitude, pointB.longitude
        )

        accumulator.addDistance(pointA)
        accumulator.addDistance(pointB)
        accumulator.addDistance(pointA)

        assertEquals(legOne * 2, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `超過門檻的單段位移被視為瞬移不計入距離`() {
        accumulator.addDistance(pointA)
        // 台北 → 東京，遠超過 100 公尺門檻
        val outcome = accumulator.addDistance(LatLng(35.681_236, 139.767_125))

        assertEquals(StepAccumulator.LegOutcome.Teleport, outcome)
        assertEquals(0.0, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `瞬移後的下一段連續移動正常計入`() {
        accumulator.addDistance(pointA)
        accumulator.addDistance(LatLng(35.681_236, 139.767_125)) // 瞬移，丟棄
        // 從東京車站往東走一小段
        val nearTokyo = LatLng(35.681_236, 139.767_425)
        val expected = GeoDistanceMeters.haversineMeters(
            35.681_236, 139.767_125, nearTokyo.latitude, nearTokyo.longitude
        )

        val outcome = accumulator.addDistance(nearTokyo)

        assertEquals("這一段應該被計入", StepAccumulator.LegOutcome.Counted, outcome)
        assertEquals(expected, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `恰好等於門檻的位移仍計入`() {
        val accumulator = StepAccumulator(maxPlausibleLegMeters = 100.0)
        val metersPerDegree = GeoDistanceMeters.haversineMeters(0.0, 0.0, 0.0, 1.0)
        val origin = LatLng(0.0, 0.0)
        val exactlyAtLimit = LatLng(0.0, 100.0 / metersPerDegree)

        accumulator.addDistance(origin)
        val outcome = accumulator.addDistance(exactlyAtLimit)

        assertEquals(StepAccumulator.LegOutcome.Counted, outcome)
        assertEquals(100.0, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `搖桿式的密集小位移會持續累積`() {
        // 搖桿每次只移動幾公尺，全部都該被計入
        val metersPerDegree = GeoDistanceMeters.haversineMeters(0.0, 0.0, 0.0, 1.0)
        val stepDegrees = 5.0 / metersPerDegree // 每次 5 公尺

        accumulator.addDistance(LatLng(0.0, 0.0))
        repeat(20) { i ->
            accumulator.addDistance(LatLng(0.0, stepDegrees * (i + 1)))
        }

        assertEquals(100.0, accumulator.pendingDistanceMeters, 0.01)
    }

    @Test
    fun `breakContinuity 之後的第一個點不計入距離但保留已累積的距離`() {
        accumulator.addDistance(pointA)
        accumulator.addDistance(pointB)
        val beforeTeleport = accumulator.pendingDistanceMeters

        // 模擬瞬移到很遠的地方
        accumulator.breakContinuity()
        accumulator.addDistance(LatLng(35.681_236, 139.767_125)) // 東京車站

        assertEquals(
            "瞬移的距離不得計入，但先前累積的距離要留著",
            beforeTeleport,
            accumulator.pendingDistanceMeters,
            0.001
        )
    }

    @Test
    fun `reset 清空所有狀態`() {
        accumulator.addDistance(pointA)
        accumulator.addDistance(pointB)

        accumulator.reset()

        assertEquals(0.0, accumulator.pendingDistanceMeters, 0.001)
        // reset 後第一個點同樣不該產生距離
        accumulator.addDistance(LatLng(35.681_236, 139.767_125))
        assertEquals(0.0, accumulator.pendingDistanceMeters, 0.001)
    }

    // ── 步數換算 ──────────────────────────────────────────────────────────

    @Test
    fun `距離不足一步時回傳空清單且距離不被清掉`() {
        accumulate(meters = 0.5)

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)

        assertTrue(batches.isEmpty())
        assertEquals(0.5, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `步數等於距離除以步長並向下取整`() {
        accumulate(meters = 76.0) // 76 / 0.75 = 101.33 → 101

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(101L, batches.totalSteps())
    }

    @Test
    fun `步長為零時退回預設步長而非除以零`() {
        accumulate(meters = 75.0)

        val batches = accumulator.drainBatches(stepLength = 0.0, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(100L, batches.totalSteps()) // 75 / 0.75
    }

    @Test
    fun `步長為負數時退回預設步長`() {
        accumulate(meters = 75.0)

        val batches = accumulator.drainBatches(stepLength = -1.0, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(100L, batches.totalSteps())
    }

    // ── 分批 ──────────────────────────────────────────────────────────────

    @Test
    fun `500 步以內只切成一批`() {
        accumulate(meters = 375.0) // 500 步

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(1, batches.size)
        assertEquals(500L, batches[0].steps)
    }

    @Test
    fun `超過 500 步切成多批且每批不超過上限`() {
        accumulate(meters = 900.0) // 1200 步 → 500 + 500 + 200

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(3, batches.size)
        assertEquals(listOf(500L, 500L, 200L), batches.map { it.steps })
        assertTrue(batches.all { it.steps <= StepAccumulator.MAX_BATCH_STEPS })
        assertEquals(1200L, batches.totalSteps())
    }

    @Test
    fun `批次時間區間不重疊且相鄰間隔一秒`() {
        accumulate(meters = 900.0) // 1200 步 → 3 批

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = DEFAULT_SPAN)

        // 批次由 now 往回排列，所以 batches[0] 最新
        assertEquals("第一批應以 now 作為結束時間", now, batches[0].end)
        batches.forEach { assertTrue("start 必須早於 end", it.start.isBefore(it.end)) }

        for (i in 0 until batches.size - 1) {
            val newer = batches[i]
            val older = batches[i + 1]
            assertEquals(
                "相鄰批次之間須留 ${StepAccumulator.BATCH_GAP_SECONDS} 秒",
                newer.start.minusSeconds(StepAccumulator.BATCH_GAP_SECONDS),
                older.end
            )
            assertTrue("區間不得重疊", older.end.isBefore(newer.start))
        }
    }

    @Test
    fun `批次時間長度等於實際經過時間`() {
        accumulate(meters = 375.0) // 500 步

        val span = 300L
        val batches = accumulator.drainBatches(
            stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = span
        )

        val batch = batches.single()
        val seconds = batch.end.epochSecond - batch.start.epochSecond
        assertEquals("單批時應完全填滿實際經過時間", span, seconds)
    }

    @Test
    fun `極少步數的批次至少佔一秒避免零長度區間`() {
        accumulate(meters = 0.8) // 1 步

        val batches = accumulator.drainBatches(
            stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = DEFAULT_SPAN
        )

        val batch = batches.single()
        assertEquals(1L, batch.steps)
        assertTrue(batch.start.isBefore(batch.end))
    }


    // ── 時間窗不得超出實際經過時間（迴歸）────────────────────────────────

    @Test
    fun `單批的時間窗不會超出實際經過時間`() {
        // 15 km/h 跑 20 秒 = 83 公尺 = 111 步。
        // 舊版固定 2 步每秒會產生 56 秒的時間窗，是實際時間的 2.8 倍。
        accumulate(meters = 83.0)

        val span = 20L
        val batches = accumulator.drainBatches(
            stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = span
        )

        val earliest = batches.minOf { it.start }
        val elapsed = now.epochSecond - earliest.epochSecond
        assertTrue("時間窗 $elapsed 秒不得超過實際經過的 $span 秒", elapsed <= span)
    }

    @Test
    fun `多批含間隔的總時間窗也不會超出實際經過時間`() {
        accumulate(meters = 900.0) // 1200 步 → 3 批

        val span = 200L
        val batches = accumulator.drainBatches(
            stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = span
        )

        assertTrue("應切成多批", batches.size > 1)
        val earliest = batches.minOf { it.start }
        val elapsed = now.epochSecond - earliest.epochSecond
        assertTrue("總時間窗 $elapsed 秒不得超過實際經過的 $span 秒", elapsed <= span)
    }

    // ── 速度判斷已移出 accumulator ────────────────────────────────────────

    @Test
    fun `不再依據耗時判斷速度`() {
        // 速度改由呼叫端用設定值判斷。accumulator 只管距離與瞬移門檻，
        // 因此再快的單段位移（只要在門檻內）都會被計入。
        // 這樣才不會被 setLocation IPC 的延遲雜訊誤判——19 km/h 距 20 km/h
        // 的門檻只有 5% 餘裕，量測誤差直接吃掉整個餘裕。
        val metersPerDegree = GeoDistanceMeters.haversineMeters(0.0, 0.0, 0.0, 1.0)
        accumulator.addDistance(LatLng(0.0, 0.0))

        // 99 公尺（門檻內）不論多短時間都計入
        val outcome = accumulator.addDistance(LatLng(0.0, 99.0 / metersPerDegree))

        assertEquals(StepAccumulator.LegOutcome.Counted, outcome)
        assertEquals(99.0, accumulator.pendingDistanceMeters, 0.01)
    }

    // ── 配額 ──────────────────────────────────────────────────────────────

    @Test
    fun `總步數受剩餘配額截斷`() {
        accumulate(meters = 900.0) // 1200 步

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 300, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals(300L, batches.totalSteps())
    }

    @Test
    fun `配額為零時不產生批次`() {
        accumulate(meters = 900.0)

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 0, now = now, spanSeconds = DEFAULT_SPAN)

        assertTrue(batches.isEmpty())
    }

    @Test
    fun `配額為負數時不產生批次`() {
        accumulate(meters = 900.0)

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = -5, now = now, spanSeconds = DEFAULT_SPAN)

        assertTrue(batches.isEmpty())
    }

    // ── commit ────────────────────────────────────────────────────────────

    @Test
    fun `drainBatches 本身不扣除距離`() {
        accumulate(meters = 75.0)

        accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)

        assertEquals("未 commit 前距離必須原封不動", 75.0, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `commit 後剩餘距離正確`() {
        accumulate(meters = 76.0) // 101 步，餘 0.25 公尺

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 1000, now = now, spanSeconds = DEFAULT_SPAN)
        accumulator.commit(batches.totalSteps(), stepLength = 0.75)

        assertEquals(76.0 - 101 * 0.75, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `只 commit 成功的部分時未寫入的距離會留著`() {
        accumulate(meters = 900.0) // 1200 步

        val batches = accumulator.drainBatches(stepLength = 0.75, quotaRemaining = 10_000, now = now, spanSeconds = DEFAULT_SPAN)
        // 只有第一批寫入成功
        accumulator.commit(batches[0].steps, stepLength = 0.75)

        assertEquals(900.0 - 500 * 0.75, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `commit 零步或負數不改變距離`() {
        accumulate(meters = 75.0)

        accumulator.commit(0L, stepLength = 0.75)
        accumulator.commit(-10L, stepLength = 0.75)

        assertEquals(75.0, accumulator.pendingDistanceMeters, 0.001)
    }

    @Test
    fun `commit 超過已累積的距離時歸零而非變負`() {
        accumulate(meters = 10.0)

        accumulator.commit(9999L, stepLength = 0.75)

        assertEquals(0.0, accumulator.pendingDistanceMeters, 0.001)
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * 讓 accumulator 累積指定公尺數。
     *
     * 沿赤道往東走，每段固定 50 公尺——必須壓在單段門檻之內，
     * 否則會被當成瞬移丟棄。最後一段補足餘數。
     */
    private fun accumulate(meters: Double) {
        val metersPerDegree = GeoDistanceMeters.haversineMeters(0.0, 0.0, 0.0, 1.0)
        val legMeters = 50.0

        accumulator.addDistance(LatLng(0.0, 0.0))
        var walked = 0.0
        while (walked < meters) {
            walked = minOf(walked + legMeters, meters)
            accumulator.addDistance(LatLng(0.0, walked / metersPerDegree))
        }

        // 構造誤差應遠小於容許值
        assertEquals(meters, accumulator.pendingDistanceMeters, 0.01)
    }

    private fun List<StepBatch>.totalSteps(): Long = sumOf { it.steps }

    private companion object {
        /**
         * 既有測試用的預設經過秒數。取得夠大，讓那些案例的平均速度遠低於
         * [StepAccumulator.MAX_STEP_SPEED_MPS]，才不會被過速判斷攔下
         * ——它們驗的是換算與分批，不是速度。
         */
        const val DEFAULT_SPAN = 600L
    }
}
