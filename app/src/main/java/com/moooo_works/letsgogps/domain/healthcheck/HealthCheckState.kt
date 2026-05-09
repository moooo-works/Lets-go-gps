package com.moooo_works.letsgogps.domain.healthcheck

sealed class ItemStatus {
    object Passed : ItemStatus()
    object Failed : ItemStatus()
    /** Item does not apply on this Android version (e.g. POST_NOTIFICATIONS on API < 33). */
    object NotApplicable : ItemStatus()
}

data class HealthCheckState(
    val items: Map<HealthCheckItem, ItemStatus>
) {
    /** True if any *critical* item is Failed. NotApplicable never blocks. */
    val hasBlockingFailure: Boolean
        get() = items.any { (item, status) -> item.isCritical && status is ItemStatus.Failed }

    /** True only when every applicable item passed (including soft items). */
    val allPassed: Boolean
        get() = items.values.all { it is ItemStatus.Passed || it is ItemStatus.NotApplicable }

    fun statusOf(item: HealthCheckItem): ItemStatus =
        items[item] ?: ItemStatus.NotApplicable
}
