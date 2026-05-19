package com.moooo_works.letsgogps.data.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.adUnlockDataStore by preferencesDataStore(name = "ad_unlock")

@Singleton
class AdUnlockStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val expiryKey = longPreferencesKey("pro_ad_unlock_expiry_millis")

    val expiryFlow: Flow<Long> = context.adUnlockDataStore.data.map { it[expiryKey] ?: 0L }

    suspend fun setExpiry(millis: Long) {
        context.adUnlockDataStore.edit { it[expiryKey] = millis }
    }
}
