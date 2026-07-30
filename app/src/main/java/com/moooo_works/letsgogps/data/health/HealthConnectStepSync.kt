package com.moooo_works.letsgogps.data.health

import android.util.Log
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.moooo_works.letsgogps.domain.health.StepSyncEngine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StepSyncEngine] 的 Health Connect 實作。
 *
 * 所有失敗都就地吞掉並回傳 false——位置模擬的正確性遠比步數寫入重要，
 * 任何例外都不得傳播到注入路徑上。
 */
@Singleton
class HealthConnectStepSync @Inject constructor(
    private val availability: HealthConnectAvailability
) : StepSyncEngine {

    override suspend fun write(steps: Long, start: Instant, end: Instant): Boolean {
        if (steps <= 0L || !start.isBefore(end)) {
            Log.w(TAG, "無效的步數紀錄：steps=$steps start=$start end=$end")
            return false
        }

        val client = availability.clientOrNull() ?: return false

        return try {
            val offset = ZoneId.systemDefault().rules.getOffset(start)
            client.insertRecords(
                listOf(
                    StepsRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        count = steps,
                        // autoRecorded：由 app 自動產生，非使用者手動輸入。
                        // 這是這批資料最誠實的分類方式。
                        metadata = Metadata.autoRecorded(Device(type = Device.TYPE_PHONE))
                    )
                )
            )
            true
        } catch (e: Exception) {
            // 權限被撤銷、Health Connect 被停用、資料重疊等都會走到這裡。
            Log.w(TAG, "寫入步數失敗（steps=$steps）", e)
            false
        }
    }

    private companion object {
        const val TAG = "HealthConnectStepSync"
    }
}
