package com.moooo_works.letsgogps.domain.health

import java.time.Instant

/**
 * 把步數寫入系統健康資料層的抽象。
 *
 * 之所以抽出介面，是為了讓 [com.moooo_works.letsgogps.service.MockLocationService]
 * 能在單元測試中注入 fake，不必碰真實的 Health Connect。
 */
interface StepSyncEngine {

    /**
     * 寫入一筆步數紀錄。
     *
     * 實作**不得**讓例外逃逸——寫入失敗只回傳 false，絕不影響位置模擬。
     *
     * @param steps 步數，須為正整數
     * @param start 該筆紀錄的起始時間
     * @param end   該筆紀錄的結束時間，須晚於 [start]
     * @return 是否寫入成功
     */
    suspend fun write(steps: Long, start: Instant, end: Instant): Boolean
}
