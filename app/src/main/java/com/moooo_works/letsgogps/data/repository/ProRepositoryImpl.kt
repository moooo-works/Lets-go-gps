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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [ProRepository].
 *
 * [isProActive] is true when either the billing subscription is active OR the ad-unlock
 * timer has not yet expired.  [isAdFreeActive] reflects the subscription only (used to
 * gate ad display).
 *
 * Design notes
 * ============
 * The backing [MutableStateFlow] properties are initialised LAZILY so that the [clock]
 * and [tickerScope] test-hooks (set in the `apply {}` block after construction) are
 * already in place by the time the initial value is computed.
 *
 * Reactive updates from the DataStore are not re-subscribed after startup; instead,
 * [grantAdUnlockHours] writes through to both the local [MutableStateFlow] and the
 * DataStore.  This avoids a subtle race where the DataStore's async collector could
 * overwrite a freshly-stacked expiry with a stale earlier value.
 */
@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billingManager: BillingManager,
    private val adUnlockStore: AdUnlockStore,
) : ProRepository {

    /** Returns the current epoch-millis.  Overridable in tests. */
    @VisibleForTesting
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Scope for background work (production: long-lived SupervisorJob scope;
     * tests: set to the [TestScope] for access to a controlled scheduler).
     * Only finite / one-shot coroutines are launched here to avoid
     * [kotlinx.coroutines.test.UncompletedCoroutinesError] in tests.
     */
    @VisibleForTesting
    internal var tickerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Independent scope for the startup DataStore seed read.
    // Lives separately from tickerScope so test scopes are never polluted.
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Lazy backing state ----
    //
    // Lazy initialisation ensures that [clock] and [tickerScope] have already been
    // set (via the test's `apply {}` block) before any computation happens.

    /**
     * Local mirror of the DataStore expiry.
     *
     * In tests [adUnlockStore.expiryFlow] is backed by a [MutableStateFlow] whose
     * current value can be read via the [StateFlow] interface.  The cast lets us seed
     * the initial value synchronously without blocking.
     *
     * In production [adUnlockStore.expiryFlow] is a cold DataStore [Flow].  We launch
     * a one-shot coroutine in [workScope] to pull the first persisted value and seed
     * this MutableStateFlow; subsequent writes go through [grantAdUnlockHours] which
     * updates both this flow and the DataStore atomically.
     */
    private val _expiryMillis: MutableStateFlow<Long> by lazy {
        val syncValue = (adUnlockStore.expiryFlow as? StateFlow<Long>)?.value ?: 0L
        MutableStateFlow(syncValue).also { stateFlow ->
            if (syncValue == 0L) {
                // Production path: seed from the DataStore's first emission.
                workScope.launch {
                    val stored = adUnlockStore.expiryFlow.first()
                    if (stored > stateFlow.value) {
                        stateFlow.value = stored
                        recompute()
                    }
                }
            }
        }
    }

    private val _isAdFreeActive: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(billingManager.isProActive.value)
    }

    private val _isProActive: MutableStateFlow<Boolean> by lazy {
        val subscribed = billingManager.isProActive.value
        val expiry = _expiryMillis.value
        MutableStateFlow(subscribed || expiry > clock())
    }

    // ---- Public StateFlow properties ----

    override val adUnlockExpiryMillis: StateFlow<Long> by lazy { _expiryMillis.asStateFlow() }

    override val isAdFreeActive: StateFlow<Boolean> by lazy { _isAdFreeActive.asStateFlow() }

    override val isProActive: StateFlow<Boolean> by lazy { _isProActive.asStateFlow() }

    // ---- Internal helpers ----

    private fun recompute() {
        val subscribed = billingManager.isProActive.value
        val expiry = _expiryMillis.value
        val now = clock()
        _isAdFreeActive.value = subscribed
        _isProActive.value = subscribed || expiry > now
    }

    // ---- ProRepository implementation ----

    override suspend fun refreshProStatus() {
        billingManager.queryActiveSubscriptions()
        recompute()
    }

    override suspend fun grantAdUnlockHours(hours: Long) {
        val nowMillis = clock()
        val current = _expiryMillis.value
        val baseline = maxOf(current, nowMillis)
        val newExpiry = baseline + hours * 3600_000L
        val cap = nowMillis + 24 * 3600_000L
        val capped = minOf(newExpiry, cap)
        // Update local mirror immediately so consecutive calls stack correctly.
        _expiryMillis.value = capped
        recompute()
        // Persist to DataStore.
        adUnlockStore.setExpiry(capped)
    }

    override fun launchBillingFlow(activity: Activity) {
        billingManager.launchBillingFlow(activity)
    }
}
