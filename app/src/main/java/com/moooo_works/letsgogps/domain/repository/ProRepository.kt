package com.moooo_works.letsgogps.domain.repository

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface ProRepository {
    /** Subscription OR active ad-unlock (used to gate Pro features). */
    val isProActive: StateFlow<Boolean>

    /** Subscription only (used to gate ad display — banner / interstitial). */
    val isAdFreeActive: StateFlow<Boolean>

    /** Current ad-unlock expiry epoch millis; 0 means not unlocked. */
    val adUnlockExpiryMillis: StateFlow<Long>

    suspend fun refreshProStatus()

    /** Grant N hours of ad-unlocked Pro; stacks onto existing expiry, capped at 24h total from now. */
    suspend fun grantAdUnlockHours(hours: Long)

    fun launchBillingFlow(activity: Activity)
}
