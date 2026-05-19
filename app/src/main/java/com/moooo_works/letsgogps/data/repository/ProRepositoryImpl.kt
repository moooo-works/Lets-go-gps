package com.moooo_works.letsgogps.data.repository

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.moooo_works.letsgogps.data.billing.AdUnlockStore
import com.moooo_works.letsgogps.data.billing.BillingManager
import com.moooo_works.letsgogps.domain.repository.ProRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billingManager: BillingManager,
    private val adUnlockStore: AdUnlockStore,
) : ProRepository {

    @VisibleForTesting
    internal var clock: () -> Long = { System.currentTimeMillis() }

    @VisibleForTesting
    internal var tickerScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val nowFlow = flow {
        while (true) {
            emit(clock())
            delay(60_000)
        }
    }

    override val adUnlockExpiryMillis: StateFlow<Long> by lazy {
        adUnlockStore.expiryFlow.stateIn(tickerScope, SharingStarted.Eagerly, 0L)
    }

    override val isAdFreeActive: StateFlow<Boolean>
        get() = billingManager.isProActive

    override val isProActive: StateFlow<Boolean> by lazy {
        combine(
            billingManager.isProActive,
            adUnlockStore.expiryFlow,
            nowFlow
        ) { subscribed, expiry, now -> subscribed || expiry > now }
            .stateIn(tickerScope, SharingStarted.Eagerly, false)
    }

    override suspend fun refreshProStatus() {
        billingManager.queryActiveSubscriptions()
    }

    override suspend fun grantAdUnlockHours(hours: Long) {
        val nowMillis = clock()
        val current = adUnlockExpiryMillis.value
        val newExpiry = maxOf(current, nowMillis) + hours * HOUR_MILLIS
        val cap = nowMillis + 24 * HOUR_MILLIS
        adUnlockStore.setExpiry(minOf(newExpiry, cap))
    }

    override fun launchBillingFlow(activity: Activity) {
        billingManager.launchBillingFlow(activity)
    }

    companion object {
        private const val HOUR_MILLIS = 3_600_000L
    }
}
