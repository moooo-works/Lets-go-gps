package com.moooo_works.letsgogps.domain.repository

import android.app.Activity
import com.moooo_works.letsgogps.domain.model.SubscriptionOffer
import kotlinx.coroutines.flow.StateFlow

interface ProRepository {
    /** Subscription OR active ad-unlock (used to gate Pro features). */
    val isProActive: StateFlow<Boolean>

    /** Subscription only (used to gate ad display — banner / interstitial). */
    val isAdFreeActive: StateFlow<Boolean>

    /**
     * Subscription only — for entitlement gates that an ad-unlock must NOT satisfy.
     *
     * Same value as [isAdFreeActive] but a distinct name on purpose: that one is
     * about whether to show ads, this one is about whether the user paid.
     *
     * **Do not use [isProActive] for pay-gated features** — it is
     * `subscription OR ad-unlock`, so a free user inside their 6-hour ad-unlock
     * window would be let through. That is correct for the legacy Pro features
     * but wrong for anything on the per-use credit model.
     */
    val isSubscriptionActive: StateFlow<Boolean>

    /**
     * Generic per-use credit wallet, priced via
     * [com.moooo_works.letsgogps.domain.FeatureCost].
     *
     * Independent of [adUnlockExpiryMillis] — the two monetisation mechanisms
     * never touch each other.
     */
    val featureCredits: StateFlow<Int>

    /** Current ad-unlock expiry epoch millis; 0 means not unlocked. */
    val adUnlockExpiryMillis: StateFlow<Long>

    /**
     * Localized Play Billing offer (formattedPrice in the user's billing
     * country currency). `null` until BillingClient has connected and queried
     * ProductDetails — UI must fall back to a no-price string in that case
     * rather than show any hard-coded amount.
     */
    val subscriptionOffer: StateFlow<SubscriptionOffer?>

    suspend fun refreshProStatus()

    /** Grant N hours of ad-unlocked Pro; stacks onto existing expiry, capped at 24h total from now. */
    suspend fun grantAdUnlockHours(hours: Long)

    /** Add credits to the wallet — one rewarded ad grants `CREDITS_PER_REWARDED_AD`. */
    suspend fun grantFeatureCredits(count: Int)

    /** Spend credits. Returns false and spends nothing when the balance is short. */
    suspend fun consumeFeatureCredits(cost: Int): Boolean

    fun launchBillingFlow(activity: Activity)
}
