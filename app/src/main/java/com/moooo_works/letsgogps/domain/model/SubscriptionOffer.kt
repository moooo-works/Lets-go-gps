package com.moooo_works.letsgogps.domain.model

/**
 * Localized subscription offer pulled from Google Play Billing. `formattedPrice`
 * is the locale-correct recurring price (e.g. "₱269.00", "$3.99", "¥600") and
 * must be the only price string surfaced in the app — hard-coding any currency
 * violates Play's "consistent local currency" subscription policy.
 */
data class SubscriptionOffer(
    val formattedPrice: String,
    val hasFreeTrial: Boolean,
)
