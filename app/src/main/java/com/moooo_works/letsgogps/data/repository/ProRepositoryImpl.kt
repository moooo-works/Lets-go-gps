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
        // Seed with the persisted expiry read synchronously so observers (e.g.
        // SettingsViewModel.proSection) reflect the cached unlock window from
        // the very first emission, not a brief 0L window while DataStore loads.
        adUnlockStore.expiryFlow.stateIn(tickerScope, SharingStarted.Eagerly, adUnlockStore.currentExpiry())
    }

    override val isAdFreeActive: StateFlow<Boolean>
        get() = billingManager.isProActive

    override val isProActive: StateFlow<Boolean> by lazy {
        // Initial value computed from both synchronous caches (billing + ad-
        // unlock). combine() only fires its first value once all three
        // upstreams have emitted, gated on DataStore disk reads. Until that
        // moment observers receive whatever we pass as `initialValue` here.
        // A hard-coded `false` previously caused the first ROUTE pill tap to
        // spuriously open the upgrade dialog for both subscribers AND ad-
        // unlocked users on cold launch. Both BillingManager and AdUnlockStore
        // expose synchronous accessors so we can reconstruct the correct
        // initial value here.
        val nowMillis = clock()
        val initial = billingManager.isProActive.value || adUnlockStore.currentExpiry() > nowMillis
        combine(
            billingManager.isProActive,
            adUnlockStore.expiryFlow,
            nowFlow
        ) { subscribed, expiry, now -> subscribed || expiry > now }
            .stateIn(tickerScope, SharingStarted.Eagerly, initial)
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
