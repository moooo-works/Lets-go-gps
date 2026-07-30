package com.moooo_works.letsgogps.data.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.featureCreditDataStore by preferencesDataStore(name = "feature_credits")

/**
 * 通用功能次數錢包。
 *
 * 與 [AdUnlockStore] 的時數解鎖是**兩套獨立機制**，不得互相影響：
 * 時數解鎖給既有 Pro 功能（路線、搖桿、匯入匯出）用，
 * 次數錢包給採計次制的新功能用（目前只有步數同步）。
 */
@Singleton
class FeatureCreditStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val creditsKey = intPreferencesKey("feature_credits")

    val creditsFlow: Flow<Int> = context.featureCreditDataStore.data.map { it[creditsKey] ?: 0 }

    /**
     * 同步一次性讀取，用來在 Hilt singleton 建構時給 StateFlow 一個正確的初始值。
     * 沿用 [AdUnlockStore.currentExpiry] 的作法——若初始值先給 0 再跳到真值，
     * 冷啟動後第一次按開始模擬會誤判成沒有次數而彈出廣告對話框。
     */
    fun currentCredits(): Int = runBlocking {
        context.featureCreditDataStore.data.first()[creditsKey] ?: 0
    }

    suspend fun add(count: Int) {
        if (count <= 0) return
        context.featureCreditDataStore.edit { prefs ->
            prefs[creditsKey] = (prefs[creditsKey] ?: 0) + count
        }
    }

    /** 扣款。餘額不足時不扣並回傳 false。 */
    suspend fun consume(cost: Int): Boolean {
        if (cost <= 0) return true
        var consumed = false
        context.featureCreditDataStore.edit { prefs ->
            val current = prefs[creditsKey] ?: 0
            if (current >= cost) {
                prefs[creditsKey] = current - cost
                consumed = true
            }
        }
        return consumed
    }
}
