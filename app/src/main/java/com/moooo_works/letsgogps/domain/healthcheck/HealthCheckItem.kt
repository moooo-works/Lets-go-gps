package com.moooo_works.letsgogps.domain.healthcheck

/**
 * The six checks that gate "Start mocking". Order is the order shown in UI.
 *
 * Critical items must pass before mocking can start; non-critical items
 * are surfaced as soft warnings.
 */
enum class HealthCheckItem {
    NotificationPermission,
    LocationPermission,
    GpsEnabled,
    DeveloperMode,
    MockAppSelected,
    BatteryOptimizationExempt;

    val isCritical: Boolean
        get() = this != BatteryOptimizationExempt
}
